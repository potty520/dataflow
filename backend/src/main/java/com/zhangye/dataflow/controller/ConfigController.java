package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.SysConfig;
import com.zhangye.dataflow.mapper.SysConfigMapper;
import com.zhangye.dataflow.service.ConfigService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/platform/config")
public class ConfigController {

    private final SysConfigMapper configMapper;
    private final ConfigService configService;

    public ConfigController(SysConfigMapper configMapper, ConfigService configService) {
        this.configMapper = configMapper;
        this.configService = configService;
    }

    @GetMapping
    public Result<List<SysConfig>> listAll() {
        return Result.ok(configMapper.selectList(null));
    }

    @GetMapping("/{key}")
    public Result<String> getValue(@PathVariable String key) {
        return Result.ok(configService.getConfigValue(key));
    }

    @PutMapping("/{key}")
    public Result<Void> updateValue(@PathVariable String key, @RequestBody java.util.Map<String, String> body) {
        String value = body.get("configValue");
        if (value == null) {
            return Result.fail("configValue is required");
        }
        SysConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config != null) {
            config.setConfigValue(value);
            config.setUpdateTime(LocalDateTime.now());
            configMapper.updateById(config);
        } else {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(body.get("description"));
            config.setEnv(body.get("env") != null ? body.get("env") : "DEV");
            config.setUpdateTime(LocalDateTime.now());
            configMapper.insert(config);
        }
        configService.setConfigValue(key, value);
        return Result.ok();
    }
}
