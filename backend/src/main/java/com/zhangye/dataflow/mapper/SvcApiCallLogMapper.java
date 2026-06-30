package com.zhangye.dataflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhangye.dataflow.entity.SvcApiCallLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SvcApiCallLogMapper extends BaseMapper<SvcApiCallLog> {
}
