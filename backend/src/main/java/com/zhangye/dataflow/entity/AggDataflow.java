package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agg_dataflow")
public class AggDataflow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private Long sourceId;
    private Long targetId;
    private String writeMode;
    private Integer faultTolerance;
    private String preSql;
    private String fieldMapping;
    private String scheduleCron;
    private Integer scheduleEnabled;
    private String status;
    private Long readCount;
    private Long writeCount;
    private LocalDateTime lastRunTime;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String sourceName;
    @TableField(exist = false)
    private String targetName;
}
