package com.dgp.search;

import com.dgp.common.Db;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 从治理库装载可检索目录（与 core 共用库）。 */
@Component
public class CatalogLoader {

    private final Db db;

    public CatalogLoader(Db db) {
        this.db = db;
    }

    public List<SearchDoc> loadAll() {
        List<SearchDoc> out = new ArrayList<>();
        out.addAll(loadTables());
        out.addAll(loadAssets());
        out.addAll(loadStandards());
        out.addAll(loadApis());
        return out;
    }

    public List<SearchDoc> loadTables() {
        List<SearchDoc> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = db.list(
                    "SELECT t.id,t.tenant_id,t.name,t.cn_name,t.layer,t.domain,t.description,t.tags,"
                            + "t.quality_score,t.sensitive_level,t.obj_type,d.name AS ds_name "
                            + "FROM md_table t LEFT JOIN md_datasource d ON d.id=t.datasource_id");
            for (Map<String, Object> r : rows) {
                SearchDoc d = new SearchDoc();
                d.setId("TABLE:" + r.get("id"));
                d.setType("TABLE");
                d.setTenantId(asLong(r.get("tenantId")));
                d.setTitle(str(r.get("name")));
                d.setSubtitle(str(r.get("cnName")));
                d.setContent(str(r.get("description")) + " " + str(r.get("tags")));
                d.setLayer(str(r.get("layer")));
                d.setDomain(str(r.get("domain")));
                d.setScoreHint(asDouble(r.get("qualityScore")));
                d.getPayload().putAll(r);
                out.add(d);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public List<SearchDoc> loadAssets() {
        List<SearchDoc> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = db.list(
                    "SELECT id,tenant_id,asset_name,table_name,category,domain,business_desc,value_score,owner "
                            + "FROM ast_catalog");
            for (Map<String, Object> r : rows) {
                SearchDoc d = new SearchDoc();
                d.setId("ASSET:" + r.get("id"));
                d.setType("ASSET");
                d.setTenantId(asLong(r.get("tenantId")));
                d.setTitle(str(r.get("assetName")));
                d.setSubtitle(str(r.get("tableName")));
                d.setContent(str(r.get("businessDesc")));
                d.setDomain(str(r.get("domain")));
                d.setScoreHint(asDouble(r.get("valueScore")));
                d.getPayload().putAll(r);
                out.add(d);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public List<SearchDoc> loadStandards() {
        List<SearchDoc> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = db.list(
                    "SELECT id,tenant_id,code,name,cn_name,category,description FROM std_element");
            for (Map<String, Object> r : rows) {
                SearchDoc d = new SearchDoc();
                d.setId("STANDARD:" + r.get("id"));
                d.setType("STANDARD");
                d.setTenantId(asLong(r.get("tenantId")));
                d.setTitle(str(r.get("name")));
                d.setSubtitle(str(r.get("code")));
                d.setContent(str(r.get("cnName")) + " " + str(r.get("description")));
                d.getPayload().putAll(r);
                out.add(d);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public List<SearchDoc> loadApis() {
        List<SearchDoc> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = db.list(
                    "SELECT id,tenant_id,code,name,api_path,method,description FROM svc_api");
            for (Map<String, Object> r : rows) {
                SearchDoc d = new SearchDoc();
                d.setId("API:" + r.get("id"));
                d.setType("API");
                d.setTenantId(asLong(r.get("tenantId")));
                d.setTitle(str(r.get("name")));
                d.setSubtitle(str(r.get("apiPath")));
                d.setContent(str(r.get("code")) + " " + str(r.get("description")));
                d.getPayload().putAll(r);
                out.add(d);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static Double asDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }
}
