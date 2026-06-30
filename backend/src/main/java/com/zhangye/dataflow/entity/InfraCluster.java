package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("infra_cluster")
public class InfraCluster {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String description;
    private Integer hostCount;
    private String status;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
