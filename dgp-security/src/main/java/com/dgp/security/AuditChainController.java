package com.dgp.security;

import com.dgp.common.M;
import com.dgp.common.Req;
import com.dgp.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 独立安全服务：审计哈希链查询与校验（网关路由 /audit/chain、/audit/verify）。
 */
@RestController
@RequestMapping("/api/v1/security/audit")
public class AuditChainController {

    private final AuditService auditService;

    public AuditChainController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/chain")
    @RequirePerm("security:audit:view")
    public Result<Object> chain() {
        Map<String, Object> v = auditService.cachedVerify();
        return Result.ok(M.of(
                "intact", v.get("intact"),
                "brokenAt", v.get("brokenAt"),
                "checked", v.get("checked"),
                "lastHash", v.get("lastHash"),
                "algorithm", v.get("algorithm"),
                "verifyTime", v.get("verifyTime")));
    }

    @PostMapping({"/verify", "/verify-chain"})
    @RequirePerm("security:audit:view")
    public Result<Object> verify(@RequestBody(required = false) Map<String, Object> body) {
        int limit = body == null ? 5000 : Req.toInt(body.getOrDefault("limit", 5000), 5000);
        return Result.ok(auditService.verifyChain(limit));
    }
}
