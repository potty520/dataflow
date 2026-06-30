package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件监听触发源实体 (F-SCH-005)
 */
@Data
@TableName("dev_file_watcher")
public class DevFileWatcher {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String watchDirectory;
    private String fileNamePattern;
    private Long targetWorkflowId;
    private String status;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
