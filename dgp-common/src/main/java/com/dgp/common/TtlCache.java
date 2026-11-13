package com.dgp.common;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 进程内 TTL 缓存：用于"读多写少"的聚合查询（概览 KPI、统计概览等）。
 *
 * <p>背景：治理概览一次请求要发 30 条 SQL，1 核机器上并发 10 时代价约 60ms/请求，
 * 成为并发瓶颈。这类看板数据允许秒级延迟，缓存后 QPS 可提升一个数量级。
 *
 * <p>实现要点：
 * <ul>
 *   <li>按 key 分段，{@code compute} 内加载，避免缓存击穿时同 key 并发重复查库；</li>
 *   <li>写操作可通过 {@link #evict(String)} 按前缀失效，保证"改完立刻可见"；</li>
 *   <li>容量按 key 数量自然有界（本系统 key 基数很小），无需 LRU。</li>
 * </ul>
 */
public final class TtlCache {

    private record Entry(Object value, long expireAt) {
    }

    private static final ConcurrentHashMap<String, Entry> MAP = new ConcurrentHashMap<>();

    private TtlCache() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, long ttlMillis, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        Entry hit = MAP.get(key);
        if (hit != null && hit.expireAt > now) {
            return (T) hit.value;
        }
        Entry entry = MAP.compute(key, (k, old) -> {
            long n = System.currentTimeMillis();
            if (old != null && old.expireAt > n) {
                return old;
            }
            return new Entry(loader.get(), n + ttlMillis);
        });
        return (T) entry.value;
    }

    /** 按前缀失效（如 "quality" 会让 "quality:score:1" 等全部过期）。 */
    public static void evict(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            MAP.clear();
            return;
        }
        MAP.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void clear() {
        MAP.clear();
    }
}
