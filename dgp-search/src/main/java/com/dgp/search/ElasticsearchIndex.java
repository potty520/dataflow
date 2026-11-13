package com.dgp.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Elasticsearch 8 索引实现。 */
@Component
@ConditionalOnProperty(name = "dgp.search.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchIndex implements SearchIndex {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndex.class);

    private final String uris;
    private final String indexName;
    private RestClient restClient;
    private ElasticsearchClient client;

    public ElasticsearchIndex(
            @Value("${spring.elasticsearch.uris:http://127.0.0.1:9200}") String uris,
            @Value("${dgp.search.index-name:dgp_catalog}") String indexName) {
        this.uris = uris;
        this.indexName = indexName;
    }

    @PostConstruct
    public void init() {
        java.net.URI uri = java.net.URI.create(uris.split(",")[0].trim());
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        String h = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        int p = uri.getPort() > 0 ? uri.getPort() : 9200;
        restClient = RestClient.builder(new HttpHost(h, p, scheme)).build();
        client = new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper(new ObjectMapper())));
        try {
            boolean exists = client.indices().exists(e -> e.index(indexName)).value();
            if (!exists) {
                client.indices().create(c -> c.index(indexName)
                        .mappings(m -> m
                                .properties("type", p2 -> p2.keyword(k -> k))
                                .properties("tenantId", p2 -> p2.long_(l -> l))
                                .properties("title", p2 -> p2.text(t -> t))
                                .properties("subtitle", p2 -> p2.text(t -> t))
                                .properties("content", p2 -> p2.text(t -> t))
                                .properties("layer", p2 -> p2.keyword(k -> k))
                                .properties("domain", p2 -> p2.keyword(k -> k))
                                .properties("searchableText", p2 -> p2.text(t -> t))
                        ));
            }
            log.info("Elasticsearch 索引就绪：{}", indexName);
        } catch (Exception e) {
            log.error("初始化 ES 索引失败：{}", e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    @PreDestroy
    public void close() throws Exception {
        if (restClient != null) restClient.close();
    }

    @Override
    public String name() {
        return "elasticsearch";
    }

    @Override
    public void replaceAll(List<SearchDoc> docs) {
        try {
            for (SearchDoc d : docs) {
                upsert(d);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void upsert(SearchDoc doc) {
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("type", doc.getType());
            body.put("tenantId", doc.getTenantId());
            body.put("title", doc.getTitle());
            body.put("subtitle", doc.getSubtitle());
            body.put("content", doc.getContent());
            body.put("layer", doc.getLayer());
            body.put("domain", doc.getDomain());
            body.put("scoreHint", doc.getScoreHint());
            body.put("payload", doc.getPayload());
            body.put("searchableText", doc.searchableText());
            client.index(i -> i.index(indexName).id(doc.getId()).document(body));
        } catch (Exception e) {
            throw new IllegalStateException("ES upsert failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String id) {
        try {
            client.delete(d -> d.index(indexName).id(id));
        } catch (Exception e) {
            log.warn("ES delete {}: {}", id, e.getMessage());
        }
    }

    @Override
    public SearchResult search(String keyword, Set<String> types, Long tenantId, boolean admin,
                               String layer, String domain, int pageNum, int pageSize) {
        long t0 = System.currentTimeMillis();
        try {
            int from = Math.max(0, (pageNum - 1) * pageSize);
            SearchResponse<Map> resp = client.search(s -> s
                            .index(indexName)
                            .from(from)
                            .size(pageSize)
                            .query(q -> q.bool(b -> buildBool(b, keyword, types, tenantId, admin, layer, domain))),
                    Map.class);
            SearchResult out = new SearchResult();
            out.engine = name();
            out.total = resp.hits().total() == null ? 0 : resp.hits().total().value();
            for (Hit<Map> hit : resp.hits().hits()) {
                out.records.add(fromMap(hit.id(), hit.source()));
            }
            out.costMs = System.currentTimeMillis() - t0;
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("ES search failed: " + e.getMessage(), e);
        }
    }

    private BoolQuery.Builder buildBool(BoolQuery.Builder b, String keyword, Set<String> types,
                                        Long tenantId, boolean admin, String layer, String domain) {
        if (!admin && tenantId != null) {
            b.filter(f -> f.term(t -> t.field("tenantId").value(tenantId)));
        }
        if (types != null && !types.isEmpty()) {
            b.filter(f -> f.terms(t -> t.field("type").terms(v -> v.value(
                    types.stream().map(FieldValue::of).toList()))));
        }
        if (layer != null && !layer.isBlank()) {
            b.filter(f -> f.term(t -> t.field("layer").value(layer)));
        }
        if (domain != null && !domain.isBlank()) {
            b.filter(f -> f.term(t -> t.field("domain").value(domain)));
        }
        if (keyword != null && !keyword.isBlank()) {
            b.must(m -> m.multiMatch(mm -> mm.query(keyword)
                    .fields("title^3", "subtitle^2", "content", "searchableText")));
        } else {
            b.must(m -> m.matchAll(ma -> ma));
        }
        return b;
    }

    @SuppressWarnings("unchecked")
    private SearchDoc fromMap(String id, Map src) {
        SearchDoc d = new SearchDoc();
        d.setId(id);
        if (src == null) return d;
        d.setType(str(src.get("type")));
        Object tid = src.get("tenantId");
        if (tid instanceof Number n) d.setTenantId(n.longValue());
        d.setTitle(str(src.get("title")));
        d.setSubtitle(str(src.get("subtitle")));
        d.setContent(str(src.get("content")));
        d.setLayer(str(src.get("layer")));
        d.setDomain(str(src.get("domain")));
        Object sh = src.get("scoreHint");
        if (sh instanceof Number n) d.setScoreHint(n.doubleValue());
        Object payload = src.get("payload");
        if (payload instanceof Map<?, ?> m) {
            d.setPayload((Map<String, Object>) m);
        }
        return d;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
