package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("svc_catalog")
public class SvcCatalog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long parentId;
    private String name;
    private Integer enabled;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
