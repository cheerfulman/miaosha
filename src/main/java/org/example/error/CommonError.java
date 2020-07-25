package org.example.error;

public interface CommonError {
    int getErrCode();
    String getErrMsg();

    CommonError setCommonError(String errMsg);

}
