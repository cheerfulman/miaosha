package org.example.service;

import org.example.error.BusinessException;
import org.example.service.model.UserModel;

public interface UserService {
    // 通过用户id 获取用户对象
    UserModel getUserById(Integer id);
    UserModel getUserByIdInCache(Integer id);

    void register(UserModel userModel) throws BusinessException;

    // telphone 手机号, password 加密后密码
    UserModel validateLogin(String telphone,String password) throws BusinessException;
}
