package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_schedule_execution_log")
public class DevScheduleExecutionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String taskType;
    private Long tenantId;
    private String status;
    private String output;
    private String errorLog;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private LocalDateTime createTime;
}
