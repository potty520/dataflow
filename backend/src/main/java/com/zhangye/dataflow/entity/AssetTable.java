package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_table")
public class AssetTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String tableEn;
    private String tableCn;
    private String databaseName;
    private Long datasourceId;
    private String layerCode;
    private Long rowCount;
    private Integer isKeyAsset;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
