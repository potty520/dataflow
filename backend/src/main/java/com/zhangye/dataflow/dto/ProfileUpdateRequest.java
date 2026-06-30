package com.zhangye.dataflow.dto;

import lombok.Data;

@Data
public class ProfileUpdateRequest {
    private String nickname;
    private String phone;
    private String avatar;
}
