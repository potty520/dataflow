package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("svc_workorder")
public class SvcWorkorder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long apiId;
    private Long appId;
    private String apiName;
    private String appName;
    private String applicant;
    private String applyType;
    private String reason;
    private LocalDateTime expireTime;
    private Integer status;
    private String approveReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
