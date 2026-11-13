package com.dgp.common;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** 口令散列与常量时间比较（无外部依赖，供 auth/core 共用）。 */
public final class PasswordUtil {

    private static final int ITERATIONS = 210_000;
    private static final int MIN_PASSWORD_LEN = 8;

    private PasswordUtil() {
    }

    public static String hash(String raw) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] dk = pbkdf2(raw.toCharArray(), salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(dk);
    }

    public static boolean matches(String raw, String stored) {
        try {
            String[] p = stored.split("\\$");
            if (p.length != 4) return false;
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expect = Base64.getDecoder().decode(p[3]);
            byte[] actual = pbkdf2(raw.toCharArray(), salt, Integer.parseInt(p[1]));
            return MessageDigest.isEqual(expect, actual);
        } catch (Exception e) {
            return false;
        }
    }

    public static void assertPasswordPolicy(String pwd) {
        if (pwd == null || pwd.length() < MIN_PASSWORD_LEN) {
            throw new BizException(ErrorCode.PARAM_INVALID, "口令长度至少 " + MIN_PASSWORD_LEN + " 位");
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < pwd.length(); i++) {
            char c = pwd.charAt(i);
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        if (!letter || !digit) {
            throw new BizException(ErrorCode.PARAM_INVALID, "口令须同时包含字母与数字");
        }
    }

    /** 常量时间字符串比较，避免时序侧信道。 */
    public static boolean constantEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) {
            MessageDigest.isEqual(x, x);
            return false;
        }
        return MessageDigest.isEqual(x, y);
    }

    private static byte[] pbkdf2(char[] raw, byte[] salt, int iter) {
        try {
            PBEKeySpec spec = new PBEKeySpec(raw, salt, iter, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
