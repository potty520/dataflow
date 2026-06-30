package com.zhangye.dataflow.engine;

import com.zhangye.dataflow.entity.DevFileWatcher;
import com.zhangye.dataflow.mapper.DevFileWatcherMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件监听服务 - 监控目录文件变化触发工作流 (F-SCH-005)
 */
@Service
public class FileWatcherService {

    private static final Logger log = LoggerFactory.getLogger(FileWatcherService.class);

    private final DevFileWatcherMapper fileWatcherMapper;
    private final ScheduleEngine scheduleEngine;
    private final ConcurrentHashMap<Long, Long> lastProcessed = new ConcurrentHashMap<>();

    public FileWatcherService(DevFileWatcherMapper fileWatcherMapper, ScheduleEngine scheduleEngine) {
        this.fileWatcherMapper = fileWatcherMapper;
        this.scheduleEngine = scheduleEngine;
    }

    /**
     * Poll enabled watchers every 30 seconds
     */
    @Scheduled(fixedDelay = 30000)
    public void pollWatchers() {
        List<DevFileWatcher> watchers = fileWatcherMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DevFileWatcher>()
                        .eq(DevFileWatcher::getStatus, "ACTIVE")
        );

        for (DevFileWatcher watcher : watchers) {
            try {
                checkDirectory(watcher);
            } catch (Exception e) {
                log.warn("File watcher check failed: id={}, dir={}", watcher.getId(), watcher.getWatchDirectory(), e);
            }
        }
    }

    private void checkDirectory(DevFileWatcher watcher) {
        String dirPath = watcher.getWatchDirectory();
        if (dirPath == null || dirPath.isEmpty()) {
            log.warn("FileWatcher {} has no watch directory", watcher.getId());
            return;
        }

        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            log.debug("FileWatcher {} directory not found: {}", watcher.getId(), dirPath);
            return;
        }

        Pattern pattern = null;
        if (watcher.getFileNamePattern() != null && !watcher.getFileNamePattern().isEmpty()) {
            try {
                pattern = Pattern.compile(watcher.getFileNamePattern());
            } catch (PatternSyntaxException e) {
                log.warn("Invalid file name pattern: {}", watcher.getFileNamePattern());
            }
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        // Track the latest file timestamp for this watcher
        long lastKnown = lastProcessed.getOrDefault(watcher.getId(), 0L);

        for (File file : files) {
            if (file.isDirectory()) continue;
            if (file.lastModified() <= lastKnown) continue;
            if (pattern != null && !pattern.matcher(file.getName()).matches()) continue;

            // Found new/matching file - trigger workflow
            log.info("FileWatcher {} detected file: {} -> trigging workflow {}",
                    watcher.getId(), file.getName(), watcher.getTargetWorkflowId());

            try {
                if (watcher.getTargetWorkflowId() != null) {
                    org.quartz.JobDataMap jobData = new org.quartz.JobDataMap();
                    jobData.put("workflowId", watcher.getTargetWorkflowId());
                    jobData.put("tenantId", watcher.getTenantId());
                    jobData.put("triggerBy", "FILE_WATCHER");
                    jobData.put("triggerFile", file.getAbsolutePath());
                    scheduleEngine.triggerNow(watcher.getTargetWorkflowId(), "WORKFLOW", jobData);
                }
            } catch (Exception e) {
                log.error("Failed to trigger workflow from file watcher", e);
            }
        }

        // Update last known timestamp
        lastProcessed.put(watcher.getId(), System.currentTimeMillis());
    }
}
