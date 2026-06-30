package com.zhangye.dataflow.dto;

import lombok.Data;

import java.util.List;

@Data
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private Long tenantId;
    private List<String> roles;
}
