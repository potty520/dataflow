package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_project")
public class DevProject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String domain;
    private String description;
    private String createBy;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
