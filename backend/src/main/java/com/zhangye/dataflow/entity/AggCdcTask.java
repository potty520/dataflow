package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agg_cdc_task")
public class AggCdcTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private Long sourceId;
    private Long targetId;
    private String operationMode;
    private String fieldMapping;
    private String remark;
    private String status;
    private LocalDateTime startTime;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
