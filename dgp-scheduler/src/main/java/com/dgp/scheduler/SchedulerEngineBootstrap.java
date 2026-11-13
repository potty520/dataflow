package com.dgp.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时打印调度引擎模式，避免 local + xxl 双跑误配。 */
@Component
public class SchedulerEngineBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchedulerEngineBootstrap.class);

    @Value("${dgp.scheduler.engine:local}")
    private String engine;

    @Value("${dgp.scheduler.xxl.enabled:false}")
    private boolean xxlEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (xxlEnabled && "local".equalsIgnoreCase(engine)) {
            log.warn("XXL 已启用但 engine=local：LocalDueScanner 与 XXL 可能双跑。"
                    + "请设置 DGP_SCHEDULER_ENGINE=xxl，并在 Admin 注册 dgpScanDueAll 或按作业注册 Handler");
        }
        log.info("调度引擎模式 engine={} xxlEnabled={}", engine, xxlEnabled);
    }
}
