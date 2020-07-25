package org.example.service.model;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class UserModel implements Serializable {
    private Integer id;
    @NotBlank(message = "用户名不能为空")
    private String name;

    @NotNull(message = "性别不能不填")
    private Byte gender;

    @NotNull(message = "年龄不能不填")
    @Min(value = 0,message = "年龄至少大于0")
    @Max(value = 200,message = "有超过200岁的人，我不信？")
    private Integer age;

    @NotBlank(message = "手机号不能为空")
    private String telphone;
    private String thirdPartId;
    private String registerMode;

    @NotBlank(message = "密码不能为空")
    private String encrptPassword;
}
