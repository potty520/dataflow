package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gov_business_limit")
public class GovBusinessLimit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String sourceField;
    private String calcLogic;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
