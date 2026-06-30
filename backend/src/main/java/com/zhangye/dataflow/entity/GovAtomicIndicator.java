package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gov_atomic_indicator")
public class GovAtomicIndicator {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String calcLogic;
    private String sourceField;
    private Integer status;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
