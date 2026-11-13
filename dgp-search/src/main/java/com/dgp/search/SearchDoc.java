package com.dgp.search;

import java.util.LinkedHashMap;
import java.util.Map;

/** 统一检索文档（元数据表 / 资产 / 标准 / 服务 API）。 */
public class SearchDoc {

    private String id;
    private String type;
    private Long tenantId;
    private String title;
    private String subtitle;
    private String content;
    private String layer;
    private String domain;
    private Double scoreHint;
    private Map<String, Object> payload = new LinkedHashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public Double getScoreHint() { return scoreHint; }
    public void setScoreHint(Double scoreHint) { this.scoreHint = scoreHint; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public String searchableText() {
        return String.join(" ",
                nz(title), nz(subtitle), nz(content), nz(layer), nz(domain),
                nz(payload.get("tags")), nz(payload.get("cnName")), nz(payload.get("code")));
    }

    private static String nz(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
