package com.zhangye.dataflow.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysUserRoleMapper {

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_role(user_id, role_id) VALUES(#{userId}, #{roleId})")
    void insert(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
