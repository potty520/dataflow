package com.zhangye.dataflow.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangye.dataflow.entity.SysUser;
import com.zhangye.dataflow.mapper.SysUserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        SysUser admin = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
        if (admin != null) {
            admin.setPassword(passwordEncoder.encode("admin123"));
            userMapper.updateById(admin);
        }
    }
}
