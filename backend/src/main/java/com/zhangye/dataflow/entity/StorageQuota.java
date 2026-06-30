package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("storage_quota")
public class StorageQuota {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long datasourceId;
    private String databaseName;
    private BigDecimal maxSizeGb;
    private BigDecimal currentUsageGb;
    private Integer alertThresholdPct;
    private String status;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
