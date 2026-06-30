package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("svc_api_call_log")
public class SvcApiCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long apiId;
    private String apiName;
    private String appName;
    private Integer success;
    private Integer responseMs;
    private LocalDateTime createTime;
}
