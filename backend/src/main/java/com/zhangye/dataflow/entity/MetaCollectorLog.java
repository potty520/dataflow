package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("meta_collector_log")
public class MetaCollectorLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String status;
    private Integer tableCount;
    private Integer fieldCount;
    private String errorLog;
    private Long durationMs;
    private LocalDateTime createTime;
}
