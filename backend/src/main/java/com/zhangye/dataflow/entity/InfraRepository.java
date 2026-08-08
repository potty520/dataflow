package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("infra_repository")
public class InfraRepository {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String componentType;
    private String packageName;
    private String version;
    private String filePath;
    private BigDecimal packageSizeMb;
    private String checksum;
    private String compatibleOs;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
