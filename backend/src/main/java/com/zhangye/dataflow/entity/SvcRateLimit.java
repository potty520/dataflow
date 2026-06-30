package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("svc_rate_limit")
public class SvcRateLimit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long apiId;
    private String apiName;
    private Integer timeWindowSec;
    private Integer maxRequests;
    private Integer enabled;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
