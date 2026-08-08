package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("infra_alert")
public class InfraAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String alertName;
    private String alertLevel;
    private String alertType;
    private String targetType;
    private String targetName;
    private String ruleExpr;
    private String message;
    private String status;
    private String handleSuggestion;
    private LocalDateTime lastTriggerTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
