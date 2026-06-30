package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("meta_collector_task")
public class MetaCollectorTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String taskName;
    private Long datasourceId;
    private String collectType;
    private String targetPattern;
    private String cronExpr;
    private Integer triggerOnChange;
    private String status;
    private LocalDateTime lastRunTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
