package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_workflow")
public class DevWorkflow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private String name;
    private String dagJson;
    private String status;
    private String scheduleCron;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
