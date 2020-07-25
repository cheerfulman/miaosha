package org.example.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.example.dao.UserDOMapper;
import org.example.dao.UserPasswordDOMapper;
import org.example.dataobject.UserDO;
import org.example.dataobject.UserPasswordDO;
import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.service.UserService;
import org.example.service.model.UserModel;
import org.example.validator.ValidationImpl;
import org.example.validator.ValidationResult;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {
    @Autowired(required = false)
    private UserDOMapper userDOMapper;

    @Autowired(required = false)
    private UserPasswordDOMapper userPasswordDOMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ValidationImpl validation;
    @Override
    public UserModel getUserById(Integer id) {
        // 调用userDOMapper 获取对应用户的dataObject
        UserDO userDO = userDOMapper.selectByPrimaryKey(id);
        if(userDO == null)return null;
        // 通过 用户id 获得 加密 密码
        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(id);

        return convertFromDataObject(userDO,userPasswordDO);
    }

    @Override
    public UserModel getUserByIdInCache(Integer id) {
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get("user_validate_" + id);
        if(userModel == null){
            userModel = this.getUserById(id);
            redisTemplate.opsForValue().set("user_validate_" + id,userModel);
            redisTemplate.expire("user_validate_" + id,10, TimeUnit.MINUTES);
        }
        return userModel;

    }

    @Override
    @Transactional
    public void register(UserModel userModel) throws BusinessException {
        if(userModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }

        ValidationResult result = validation.validate(userModel);
        if(result.isHasErrors()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,result.getErrMsg());
        }
        UserDO userDO = convertFromUserModel(userModel);

        try {
            userDOMapper.insertSelective(userDO);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"该手机号已注册");
        }

        userModel.setId(userDO.getId());
        UserPasswordDO userPasswordDO = convertPasswordFromUserModel(userModel);
        userPasswordDOMapper.insertSelective(userPasswordDO);
        return ;
    }
    private UserPasswordDO convertPasswordFromUserModel(UserModel userModel){
        if(userModel == null)return null;
        UserPasswordDO userPasswordDO = new UserPasswordDO();
        userPasswordDO.setEncrptPassword(userModel.getEncrptPassword());
        userPasswordDO.setUserId(userModel.getId());
        return userPasswordDO;
    }

    private UserDO convertFromUserModel(UserModel userModel){
        if(userModel == null) return null;
        UserDO userDO = new UserDO();
        BeanUtils.copyProperties(userModel,userDO);
        return userDO;
    }

    private UserModel convertFromDataObject(UserDO userDO, UserPasswordDO userPasswordDO){
        if(userDO == null)return null;
        UserModel userModel = new UserModel();
        BeanUtils.copyProperties(userDO,userModel);
        if(userPasswordDO != null){
            userModel.setEncrptPassword(userPasswordDO.getEncrptPassword());
        }
        return userModel;
    }

    public UserModel validateLogin(String telphone, String password) throws BusinessException {
        UserDO userDO = userDOMapper.selectByTelPhone(telphone);
        if(userDO == null){
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);
        }
        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(userDO.getId());
        // 获得数据库中的 密码进行比对
        UserModel userModel = convertFromDataObject(userDO, userPasswordDO);
        if(!StringUtils.equals(password,userModel.getEncrptPassword())){
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);
        }
        return userModel;

    }
}
