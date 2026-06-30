package com.zhangye.dataflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhangye.dataflow.entity.SvcRateLimit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SvcRateLimitMapper extends BaseMapper<SvcRateLimit> {
}
