package org.example.controller;

import com.alibaba.druid.util.StringUtils;
import org.apache.tomcat.util.security.MD5Encoder;
import org.example.config.RedisConfig;
import org.example.controller.viewObject.UserVO;
import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.response.CommentResponseType;
import org.example.service.UserService;
import org.example.service.impl.UserServiceImpl;
import org.example.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;



import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Base64.Decoder;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Controller("user")
@RequestMapping("/user")
@CrossOrigin(origins = "*",allowCredentials = "true", allowedHeaders = "*")
public class UserController extends BaseController{

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    @ResponseBody
    @RequestMapping(value = "/login",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    public CommentResponseType login(@RequestParam(name = "telphone") String telphone,
                                     @RequestParam(name = "password") String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        if(org.apache.commons.lang3.StringUtils.isEmpty(telphone)
            || StringUtils.isEmpty(password)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }
        UserModel userModel = userService.validateLogin(telphone, this.EncodeByMD5(password));
        // 将登录凭证加入到用户登录成功的session内,修改成若用户登录验证成功后将对应的登录信息和凭证token一起加入redis中
        // 生产token
        String uuidToken = UUID.randomUUID().toString().replaceAll("-","");
        // 建立token和用户登录态之间的联系
        redisTemplate.opsForValue().set(uuidToken,userModel);
        redisTemplate.expire(uuidToken,3, TimeUnit.HOURS);

        // 将登录信息 存入 session
//        httpServletRequest.getSession().setAttribute("IS_LOGIN",true);
//        httpServletRequest.getSession().setAttribute("LOGIN_USER",userModel);

        // 下发token
        return CommentResponseType.create(uuidToken);
    }
    // 用户注册
    @ResponseBody
    @RequestMapping(value = "/register",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    public CommentResponseType register(@RequestParam(name = "telphone") String telphone,
                                        @RequestParam(name = "optCode") String optCode,
                                        @RequestParam(name = "name") String name,
                                        @RequestParam(name = "gender") Byte gender,
                                        @RequestParam(name = "age") Integer age,
                                        @RequestParam(name = "password") String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        // 验证手机号和对应optCode相符
        String inSessionOptCOde = (String)this.httpServletRequest.getSession().getAttribute(telphone);
        if(!StringUtils.equals(optCode,inSessionOptCOde)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"短信验证码不符合");
        }
        // 用户注册
        UserModel userModel = new UserModel();
        userModel.setEncrptPassword(this.EncodeByMD5(password));
        userModel.setAge(age);
        userModel.setGender(gender);
        userModel.setName(name);
        userModel.setTelphone(telphone);
        userModel.setRegisterMode("By Phone");
        // 密码已经加密
        userService.register(userModel);
        return CommentResponseType.create(null);
    }

    public String EncodeByMD5(String str) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(str.getBytes("utf-8"));
    }

    @ResponseBody
    @RequestMapping("/get")
    public CommentResponseType getUser(@RequestParam(name = "id") Integer id) throws Exception{
        // 调用service服务获取对应id的用户返回给前端
        UserModel userModel = userService.getUserById(id);
        // 将核心领域模型用户对象转化为 前端的 viewObject
        if(userModel == null){
            throw  new BusinessException(EmBusinessError.USER_NOT_EXIST);
        }

        UserVO userVO = convertFromModel(userModel);
        return CommentResponseType.create(userVO);
    }

    @ResponseBody
    @RequestMapping(value = "/getOpt",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    public CommentResponseType getOtp(@RequestParam(name = "telphone") String telphone){
        Random random = new Random();
        int optInt = random.nextInt(899999) + 100000;
        String opt = String.valueOf(optInt);

        httpServletRequest.getSession().setAttribute(telphone,opt);
        System.out.println("telphone : " + telphone + " OPT : " + opt);
        return CommentResponseType.create(null);

    }

    private UserVO convertFromModel(UserModel userModel){
        if(userModel == null)return null;
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(userModel,userVO);
        return userVO;
    }


}
