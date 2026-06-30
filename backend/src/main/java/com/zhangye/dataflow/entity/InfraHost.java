package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("infra_host")
public class InfraHost {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String hostname;
    private String ip;
    private Integer cpuCores;
    private Integer memoryGb;
    private Integer diskGb;
    private String status;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
