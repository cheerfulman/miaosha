package org.example.response;

public class CommentResponseType {
    // 返回结果是否为 success
    private String status;
    // 返回的 数据
    private Object data;

    // 通用的 创建方法
    public static CommentResponseType create(Object data){
        return CommentResponseType.create(data,"success");
    }
    public static CommentResponseType create(Object data, String status){
        CommentResponseType commentResponseType = new CommentResponseType();
        commentResponseType.setData(data);
        commentResponseType.setStatus(status);
        return commentResponseType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
