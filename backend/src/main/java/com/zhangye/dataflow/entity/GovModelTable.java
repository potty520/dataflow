package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gov_model_table")
public class GovModelTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String layerCode;
    private String tableEn;
    private String tableCn;
    private Long datasourceId;
    private String cleanRules;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
