package org.example.controller;

import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.response.CommentResponseType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

public class BaseController {
    public static final String CONTENT_TYPE_FORMED = "application/x-www-form-urlencoded";

    // 拦截tomcat异常处理的机制，定义exceptionHandler解决未被controller层吸收的exception，摒弃掉了tomcat内嵌的error页


//    @ExceptionHandler(Exception.class)
//    @ResponseStatus(HttpStatus.OK)
//    @ResponseBody
//    public Object handlerException(HttpServletRequest request, Exception ex){
//        Map<String,Object> map = new HashMap<>();
//        if(ex instanceof BusinessException){
//            BusinessException businessException = (BusinessException) ex;
//            map.put("errCode",businessException.getErrCode());
//            map.put("errMsg",businessException.getErrMsg());
//        }else{
//            map.put("errCode", EmBusinessError.UNKNOWN_ERROR.getErrCode());
//            map.put("errMsg",EmBusinessError.UNKNOWN_ERROR.getErrMsg());
//        }
//        return CommentResponseType.create(map,"fail");
//
//    }
}
