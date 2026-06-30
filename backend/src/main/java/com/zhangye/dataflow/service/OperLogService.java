package com.zhangye.dataflow.service;

import com.zhangye.dataflow.entity.SysOperLog;
import com.zhangye.dataflow.mapper.SysOperLogMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@Service
public class OperLogService {

    private final SysOperLogMapper operLogMapper;

    public OperLogService(SysOperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    public void log(HttpServletRequest request, String eventName, String resourceType, String resourceName, boolean success, String detail) {
        SysOperLog log = new SysOperLog();
        log.setTenantId(SecurityUtils.getCurrentTenantId());
        log.setUserId(SecurityUtils.getCurrentUserId());
        log.setUsername(SecurityUtils.getCurrentUsername());
        log.setEventName(eventName);
        log.setResult(success ? 1 : 0);
        log.setMethod(request != null ? request.getMethod() : null);
        log.setResourceType(resourceType);
        log.setResourceName(resourceName);
        log.setIp(request != null ? request.getRemoteAddr() : null);
        log.setDetail(detail);
        log.setCreateTime(LocalDateTime.now());
        operLogMapper.insert(log);
    }
}
