package com.dgp.common;

/** 错误码，分段严格对齐 00 基线 §6.3 与 01 文档附录 A。 */
public enum ErrorCode {

    OK(0, "ok"),

    // 10000-10999 通用
    PARAM_INVALID(10001, "参数校验失败"),
    UNAUTHORIZED(10401, "未认证或令牌已失效"),
    FORBIDDEN(10403, "无权限访问该资源"),
    NOT_FOUND(10404, "资源不存在"),
    RATE_LIMITED(10429, "请求超过限流阈值"),
    METHOD_NOT_ALLOWED(10405, "请求方法不支持"),

    // 20000-20999 元数据/血缘
    DATASOURCE_CONNECT_FAIL(20101, "数据源连接失败"),
    LINEAGE_PARSE_FAIL(20201, "血缘解析失败"),
    METADATA_NOT_FOUND(20202, "元数据对象不存在"),

    // 21000-21999 建模
    MODEL_REFERENCED(21101, "模型已被引用，禁止删除"),
    MODEL_STATE_ILLEGAL(21102, "模型状态不允许该操作"),

    // 22000-22999 标准
    MAPPING_CONFLICT(22101, "标准映射冲突"),

    // 23000-23999 集成/调度
    JOB_RUNNING(23101, "任务正在运行中"),
    SCHEDULER_UNAVAILABLE(23102, "调度引擎不可用"),
    SQL_INVALID(23103, "SQL 语法校验失败"),

    // 24000-24999 质量
    RULE_SYNTAX_ERROR(24101, "规则语法错误"),
    CHECK_TIMEOUT(24102, "稽核任务执行超时"),
    ISSUE_STATE_ILLEGAL(24103, "问题单状态迁移非法"),

    // 25000-25999 安全
    SENSITIVE_SCAN_FAIL(25101, "敏感数据识别失败"),
    MASK_POLICY_CONFLICT(25102, "脱敏策略冲突"),

    // 26000-26999 资产/服务
    API_OFFLINE(26101, "API 已下线"),
    QUOTA_EXCEEDED(26102, "调用配额超限"),
    API_NOT_PUBLISHED(26103, "API 未发布"),

    // 27000-27999 行业包/报告
    PACKAGE_VERSION_INCOMPATIBLE(27101, "行业包版本不兼容"),
    PACKAGE_MANIFEST_INVALID(27102, "行业包 manifest 校验失败"),

    // 50000+
    INTERNAL_ERROR(50000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
