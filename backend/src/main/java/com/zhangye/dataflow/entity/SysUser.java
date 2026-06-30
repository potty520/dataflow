package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long deptId;
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private String avatar;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String deptName;
    @TableField(exist = false)
    private List<Long> roleIds;
    @TableField(exist = false)
    private List<String> roleNames;
}
