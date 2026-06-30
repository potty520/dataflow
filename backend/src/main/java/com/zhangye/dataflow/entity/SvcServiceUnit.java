package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("svc_service_unit")
public class SvcServiceUnit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String unitName;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
