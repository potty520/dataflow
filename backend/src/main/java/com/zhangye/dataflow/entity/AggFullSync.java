package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agg_full_sync")
public class AggFullSync {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String syncType;
    private Long sourceId;
    private Long targetId;
    private String tablesJson;
    private Integer faultLimit;
    private String targetAction;
    private String remark;
    private String status;
    private Integer tableTotal;
    private Integer successCount;
    private Integer failCount;
    private Integer runningCount;
    private LocalDateTime lastRunTime;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
