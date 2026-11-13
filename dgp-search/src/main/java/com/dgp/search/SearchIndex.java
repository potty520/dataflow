package com.dgp.search;

import java.util.List;
import java.util.Set;

public interface SearchIndex {
    String name();
    void replaceAll(List<SearchDoc> docs);
    void upsert(SearchDoc doc);
    void delete(String id);
    SearchResult search(String keyword, Set<String> types, Long tenantId, boolean admin,
                        String layer, String domain, int pageNum, int pageSize);
}
