package com.dgp.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 分页响应体，对齐 00 基线 §6.2。 */
public class PageResult {
    private List<Map<String, Object>> records = new ArrayList<>();
    private long total;
    private int pageNum = 1;
    private int pageSize = 20;

    public static PageResult of(List<Map<String, Object>> records, long total, int pageNum, int pageSize) {
        PageResult p = new PageResult();
        p.records = records;
        p.total = total;
        p.pageNum = pageNum;
        p.pageSize = pageSize;
        return p;
    }

    public static PageResult empty(int pageNum, int pageSize) {
        return of(new ArrayList<>(), 0, pageNum, pageSize);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("records", records);
        m.put("total", total);
        m.put("pageNum", pageNum);
        m.put("pageSize", pageSize);
        m.put("pages", pageSize <= 0 ? 0 : (int) Math.ceil((double) total / pageSize));
        return m;
    }

    public List<Map<String, Object>> getRecords() {
        return records;
    }

    public long getTotal() {
        return total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }
}
