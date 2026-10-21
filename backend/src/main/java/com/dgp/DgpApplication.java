package com.dgp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 数据治理平台 - 领域服务聚合启动类。
 *
 * <p>按《01-后端框架设计文档》的领域边界，各 dgp-* 服务的 controller/service 以
 * {@code com.dgp.<domain>} 包隔离，共享同一进程与同一数据源（见 README「交付形态说明」）。</p>
 */
@EnableScheduling
@SpringBootApplication
public class DgpApplication {
    public static void main(String[] args) {
        SpringApplication.run(DgpApplication.class, args);
    }
}
