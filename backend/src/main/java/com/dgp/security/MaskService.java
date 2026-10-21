package com.dgp.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 脱敏算法实现，对齐 01 文档 §8.7.4：
 * MASK 掩码 / REPLACE 替换 / TRUNCATE 截取 / ENCRYPT 加密 / HASH 散列 / GENERALIZE 泛化。
 *
 * <p>修正点：AES 密钥由 SHA-256 派生（原 17 字节密钥导致 ENCRYPT 恒失败）；IV 随机；
 * HASH 改为带密钥 HMAC（原裸 SHA-256 可被彩虹表反查）；TRUNCATE 对短值也做掩码。</p>
 */
@Service
public class MaskService {

    private static final byte[] KEY;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] HMAC_KEY;

    static {
        // 任意长度配置密钥 → SHA-256 派生固定 32 字节 AES 密钥
        KEY = sha256(env("DGP_MASK_KEY", "dgp-mask-key-2026"));
        HMAC_KEY = sha256(("hmac:" + Base64.getEncoder().encodeToString(KEY)).getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] env(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank() ? def : v).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String mask(String algo, Object value) {
        if (value == null) return null;
        if (algo == null || algo.isBlank()) return String.valueOf(value);
        String v = String.valueOf(value);
        return switch (algo.toUpperCase()) {
            case "MASK" -> maskKeep(v);
            case "REPLACE" -> v.isEmpty() ? v : "*".repeat(Math.min(v.length(), 8));
            case "TRUNCATE" -> truncate(v);
            case "HASH" -> hmac(v);
            case "ENCRYPT" -> encrypt(v);
            case "GENERALIZE" -> generalize(v);
            default -> v;
        };
    }

    /** 保留首尾，中间掩码：手机号 138****8000；证件号 6222**********1234。 */
    private String maskKeep(String v) {
        int len = v.length();
        if (len <= 2) return "*".repeat(len);
        if (len <= 6) return v.charAt(0) + "*".repeat(len - 2) + v.charAt(len - 1);
        int keepHead, keepTail;
        if (len >= 15) {
            keepHead = 4;
            keepTail = 4;
        } else {
            // 11 位手机号只保留 3+4，中间必须至少遮蔽 3 位
            keepHead = Math.min(3, len - 4);
            keepTail = Math.min(4, len - keepHead - 3);
        }
        int hide = Math.max(3, len - keepHead - keepTail);
        return v.substring(0, keepHead) + "*".repeat(hide) + v.substring(len - keepTail);
    }

    private String truncate(String v) {
        int keep = Math.min(4, Math.max(1, v.length() - 1));
        if (v.length() <= keep) return "*".repeat(v.length());
        return v.substring(0, keep) + "*".repeat(Math.max(3, v.length() - keep));
    }

    private String hmac(String v) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_KEY, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(v.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("HASH 脱敏失败", e);
        }
    }

    private String encrypt(String v) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(v.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return "enc:" + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("ENCRYPT 脱敏失败", e);
        }
    }

    /** 解密仅用于需要回传原文的受控场景（当前未开放给外部接口）。 */
    public String decrypt(String encrypted) {
        if (encrypted == null || !encrypted.startsWith("enc:")) return encrypted;
        try {
            byte[] all = Base64.getDecoder().decode(encrypted.substring(4));
            byte[] iv = Arrays.copyOfRange(all, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(Arrays.copyOfRange(all, 12, all.length)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败", e);
        }
    }

    private String generalize(String v) {
        try {
            double d = Double.parseDouble(v);
            if (d < 100) return "[0,100)";
            if (d < 1000) return "[100,1k)";
            if (d < 10000) return "[1k,10k)";
            if (d < 100000) return "[10k,100k)";
            return "[100k,+)";
        } catch (NumberFormatException e) {
            // 非数值：保留前 2 位（原实现前 4 位，对短文本几乎不脱敏）
            return v.length() > 2 ? v.substring(0, 2) + "***" : "***";
        }
    }
}
