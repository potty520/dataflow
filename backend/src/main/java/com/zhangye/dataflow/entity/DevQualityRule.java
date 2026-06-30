package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_quality_rule")
public class DevQualityRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String dimension;
    private String ruleLevel;
    private String ruleExpr;
    private String templateType;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
