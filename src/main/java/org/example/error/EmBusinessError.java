package org.example.error;

public enum
EmBusinessError implements CommonError{
    // 通用 错误 10000 开头
    PARAMETER_VALIDATION_ERROR(10001,"参数不合法"),
    UNKNOWN_ERROR(10002,"未知错误"),

    // 20000 开头 表示用户信息错误
    USER_NOT_EXIST(20001,"用户不存在"),
    USER_LOGIN_FAIL(20002,"用户手机号或密码不正确"),
    USER_NOT_LOGIN(20003,"用户未登录"),


    // 30000开头为交易信息错误
    STOCK_NOT_ENOUGH(30001,"库存不足"),
    MQ_SEND_FAIL(30002,"库存异步消息失败"),
    RATELIMIT(30003,"活动太火爆请稍后再试")
    ;

    private EmBusinessError(int errCode,String errMsg){
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    private int errCode;
    private String errMsg;

    public void setErrCode(int errCode) {
        this.errCode = errCode;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    @Override
    public int getErrCode() {
        return this.errCode;
    }

    @Override
    public String getErrMsg() {
        return this.errMsg;
    }

    @Override
    public CommonError setCommonError(String errMsg) {
        this.errMsg = errMsg;
        return this;
    }


}
