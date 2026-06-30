package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_field")
public class AssetField {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tableId;
    private String fieldEn;
    private String fieldCn;
    private String fieldType;
    private String description;
    private Integer isSensitive;
    private LocalDateTime createTime;
}
