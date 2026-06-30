package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_lineage")
public class AssetLineage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long sourceTableId;
    private Long targetTableId;
    private String relationType;
    private LocalDateTime createTime;
}
