package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agg_datasource")
public class AggDatasource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;
    private String filePath;
    private String configJson;
    private Integer locked;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
