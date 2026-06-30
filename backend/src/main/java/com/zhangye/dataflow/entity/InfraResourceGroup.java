package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("infra_resource_group")
public class InfraResourceGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String groupName;
    private String resourceType;
    private String resourceKey;
    private String description;
    private Integer maxCores;
    private BigDecimal maxMemoryGb;
    private String status;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
