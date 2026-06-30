package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gov_word")
public class GovWord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long catalogId;
    private String wordEn;
    private String wordCn;
    private String definition;
    private String version;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
