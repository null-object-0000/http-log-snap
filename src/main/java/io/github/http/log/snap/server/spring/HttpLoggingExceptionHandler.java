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
 * 捕获 Controller 中抛出的异常并记录到 HttpRequestLogger 中。
 * 使用最高优先级确保在其他 ExceptionHandler 之前执行。
 * <p>
 * 注意：此处理器只记录异常，不处理异常（会重新抛出），
 * 实际的异常响应由应用的其他 ExceptionHandler 处理。
 */
@Slf4j
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpLoggingExceptionHandler {

    @ExceptionHandler(Exception.class)
    public void handleException(Exception ex) throws Exception {
        // 记录异常到 HttpRequestLogger
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger != null && logger.getException() == null) {
            logger.recordHandlerException(ex);
        }

        // 重新抛出，让其他 ExceptionHandler 处理
        throw ex;
    }
}

