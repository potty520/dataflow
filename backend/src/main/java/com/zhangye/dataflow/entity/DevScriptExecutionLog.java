package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_script_execution_log")
public class DevScriptExecutionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private Long tenantId;
    private String status;
    private String output;
    private String errorLog;
    private Integer exitCode;
    private Long durationMs;
    private String triggerBy;
    private LocalDateTime createTime;
}
