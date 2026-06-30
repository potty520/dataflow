package com.zhangye.dataflow.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangye.dataflow.entity.SysRole;
import com.zhangye.dataflow.entity.SysUser;
import com.zhangye.dataflow.mapper.SysRoleMapper;
import com.zhangye.dataflow.mapper.SysUserMapper;
import com.zhangye.dataflow.mapper.SysUserRoleMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;

    public UserDetailsServiceImpl(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(user.getId());
        List<String> roleKeys = roleIds.isEmpty() ? java.util.Collections.emptyList() :
                roleMapper.selectBatchIds(roleIds).stream().map(SysRole::getRoleKey).collect(Collectors.toList());

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setTenantId(user.getTenantId());
        loginUser.setUsername(user.getUsername());
        loginUser.setPassword(user.getPassword());
        loginUser.setStatus(user.getStatus());
        loginUser.setRoles(roleKeys);
        return loginUser;
    }
}
