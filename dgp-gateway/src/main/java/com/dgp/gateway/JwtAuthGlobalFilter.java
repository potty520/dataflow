package com.dgp.gateway;

import com.dgp.common.GatewayHeaders;
import com.dgp.common.GatewayHmac;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网关全局鉴权：RS256 验签 → 写内部身份头（HMAC）→ Redis 租户限流。
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/jwks",
            "/api/v1/auth/.well-known/jwks.json",
            "/api/v1/auth/cas/",
            "/api/v1/admin/ping",
            "/actuator"
    );

    /** 强制改密期间仍允许访问的路径。 */
    private static final Set<String> MCP_ALLOW = Set.of(
            "/api/v1/auth/password",
            "/api/v1/auth/logout",
            "/api/v1/auth/me",
            "/api/v1/auth/menus"
    );

    private final String gatewaySecret;
    private final int tenantLimitPerMinute;
    private final String jwksUrl;
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder().build();
    private final AtomicReference<PublicKey> cachedKey = new AtomicReference<>();
    private volatile long keyLoadedAt;

    public JwtAuthGlobalFilter(
            @Value("${dgp.gateway-hmac-secret:dev-gateway-hmac-change-me}") String gatewaySecret,
            @Value("${dgp.tenant-rate-limit-per-minute:600}") int tenantLimitPerMinute,
            @Value("${dgp.jwks-url:http://127.0.0.1:8081/api/v1/auth/jwks}") String jwksUrl,
            ReactiveStringRedisTemplate redis) {
        this.gatewaySecret = gatewaySecret;
        this.tenantLimitPerMinute = tenantLimitPerMinute;
        this.jwksUrl = jwksUrl;
        this.redis = redis;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String traceId = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = java.util.UUID.randomUUID().toString().replace("-", "");
        }
        final String tidHeader = traceId;

        if (isPublic(path) || path.startsWith("/open/")) {
            ServerHttpRequest req = exchange.getRequest().mutate()
                    .header(GatewayHeaders.TRACE_ID, tidHeader)
                    .header(GatewayHeaders.INTERNAL_SKIP_AUTH, path.startsWith("/open/") || isPublic(path) ? "1" : "0")
                    .build();
            return chain.filter(exchange.mutate().request(req).build());
        }

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "缺少 Bearer Token");
        }
        String token = auth.substring(7);
        return verifyAndEnrich(exchange, chain, token, tidHeader);
    }

    private Mono<Void> verifyAndEnrich(ServerWebExchange exchange, GatewayFilterChain chain,
                                       String token, String traceId) {
        return loadPublicKey().flatMap(pub -> {
            Map<String, Object> payload = verifyRs256(token, pub);
            if (payload == null) {
                return unauthorized(exchange, "令牌无效或已过期");
            }
            if (!"access".equals(String.valueOf(payload.get("typ")))) {
                return unauthorized(exchange, "令牌类型错误");
            }
            String jti = String.valueOf(payload.get("jti"));
            return redis.hasKey("dgp:auth:jti:" + jti)
                    .defaultIfEmpty(false)
                    .onErrorReturn(false)
                    .flatMap(revoked -> {
                if (Boolean.TRUE.equals(revoked)) {
                    return unauthorized(exchange, "令牌已吊销");
                }
                String username = String.valueOf(payload.get("sub"));
                String userId = String.valueOf(payload.get("uid"));
                String role = String.valueOf(payload.get("role"));
                String tenantId = String.valueOf(payload.get("tid"));
                String perms = payload.get("perms") == null ? "" : String.valueOf(payload.get("perms"));
                String sid = payload.get("sid") == null ? "" : String.valueOf(payload.get("sid"));
                boolean mcp = "1".equals(String.valueOf(payload.getOrDefault("mcp", "0")));
                String path = exchange.getRequest().getURI().getPath();
                if (mcp && !isMcpAllowed(path)) {
                    return forbidden(exchange, "须先修改口令后再访问系统");
                }
                return rateLimit(tenantId).flatMap(ok -> {
                    if (!ok) {
                        return tooMany(exchange);
                    }
                    return dailyQuota(tenantId).flatMap(dailyOk -> {
                        if (!dailyOk) {
                            return tooMany(exchange);
                        }
                        String ts = String.valueOf(System.currentTimeMillis() / 1000);
                        String sign = GatewayHmac.sign(gatewaySecret,
                                GatewayHeaders.signPayload(userId, username, role, tenantId, ts));
                        ServerHttpRequest req = exchange.getRequest().mutate()
                                .header(GatewayHeaders.USER_ID, userId)
                                .header(GatewayHeaders.USERNAME, username)
                                .header(GatewayHeaders.ROLE, role)
                                .header(GatewayHeaders.TENANT_ID, tenantId)
                                .header(GatewayHeaders.PERMS, perms)
                                .header(GatewayHeaders.SESSION_ID, sid)
                                .header(GatewayHeaders.TRACE_ID, traceId)
                                .header(GatewayHeaders.GATEWAY_TS, ts)
                                .header(GatewayHeaders.GATEWAY_SIGN, sign)
                                .headers(h -> h.remove(HttpHeaders.AUTHORIZATION))
                                .build();
                        return chain.filter(exchange.mutate().request(req).build());
                    });
                });
            });
        });
    }

    private boolean isMcpAllowed(String path) {
        for (String p : MCP_ALLOW) {
            if (path.equals(p) || path.startsWith(p + "/")) return true;
        }
        return false;
    }

    private Mono<Boolean> rateLimit(String tenantId) {
        String rpmKey = "dgp:quota:tenant:" + tenantId + ":rpm";
        return redis.opsForValue().get(rpmKey)
                .map(v -> {
                    try {
                        return Integer.parseInt(v);
                    } catch (Exception e) {
                        return tenantLimitPerMinute;
                    }
                })
                .defaultIfEmpty(tenantLimitPerMinute)
                .onErrorReturn(tenantLimitPerMinute)
                .flatMap(limit -> {
                    String key = "dgp:rl:tenant:" + tenantId + ":" + (System.currentTimeMillis() / 60000);
                    return redis.opsForValue().increment(key)
                            .flatMap(n -> redis.expire(key, Duration.ofMinutes(2)).thenReturn(n))
                            .map(n -> n <= limit)
                            .onErrorReturn(true);
                });
    }

    private Mono<Boolean> dailyQuota(String tenantId) {
        String dailyKey = "dgp:quota:tenant:" + tenantId + ":daily";
        return redis.opsForValue().get(dailyKey)
                .map(v -> {
                    try {
                        return Integer.parseInt(v);
                    } catch (Exception e) {
                        return 100_000;
                    }
                })
                .defaultIfEmpty(100_000)
                .onErrorReturn(100_000)
                .flatMap(limit -> {
                    String key = "dgp:rl:tenant:" + tenantId + ":day:" + java.time.LocalDate.now();
                    return redis.opsForValue().increment(key)
                            .flatMap(n -> redis.expire(key, Duration.ofDays(2)).thenReturn(n))
                            .map(n -> n <= limit)
                            .onErrorReturn(true);
                });
    }

    private Mono<PublicKey> loadPublicKey() {
        PublicKey cached = cachedKey.get();
        if (cached != null && System.currentTimeMillis() - keyLoadedAt < 60_000) {
            return Mono.just(cached);
        }
        return webClient.get().uri(jwksUrl).retrieve().bodyToMono(String.class)
                .map(this::parseJwks)
                .doOnNext(k -> {
                    cachedKey.set(k);
                    keyLoadedAt = System.currentTimeMillis();
                })
                .onErrorResume(e -> cached != null ? Mono.just(cached) : Mono.error(e));
    }

    private PublicKey parseJwks(String body) {
        try {
            JsonNode keys = mapper.readTree(body).path("keys");
            if (!keys.isArray() || keys.isEmpty()) throw new IllegalStateException("empty jwks");
            JsonNode jwk = keys.get(0);
            byte[] n = Base64.getUrlDecoder().decode(jwk.get("n").asText());
            byte[] e = Base64.getUrlDecoder().decode(jwk.get("e").asText());
            RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, n), new BigInteger(1, e));
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new IllegalStateException("JWKS parse failed", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> verifyRs256(String token, PublicKey pub) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(pub);
            s.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
            if (!s.verify(Base64.getUrlDecoder().decode(parts[2]))) return null;
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> payload = mapper.readValue(json, Map.class);
            long exp = ((Number) payload.getOrDefault("exp", 0)).longValue();
            if (exp < System.currentTimeMillis() / 1000) return null;
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isPublic(String path) {
        for (String p : PUBLIC_PREFIXES) {
            if (path.equals(p) || path.startsWith(p)) return true;
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"code\":401,\"message\":\"" + msg + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buf = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buf));
    }

    private Mono<Void> tooMany(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = "{\"code\":429,\"message\":\"租户请求过于频繁或已达日配额\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buf = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buf));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"code\":403,\"message\":\"" + msg + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buf = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buf));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
