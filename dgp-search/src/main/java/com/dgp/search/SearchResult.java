package com.dgp.search;

import java.util.ArrayList;
import java.util.List;

public class SearchResult {
    public long total;
    public List<SearchDoc> records = new ArrayList<>();
    public String engine;
    public long costMs;
}
