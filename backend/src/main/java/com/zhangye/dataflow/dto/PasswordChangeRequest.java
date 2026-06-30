package com.zhangye.dataflow.dto;

import lombok.Data;

@Data
public class PasswordChangeRequest {
    private String phone;
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
}
