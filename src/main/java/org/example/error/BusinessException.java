package org.example.error;
// 包装器 业务异常类实现
public class BusinessException extends Exception implements CommonError{
    private CommonError commonError;
    private int errCode;
    private String errMsg;

    public BusinessException(CommonError commonError){
        super();
        this.commonError = commonError;
    }

    public BusinessException(CommonError commonError,String errMsg){
        super();
        this.commonError = commonError;
        this.commonError.setCommonError(errMsg);
    }



    @Override
    public int getErrCode() {
        return this.commonError.getErrCode();
    }

    @Override
    public String getErrMsg() {
        return this.commonError.getErrMsg();
    }

    @Override
    public CommonError setCommonError(String errMsg) {
        this.commonError.setCommonError(errMsg);
        return this;
    }
}
