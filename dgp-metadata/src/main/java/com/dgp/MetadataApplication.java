package com.dgp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 元数据与血缘服务（从 dgp-core 绞杀迁出）。 */
@SpringBootApplication
public class MetadataApplication {
    public static void main(String[] args) {
        SpringApplication.run(MetadataApplication.class, args);
    }
}
