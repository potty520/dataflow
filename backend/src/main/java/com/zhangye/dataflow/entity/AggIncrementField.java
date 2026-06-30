package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agg_increment_field")
public class AggIncrementField {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String tableName;
    private String fieldName;
    private Integer fieldPosition;
    private String fieldType;
    private String fieldComment;
    private String incrementValue;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
