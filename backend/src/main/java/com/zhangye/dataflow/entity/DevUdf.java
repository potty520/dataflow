package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_udf")
public class DevUdf {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String udfName;
    private String udfType;
    private String owner;
    private String className;
    private String jarPath;
    private String createSql;
    private String description;
    private String status;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
