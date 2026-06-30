package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dev_quality_score_history")
public class DevQualityScoreHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private BigDecimal score;
    private Integer issueCount;
    private LocalDateTime checkTime;
}
