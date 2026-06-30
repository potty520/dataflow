package com.zhangye.dataflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.zhangye.dataflow.mapper")
public class DataflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataflowApplication.class, args);
    }
}
