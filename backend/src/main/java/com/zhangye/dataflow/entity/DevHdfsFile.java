package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_hdfs_file")
public class DevHdfsFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String filePath;
    private String fileName;
    private Integer isDir;
    private Long fileSize;
    private String contentType;
    private String parentPath;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
