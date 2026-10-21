package com.dgp.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/** 全局异常处理，统一输出 00 基线 §6.2 响应体；对外不暴露数据库/框架内部细节。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Object>> handleBiz(BizException e, HttpServletRequest req) {
        log.warn("业务异常 {} {} -> {} {}", req.getMethod(), req.getRequestURI(), e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getHttpStatus()).body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Object>> handleValid(Exception e) {
        String msg;
        if (e instanceof MethodArgumentNotValidException manv) {
            msg = manv.getBindingResult().getFieldErrors().stream()
                    .map(f -> f.getField() + " " + f.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        } else {
            BindException be = (BindException) e;
            msg = be.getBindingResult().getFieldErrors().stream()
                    .map(f -> f.getField() + " " + f.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.PARAM_INVALID.getCode(), msg));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Object>> handleParam(Exception e) {
        log.warn("参数异常: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(Result.fail(ErrorCode.PARAM_INVALID.getCode(), "请求参数不合法"));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Result<Object>> handleNoResource(Exception e) {
        return ResponseEntity.status(404).body(Result.fail(ErrorCode.NOT_FOUND.getCode(), "资源不存在"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Object>> handle404(NoHandlerFoundException e) {
        return ResponseEntity.status(404).body(Result.fail(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Object>> handleMediaType(org.springframework.web.HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(415)
                .body(Result.fail(ErrorCode.PARAM_INVALID.getCode(), "不支持的 Content-Type，JSON 接口请使用 application/json"));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Object>> handleMethod(org.springframework.web.HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405)
                .body(Result.fail(ErrorCode.METHOD_NOT_ALLOWED.getCode(), "请求方法不支持：" + e.getMethod()));
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<Result<Object>> handleDuplicate(org.springframework.dao.DuplicateKeyException e, HttpServletRequest req) {
        log.warn("唯一约束冲突 {} {} : {}", req.getMethod(), req.getRequestURI(), e.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest()
                .body(Result.fail(ErrorCode.PARAM_INVALID.getCode(), "数据已存在或违反唯一约束，请检查编码/名称是否重复"));
    }

    /**
     * 数据访问异常：完整堆栈进日志（含 traceId），对外只回通用提示，
     * 避免把表结构、SQL 语句、字段名等内部信息泄露给调用方。
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Object>> handleDb(DataAccessException e, HttpServletRequest req) {
        log.error("数据访问异常 traceId={} {} {}", TraceIdFilter.current(), req.getMethod(), req.getRequestURI(), e);
        return ResponseEntity.status(500)
                .body(Result.fail(50000, "数据访问异常，请联系管理员并提供 traceId：" + TraceIdFilter.current()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleOther(Exception e, HttpServletRequest req) {
        log.error("未捕获异常 traceId={} {} {}", TraceIdFilter.current(), req.getMethod(), req.getRequestURI(), e);
        return ResponseEntity.status(500)
                .body(Result.fail(ErrorCode.INTERNAL_ERROR.getCode(),
                        "系统内部错误，请联系管理员并提供 traceId：" + TraceIdFilter.current()));
    }
}
