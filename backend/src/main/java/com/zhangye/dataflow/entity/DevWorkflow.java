package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_workflow")
public class DevWorkflow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private String name;
    private String description;
    private String dagJson;
    private String scheduleCron;
    private Integer scheduleEnabled;
    private String parallelStrategy;
    private String jobType;
    private String depWorkflowIds;
    private Integer retryCount;
    private Integer retryIntervalSec;
    private String onFailure;
    private Integer alertEnabled;
    private String alertChannels;
    private String alertConditions;
    private Integer priority;
    private String status;
    private LocalDateTime lastRunTime;
    private String lastRunStatus;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
