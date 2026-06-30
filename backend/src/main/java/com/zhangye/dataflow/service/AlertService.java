package com.zhangye.dataflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 告警通知服务 - 支持邮件/钉钉/企微等渠道 (F-SCH-009)
 * 当前实现为日志记录，为后续真实集成预留接口
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final String ALERT_PREFIX = "[DATAFLOW-ALERT]";

    /**
     * Send alert to configured channels
     * @param channels Comma-separated channel names: EMAIL,DINGTALK,WEWORK,SMS
     * @param title Alert title
     * @param message Alert message body
     */
    public void sendAlert(String channels, String title, String message) {
        if (channels == null || channels.trim().isEmpty()) {
            log.warn("{} No alert channels configured, alert skipped: {}", ALERT_PREFIX, title);
            return;
        }

        List<String> channelList = Arrays.asList(channels.split(","));
        for (String channel : channelList) {
            String ch = channel.trim().toUpperCase();
            switch (ch) {
                case "EMAIL":
                    sendEmailAlert(title, message);
                    break;
                case "DINGTALK":
                    sendDingTalkAlert(title, message);
                    break;
                case "WEWORK":
                    sendWeWorkAlert(title, message);
                    break;
                case "SMS":
                    sendSmsAlert(title, message);
                    break;
                default:
                    log.warn("{} Unknown alert channel: {}", ALERT_PREFIX, ch);
            }
        }
    }

    /**
     * Send alert for workflow/task status events
     * @param channels Alert channels
     * @param conditions Comma-separated conditions: FAILURE,TIMEOUT,SUCCESS
     * @param taskName Task or workflow name
     * @param taskId Task ID
     * @param status Task status (FAILURE/TIMEOUT/SUCCESS)
     * @param detail Detailed message
     */
    public void sendConditionalAlert(String channels, String conditions,
                                      String taskName, Long taskId, String status, String detail) {
        if (channels == null || channels.trim().isEmpty()) {
            return;
        }
        if (conditions == null || conditions.trim().isEmpty()) {
            return;
        }

        List<String> conditionList = Arrays.asList(conditions.split(","));
        boolean shouldAlert = conditionList.stream()
                .anyMatch(c -> c.trim().equalsIgnoreCase(status));
        if (!shouldAlert) {
            return;
        }

        String title = String.format("[%s] %s - ID: %d", status, taskName, taskId);
        String message = String.format(
                "任务: %s\n" +
                "任务ID: %d\n" +
                "状态: %s\n" +
                "时间: %s\n" +
                "详情: %s",
                taskName, taskId, status,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                detail != null ? detail : "无"
        );

        sendAlert(channels, title, message);
    }

    private void sendEmailAlert(String title, String message) {
        // TODO: Integrate with email service (SMTP / JavaMailSender)
        log.info("{} [EMAIL] To: admin@zhangye.gov.cn | Subject: {} | Body: {}", ALERT_PREFIX, title, message);
    }

    private void sendDingTalkAlert(String title, String message) {
        // TODO: Integrate with DingTalk webhook
        log.info("{} [DINGTALK] Webhook alert | Title: {} | Message: {}", ALERT_PREFIX, title, message);
    }

    private void sendWeWorkAlert(String title, String message) {
        // TODO: Integrate with WeChat Work webhook
        log.info("{} [WEWORK] Webhook alert | Title: {} | Message: {}", ALERT_PREFIX, title, message);
    }

    private void sendSmsAlert(String title, String message) {
        // TODO: Integrate with SMS gateway (Alibaba Cloud SMS / Twilio)
        log.info("{} [SMS] To: admin | Title: {} | Message: {}", ALERT_PREFIX, title, message);
    }
}
