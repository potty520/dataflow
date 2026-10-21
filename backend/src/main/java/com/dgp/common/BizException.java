package com.dgp.common;

/** 业务异常，携带错误码与 HTTP 语义状态。 */
public class BizException extends RuntimeException {
    private final int code;
    private final int httpStatus;

    public BizException(ErrorCode ec) {
        super(ec.getMessage());
        this.code = ec.getCode();
        this.httpStatus = httpStatusOf(ec.getCode());
    }

    public BizException(ErrorCode ec, String message) {
        super(message);
        this.code = ec.getCode();
        this.httpStatus = httpStatusOf(ec.getCode());
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatusOf(code);
    }

    private static int httpStatusOf(int code) {
        if (code == 10401) return 401;
        if (code == 10403) return 403;
        if (code == 10404 || code == 20202 || code == 26101) return 404;
        if (code == 10429 || code == 26102) return 429;
        if (code >= 10000 && code < 11000) return 400;
        if (code >= 50000) return 500;
        return 400;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
