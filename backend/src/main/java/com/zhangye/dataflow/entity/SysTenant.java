package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_tenant")
public class SysTenant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantCode;
    private String tenantName;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
