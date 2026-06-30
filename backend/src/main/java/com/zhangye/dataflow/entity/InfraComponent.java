package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("infra_component")
public class InfraComponent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long hostId;
    private String componentType;
    private String version;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
