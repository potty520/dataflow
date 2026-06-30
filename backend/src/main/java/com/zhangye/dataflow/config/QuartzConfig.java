package com.zhangye.dataflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import java.util.Properties;

@Configuration
public class QuartzConfig {

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean() {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setAutoStartup(true);
        factory.setWaitForJobsToCompleteOnShutdown(true);
        factory.setOverwriteExistingJobs(true);

        Properties props = new Properties();
        props.put("org.quartz.scheduler.instanceName", "DataflowScheduler");
        props.put("org.quartz.threadPool.threadCount", "10");
        factory.setQuartzProperties(props);

        return factory;
    }
}
