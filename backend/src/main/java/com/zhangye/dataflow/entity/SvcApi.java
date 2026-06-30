package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("svc_api")
public class SvcApi {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String apiName;
    private String requestMethod;
    private String updateFrequency;
    private String description;
    private Long catalogId;
    private String createType;
    private String sqlContent;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
