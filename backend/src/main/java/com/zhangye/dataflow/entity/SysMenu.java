package com.zhangye.dataflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_menu")
public class SysMenu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String menuCode;
    private String menuName;
    private String description;
    private String path;
    private String component;
    private Integer menuType;
    private String serviceName;
    private String icon;
    private Integer sortOrder;
    private Integer visible;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private List<SysMenu> children;
}
