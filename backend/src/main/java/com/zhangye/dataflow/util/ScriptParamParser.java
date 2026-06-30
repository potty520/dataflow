package com.zhangye.dataflow.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脚本参数解析器 - 支持${variable_name}参数化 (F-DEV-007)
 */
public class ScriptParamParser {

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /**
     * Extract all parameter names from script content
     */
    public static Set<String> extractParams(String content) {
        Set<String> params = new LinkedHashSet<>();
        if (content == null || content.isEmpty()) {
            return params;
        }
        Matcher matcher = PARAM_PATTERN.matcher(content);
        while (matcher.find()) {
            params.add(matcher.group(1).trim());
        }
        return params;
    }

    /**
     * Replace ${variable_name} placeholders with actual values
     */
    public static String resolveParams(String content, Map<String, String> params) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        if (params == null || params.isEmpty()) {
            return content;
        }
        StringBuilder resolved = new StringBuilder();
        Matcher matcher = PARAM_PATTERN.matcher(content);
        while (matcher.find()) {
            String paramName = matcher.group(1).trim();
            String value = params.getOrDefault(paramName, matcher.group(0));
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    /**
     * Check if content contains any parameter placeholders
     */
    public static boolean hasParams(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        return PARAM_PATTERN.matcher(content).find();
    }
}
