package com.dgp.common;

import java.util.LinkedHashMap;
import java.util.Map;

/** 统一响应体，严格对齐 00 基线 §6.2。 */
public class Result<T> {
    private int code;
    private String message;
    private T data;
    private String traceId;
    private long timestamp;

    public Result() {
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "ok";
        r.data = data;
        r.traceId = TraceIdFilter.current();
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static Result<Object> ok() {
        return ok(new LinkedHashMap<String, Object>());
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.data = null;
        r.traceId = TraceIdFilter.current();
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> Result<T> fail(ErrorCode ec) {
        return fail(ec.getCode(), ec.getMessage());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        m.put("data", data);
        m.put("traceId", traceId);
        m.put("timestamp", timestamp);
        return m;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
