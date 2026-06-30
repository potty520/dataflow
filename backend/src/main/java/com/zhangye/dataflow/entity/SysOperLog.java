package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_oper_log")
public class SysOperLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String username;
    private String eventName;
    private Integer result;
    private String method;
    private String resourceType;
    private String resourceName;
    private String ip;
    private String detail;
    private LocalDateTime createTime;
}
