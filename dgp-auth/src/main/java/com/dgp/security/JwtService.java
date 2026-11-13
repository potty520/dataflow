package com.dgp.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 签发与校验（RS256）。
 * 公钥通过 JWKS 暴露给网关验签。
 */
public class JwtService {

    public static final String TYP_ACCESS = "access";
    public static final String TYP_REFRESH = "refresh";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;
    private final long tokenMinutes;
    private final long refreshDays;

    public JwtService(PrivateKey privateKey, PublicKey publicKey, String keyId,
                      long tokenMinutes, long refreshDays) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.keyId = keyId == null || keyId.isBlank() ? "dgp-1" : keyId;
        this.tokenMinutes = Math.max(1, tokenMinutes);
        this.refreshDays = Math.max(1, refreshDays);
    }

    public static JwtService fromPemOrGenerate(String privatePem, String publicPem, String keyId,
                                               long tokenMinutes, long refreshDays) {
        try {
            if (privatePem != null && !privatePem.isBlank() && publicPem != null && !publicPem.isBlank()) {
                PrivateKey priv = readPrivateKey(privatePem);
                PublicKey pub = readPublicKey(publicPem);
                return new JwtService(priv, pub, keyId, tokenMinutes, refreshDays);
            }
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            return new JwtService(kp.getPrivate(), kp.getPublic(), keyId, tokenMinutes, refreshDays);
        } catch (Exception e) {
            throw new IllegalStateException("初始化 RS256 密钥失败", e);
        }
    }

    public String getKeyId() {
        return keyId;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    private String create(Long userId, String username, String roleCode, Long tenantId, long minutes,
                          String typ, String sid, String perms, boolean mustChangePassword) {
        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", keyId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", username);
        payload.put("uid", userId);
        payload.put("role", roleCode);
        payload.put("tid", tenantId);
        payload.put("typ", typ);
        if (sid != null && !sid.isBlank()) payload.put("sid", sid);
        if (perms != null && !perms.isBlank() && TYP_ACCESS.equals(typ)) {
            payload.put("perms", perms);
        }
        if (TYP_ACCESS.equals(typ) && mustChangePassword) {
            payload.put("mcp", 1);
        }
        payload.put("iat", now);
        payload.put("exp", now + minutes * 60);
        payload.put("jti", java.util.UUID.randomUUID().toString().replace("-", ""));

        String h = b64(json(header));
        String p = b64(json(payload));
        String sig = sign(h + "." + p);
        return h + "." + p + "." + sig;
    }

    public String accessToken(Long userId, String username, String roleCode, Long tenantId) {
        return accessToken(userId, username, roleCode, tenantId, null, null, false);
    }

    public String accessToken(Long userId, String username, String roleCode, Long tenantId, String sid) {
        return accessToken(userId, username, roleCode, tenantId, sid, null, false);
    }

    public String accessToken(Long userId, String username, String roleCode, Long tenantId, String sid, String perms) {
        return accessToken(userId, username, roleCode, tenantId, sid, perms, false);
    }

    public String accessToken(Long userId, String username, String roleCode, Long tenantId, String sid, String perms,
                              boolean mustChangePassword) {
        return create(userId, username, roleCode, tenantId, tokenMinutes, TYP_ACCESS, sid, perms, mustChangePassword);
    }

    public String refreshToken(Long userId, String username, String roleCode, Long tenantId) {
        return refreshToken(userId, username, roleCode, tenantId, null);
    }

    public String refreshToken(Long userId, String username, String roleCode, Long tenantId, String sid) {
        return create(userId, username, roleCode, tenantId, refreshDays * 24L * 60L, TYP_REFRESH, sid, null, false);
    }

    public long getTokenMinutes() {
        return tokenMinutes;
    }

    public long getRefreshDays() {
        return refreshDays;
    }

    public Map<String, Object> verify(String token, String expectedTyp) {
        Map<String, Object> payload = verifySignature(token);
        if (payload == null) return null;
        if (expectedTyp != null && !expectedTyp.equals(String.valueOf(payload.get("typ")))) return null;
        long exp = ((Number) payload.getOrDefault("exp", 0)).longValue();
        if (exp < System.currentTimeMillis() / 1000) return null;
        return payload;
    }

    public Map<String, Object> verifySignature(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        if (!verifySig(parts[0] + "." + parts[1], parts[2])) return null;
        try {
            String body = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return Json.parse(body);
        } catch (Exception e) {
            return null;
        }
    }

    /** JWKS 单 key 导出。 */
    public Map<String, Object> jwks() {
        RSAPublicKey rsa = (RSAPublicKey) publicKey;
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyId);
        jwk.put("n", Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsigned(rsa.getModulus().toByteArray())));
        jwk.put("e", Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsigned(rsa.getPublicExponent().toByteArray())));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("keys", java.util.List.of(jwk));
        return out;
    }

    private static byte[] toUnsigned(byte[] twoComplement) {
        if (twoComplement.length > 1 && twoComplement[0] == 0) {
            byte[] out = new byte[twoComplement.length - 1];
            System.arraycopy(twoComplement, 1, out, 0, out.length);
            return out;
        }
        return twoComplement;
    }

    private String sign(String data) {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(privateKey);
            s.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }

    private boolean verifySig(String data, String sigB64) {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(publicKey);
            s.update(data.getBytes(StandardCharsets.UTF_8));
            return s.verify(Base64.getUrlDecoder().decode(sigB64));
        } catch (Exception e) {
            return false;
        }
    }

    private static PrivateKey readPrivateKey(String pem) throws Exception {
        String normalized = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static PublicKey readPublicKey(String pem) throws Exception {
        String normalized = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String json(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v instanceof Number) {
                sb.append(v);
            } else {
                sb.append("\"").append(escape(String.valueOf(v))).append("\"");
            }
        }
        return sb.append("}").toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class Json {
        static Map<String, Object> parse(String s) {
            Map<String, Object> out = new LinkedHashMap<>();
            String body = s.trim();
            if (body.startsWith("{")) body = body.substring(1);
            if (body.endsWith("}")) body = body.substring(0, body.length() - 1);
            for (String kv : body.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
                String[] p = kv.split(":", 2);
                if (p.length < 2) continue;
                String k = unescape(p[0].trim().replace("\"", ""));
                String v = p[1].trim();
                if (v.startsWith("\"")) {
                    out.put(k, unescape(v.substring(1, v.length() - 1)));
                } else {
                    try {
                        out.put(k, Long.parseLong(v));
                    } catch (NumberFormatException e) {
                        out.put(k, v);
                    }
                }
            }
            return out;
        }

        private static String unescape(String s) {
            return s.replace("\\\"", "\"").replace("\\\\", "\\");
        }
    }
}
