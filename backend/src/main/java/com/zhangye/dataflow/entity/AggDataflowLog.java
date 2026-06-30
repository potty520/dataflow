package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据流执行日志实体 (F-INT-008)
 */
@Data
@TableName("agg_dataflow_log")
public class AggDataflowLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dataflowId;
    private String runId;
    private String logLevel;
    private String message;
    private Long recordCount;
    private LocalDateTime createTime;
}
