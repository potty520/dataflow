package com.dgp.common;

import java.util.LinkedHashMap;

/** 轻量 Map 构造器（允许 null 值，便于拼装响应）。 */
public class M extends LinkedHashMap<String, Object> {

    public static M of() {
        return new M();
    }

    public static M of(Object... kv) {
        M m = new M();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    public M p(String k, Object v) {
        put(k, v);
        return this;
    }
}
