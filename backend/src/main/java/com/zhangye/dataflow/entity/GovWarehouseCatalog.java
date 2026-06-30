package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("gov_warehouse_catalog")
public class GovWarehouseCatalog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long layerId;
    private Long parentId;
    private String name;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private List<GovWarehouseCatalog> children;
}
