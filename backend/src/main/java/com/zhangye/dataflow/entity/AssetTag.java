package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_tag")
public class AssetTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String tagName;
    private String tagType;
    private String tagColor;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
