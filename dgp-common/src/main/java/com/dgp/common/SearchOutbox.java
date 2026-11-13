package com.dgp.common;

import com.dgp.security.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 写路径投递检索 Outbox，供 dgp-search 异步消费。 */
@Component
public class SearchOutbox {

    private static final Logger log = LoggerFactory.getLogger(SearchOutbox.class);
    private final Db db;

    public SearchOutbox(Db db) {
        this.db = db;
    }

    public void upsert(String entityType, Object entityId) {
        publish(entityType, entityId, "UPSERT");
    }

    public void delete(String entityType, Object entityId) {
        publish(entityType, entityId, "DELETE");
    }

    private void publish(String entityType, Object entityId, String op) {
        try {
            db.insert("INSERT INTO sys_search_outbox(tenant_id,entity_type,entity_id,op,create_time) VALUES(?,?,?,?,?)",
                    AuthContext.tenantId(), entityType, String.valueOf(entityId), op, LocalDateTime.now());
        } catch (Exception e) {
            log.debug("search outbox skip: {}", e.getMessage());
        }
    }
}
