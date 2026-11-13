package com.dgp.search;

import com.dgp.common.M;
import com.dgp.common.Req;
import com.dgp.common.Result;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping({"", "/"})
    @RequirePerm("metadata:search")
    public Result<Object> search(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String types,
                                 @RequestParam(required = false) String layer,
                                 @RequestParam(required = false) String domain,
                                 @RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(searchService.search(keyword, types, layer, domain, pageNum, pageSize));
    }

    @GetMapping("/status")
    @RequirePerm("metadata:search")
    public Result<Object> status() {
        return Result.ok(searchService.status());
    }

    @PostMapping("/reindex")
    @RequirePerm("sys:config:edit")
    public Result<Object> reindex() {
        int n = searchService.reindex();
        return Result.ok(M.of("indexed", n, "engine", searchService.status().get("engine")));
    }
}
