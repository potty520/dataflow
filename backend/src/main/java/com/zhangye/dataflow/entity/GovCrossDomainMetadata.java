package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gov_cross_domain_metadata")
public class GovCrossDomainMetadata {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String sourceName;
    private String sourceType;
    private String exportFormat;
    private String metadataJson;
    private String syncStatus;
    private String version;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
