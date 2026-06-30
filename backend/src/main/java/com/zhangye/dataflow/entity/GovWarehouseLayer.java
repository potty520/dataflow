package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gov_warehouse_layer")
public class GovWarehouseLayer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String layerCode;
    private String layerName;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
