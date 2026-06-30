package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脚本版本管理实体 (F-DEV-008)
 */
@Data
@TableName("dev_script_version")
public class DevScriptVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private Integer versionNum;
    private String content;
    private String changeNote;
    private String createBy;
    private LocalDateTime createTime;
}
