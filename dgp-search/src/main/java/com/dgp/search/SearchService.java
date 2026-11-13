package com.dgp.search;

import com.dgp.common.Db;
import com.dgp.security.AuthContext;
import com.dgp.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SearchService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final CatalogLoader loader;
    private final LocalSearchIndex local;
    private final ElasticsearchIndex es;
    private final Db db;
    private final boolean reindexOnStartup;

    public SearchService(CatalogLoader loader, LocalSearchIndex local,
                         @Autowired(required = false) ElasticsearchIndex es,
                         Db db,
                         @Value("${dgp.search.reindex-on-startup:true}") boolean reindexOnStartup) {
        this.loader = loader;
        this.local = local;
        this.es = es;
        this.db = db;
        this.reindexOnStartup = reindexOnStartup;
    }

    private SearchIndex active() {
        return es != null ? es : local;
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("engine", active().name());
        m.put("elasticsearch", es != null);
        m.put("localDocs", local.size());
        return m;
    }

    public int reindex() {
        List<SearchDoc> docs = loader.loadAll();
        local.replaceAll(docs);
        if (es != null) {
            try {
                es.replaceAll(docs);
            } catch (Exception e) {
                log.warn("ES 全量索引失败，已回退本地索引：{}", e.getMessage());
            }
        }
        log.info("检索全量重建完成：{} 条，引擎={}", docs.size(), active().name());
        return docs.size();
    }

    public Map<String, Object> search(String keyword, String types, String layer, String domain,
                                      int pageNum, int pageSize) {
        AuthUser u = AuthContext.get();
        boolean admin = u != null && u.isAdmin();
        Long tenantId = u == null ? null : u.getTenantId();
        Set<String> typeSet = types == null || types.isBlank() ? Set.of()
                : Arrays.stream(types.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(String::toUpperCase).collect(Collectors.toSet());
        SearchResult r;
        try {
            r = active().search(keyword, typeSet, tenantId, admin, layer, domain, pageNum, pageSize);
        } catch (Exception e) {
            log.warn("主引擎检索失败，改用本地索引：{}", e.getMessage());
            r = local.search(keyword, typeSet, tenantId, admin, layer, domain, pageNum, pageSize);
        }
        // 兼容元数据页：type=TABLE 时 records 展平为表结构
        List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (SearchDoc d : r.records) {
            Map<String, Object> row = new LinkedHashMap<>();
            if (d.getPayload() != null) row.putAll(d.getPayload());
            row.put("_id", d.getId());
            row.put("_type", d.getType());
            row.put("_title", d.getTitle());
            row.put("_subtitle", d.getSubtitle());
            records.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", r.total);
        out.put("pageNum", pageNum);
        out.put("pageSize", pageSize);
        out.put("records", records);
        out.put("engine", r.engine);
        out.put("costMs", r.costMs);
        return out;
    }

    /** 消费 outbox：增量同步。 */
    @Scheduled(fixedDelay = 15_000, initialDelay = 25_000)
    public void pollOutbox() {
        try {
            List<Map<String, Object>> rows = db.list(
                    "SELECT * FROM sys_search_outbox WHERE processed_at IS NULL ORDER BY id LIMIT 100");
            for (Map<String, Object> row : rows) {
                long id = ((Number) row.get("id")).longValue();
                String type = String.valueOf(row.get("entityType"));
                String entityId = String.valueOf(row.get("entityId"));
                String op = String.valueOf(row.get("op"));
                try {
                    if ("DELETE".equalsIgnoreCase(op)) {
                        active().delete(type + ":" + entityId);
                        local.delete(type + ":" + entityId);
                    } else {
                        // 简化：触发局部重载对应类型
                        reindexType(type);
                    }
                    db.update("UPDATE sys_search_outbox SET processed_at=? WHERE id=?", LocalDateTime.now(), id);
                } catch (Exception e) {
                    log.debug("outbox {} 处理失败：{}", id, e.getMessage());
                }
            }
        } catch (Exception e) {
            // 表未就绪时忽略
        }
    }

    private void reindexType(String type) {
        List<SearchDoc> docs = switch (type.toUpperCase()) {
            case "TABLE" -> loader.loadTables();
            case "ASSET" -> loader.loadAssets();
            case "STANDARD" -> loader.loadStandards();
            case "API" -> loader.loadApis();
            default -> List.of();
        };
        for (SearchDoc d : docs) {
            local.upsert(d);
            if (es != null) {
                try {
                    es.upsert(d);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (reindexOnStartup) {
            try {
                reindex();
            } catch (Exception e) {
                log.warn("启动重建索引失败：{}", e.getMessage());
            }
        }
    }
}
