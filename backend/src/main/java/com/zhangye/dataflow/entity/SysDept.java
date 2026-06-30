package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_dept")
public class SysDept {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long parentId;
    private String deptName;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private List<SysDept> children;
}
