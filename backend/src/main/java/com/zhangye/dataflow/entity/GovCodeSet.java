package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gov_code_set")
public class GovCodeSet {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long catalogId;
    private String code;
    private String name;
    private String codeValue;
    private String version;
    private String description;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
