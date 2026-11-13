package com.dgp.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** 网关内部身份头 HMAC-SHA256 签名工具。 */
public final class GatewayHmac {

    private GatewayHmac() {
    }

    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC sign failed", e);
        }
    }

    public static boolean verify(String secret, String payload, String signature) {
        if (secret == null || signature == null) return false;
        String expect = sign(secret, payload);
        if (expect.length() != signature.length()) return false;
        int r = 0;
        for (int i = 0; i < expect.length(); i++) {
            r |= expect.charAt(i) ^ signature.charAt(i);
        }
        return r == 0;
    }
}
