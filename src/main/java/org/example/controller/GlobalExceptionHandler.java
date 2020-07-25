package org.example.controller;

import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.response.CommentResponseType;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

// 利用切面的方式处理全局异常，摒弃BaseController + @ExceptionHandler的方式（只能处理经过Controller的异常无法处理 页面路径等404，405的错误）
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public CommentResponseType doError(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        ex.printStackTrace();
        Map<String, Object> responseData = new HashMap<>();
        if (ex instanceof BusinessException) {
            BusinessException bizException = (BusinessException) ex;
            responseData.put("errCode", bizException.getErrCode());
            responseData.put("errMsg", bizException.getErrMsg());
        } else if (ex instanceof ServletRequestBindingException) {
            // @RequestParam是必传的，如果没传，就会触发这个异常
            responseData.put("errCode", EmBusinessError.UNKNOWN_ERROR.getErrCode());
            responseData.put("errMsg", "URL绑定路由问题");
        } else if (ex instanceof NoHandlerFoundException) {
            responseData.put("errCode", EmBusinessError.UNKNOWN_ERROR.getErrCode());
            responseData.put("errMsg", "没有找到对应的访问路径");
        } else {
            responseData.put("errCode", EmBusinessError.UNKNOWN_ERROR.getErrCode());
            responseData.put("errMsg", EmBusinessError.UNKNOWN_ERROR.getErrMsg());
        }
        return CommentResponseType.create(responseData, "fail");
    }
}
