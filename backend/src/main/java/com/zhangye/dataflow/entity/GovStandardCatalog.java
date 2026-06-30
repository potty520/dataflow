package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gov_standard_catalog")
public class GovStandardCatalog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long parentId;
    private String name;
    private String catalogType;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
