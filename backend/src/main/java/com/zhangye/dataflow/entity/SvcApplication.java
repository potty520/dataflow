package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("svc_application")
public class SvcApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String appName;
    private String appKey;
    private String appSecret;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
