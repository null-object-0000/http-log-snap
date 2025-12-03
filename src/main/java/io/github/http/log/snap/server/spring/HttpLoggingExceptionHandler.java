package io.github.http.log.snap.server.spring;

import io.github.http.log.snap.HttpRequestLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * HTTP 日志异常处理器
 * <p>
 * 方式1：自动拦截（可能被业务的 GlobalExceptionHandler 优先处理）
 * <p>
 * 方式2：在业务的 GlobalExceptionHandler 中手动调用 {@link #record(Throwable)}
 * <pre>{@code
 * @RestControllerAdvice
 * public class GlobalExceptionHandler {
 *     @ExceptionHandler(Exception.class)
 *     public BaseResponse<?> errorHandler(Exception e) {
 *         // 记录异常到 HTTP 日志
 *         HttpLoggingExceptionHandler.record(e);
 *         
 *         // ... 原有逻辑
 *         return BaseResponse.error();
 *     }
 * }
 * }</pre>
 */
@Slf4j
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpLoggingExceptionHandler {

    @ExceptionHandler(Throwable.class)
    public void handleException(Throwable ex) throws Throwable {
        record(ex);
        throw ex;
    }

    /**
     * 记录异常到 HTTP 日志
     * <p>
     * 在业务的 GlobalExceptionHandler 中调用此方法，确保异常被记录到 HTTP 日志中
     *
     * @param ex 异常
     */
    public static void record(Throwable ex) {
        if (ex == null) {
            return;
        }
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger != null && logger.getException() == null) {
            logger.recordHandlerException(ex);
        }
    }
}

