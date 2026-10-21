package com.dgp.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/** 雪花 ID（workerId 单机固定 1，生产由 Nacos 分配），对齐 01 文档 §5.5。 */
public final class Ids {

    private static final long EPOCH = 1735660800000L; // 2025-01-01T00:00:00+08:00
    private static final long WORKER = 1L;
    private static final AtomicInteger SEQ = new AtomicInteger(0);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private Ids() {
    }

    public static synchronized long next() {
        long now = System.currentTimeMillis();
        int seq = SEQ.updateAndGet(v -> (v + 1) & 0xFFF);
        return ((now - EPOCH) << 22) | (WORKER << 12) | seq;
    }

    private static final AtomicInteger CODE_SEQ = new AtomicInteger(1000);

    public static String code(String prefix) {
        int n = CODE_SEQ.updateAndGet(v -> v >= 9999 ? 1000 : v + 1);
        return prefix + "-" + LocalDateTime.now().format(TS) + "-" + n;
    }

    public static String uuid() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
