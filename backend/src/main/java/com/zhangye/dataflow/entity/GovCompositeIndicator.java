package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gov_composite_indicator")
public class GovCompositeIndicator {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String indicatorCode;
    private String indicatorName;
    private String expression;
    private String indicatorIds;
    private String unit;
    private String description;
    private String status;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
