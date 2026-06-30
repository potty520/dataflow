package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gov_business_domain")
public class GovBusinessDomain {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long layerId;
    private Long parentId;
    private String domainName;
    private String domainCode;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
