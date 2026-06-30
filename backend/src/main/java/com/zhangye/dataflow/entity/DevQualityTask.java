package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dev_quality_task")
public class DevQualityTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long ruleId;
    private String targetTable;
    private String status;
    private BigDecimal score;
    private Integer issueCount;
    private LocalDateTime lastRunTime;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
