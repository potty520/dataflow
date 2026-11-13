package com.dgp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 业务域过渡态服务（dgp-core）：质量/迁移/资产等仍聚合于此；元数据 API 已迁至 dgp-metadata。
 */
@EnableDiscoveryClient
@EnableScheduling
@SpringBootApplication
public class DgpApplication {
    public static void main(String[] args) {
        SpringApplication.run(DgpApplication.class, args);
    }
}
