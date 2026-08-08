package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agg_migration_mapping")
public class AggMigrationMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private Long sourceTableId;
    private Long targetTableId;
    private String writeMode;
    private Integer faultTolerance;
    private String preSql;
    private String fieldMapping;
    private String remark;
    private String status;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String sourceTableName;
    @TableField(exist = false)
    private String targetTableName;
}
