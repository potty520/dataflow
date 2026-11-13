package com.dgp.search;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** 无 ES 时的内存索引（演示/开发）；生产请启用 Elasticsearch。 */
@Component
public class LocalSearchIndex implements SearchIndex {

    private final ConcurrentHashMap<String, SearchDoc> docs = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "local-memory";
    }

    @Override
    public void replaceAll(List<SearchDoc> list) {
        docs.clear();
        for (SearchDoc d : list) {
            docs.put(d.getId(), d);
        }
    }

    @Override
    public void upsert(SearchDoc doc) {
        docs.put(doc.getId(), doc);
    }

    @Override
    public void delete(String id) {
        docs.remove(id);
    }

    @Override
    public SearchResult search(String keyword, Set<String> types, Long tenantId, boolean admin,
                               String layer, String domain, int pageNum, int pageSize) {
        long t0 = System.currentTimeMillis();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<SearchDoc> matched = docs.values().stream()
                .filter(d -> admin || tenantId == null || tenantId.equals(d.getTenantId()))
                .filter(d -> types == null || types.isEmpty() || types.contains(d.getType()))
                .filter(d -> layer == null || layer.isBlank() || layer.equalsIgnoreCase(nz(d.getLayer())))
                .filter(d -> domain == null || domain.isBlank() || domain.equalsIgnoreCase(nz(d.getDomain())))
                .filter(d -> kw.isEmpty() || d.searchableText().toLowerCase(Locale.ROOT).contains(kw))
                .sorted(Comparator.comparing((SearchDoc d) -> d.getScoreHint() == null ? 0d : d.getScoreHint()).reversed()
                        .thenComparing(d -> nz(d.getTitle())))
                .collect(Collectors.toList());
        SearchResult r = new SearchResult();
        r.total = matched.size();
        r.engine = name();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(matched.size(), from + pageSize);
        if (from < to) r.records = new ArrayList<>(matched.subList(from, to));
        r.costMs = System.currentTimeMillis() - t0;
        return r;
    }

    public int size() {
        return docs.size();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
