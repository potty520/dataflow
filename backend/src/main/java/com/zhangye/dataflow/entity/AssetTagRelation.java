package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_tag_relation")
public class AssetTagRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assetTableId;
    private Long tagId;
    private LocalDateTime createTime;
}
