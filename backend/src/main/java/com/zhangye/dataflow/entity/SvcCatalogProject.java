package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("svc_catalog_project")
public class SvcCatalogProject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String projectName;
    private String description;
    private Integer enabled;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
