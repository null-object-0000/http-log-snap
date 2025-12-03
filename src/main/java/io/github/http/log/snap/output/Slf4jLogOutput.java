package io.github.http.log.snap.output;

import io.github.http.log.snap.HttpLogData;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SLF4J 日志输出实现
 * 将 HTTP 日志输出到 SLF4J 日志框架
 *
 * @author http-logging
 */
@Slf4j
public class Slf4jLogOutput implements HttpLogOutput {

    private final Logger logger;
    private final LogLevel level;

    /**
     * 日志级别
     */
    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    /**
     * 使用默认日志记录器和 INFO 级别
     */
    public Slf4jLogOutput() {
        this(log, LogLevel.INFO);
    }

    /**
     * 使用指定的日志级别
     */
    public Slf4jLogOutput(LogLevel level) {
        this(log, level);
    }

    /**
     * 使用指定的日志记录器名称
     */
    public Slf4jLogOutput(String loggerName) {
        this(LoggerFactory.getLogger(loggerName), LogLevel.INFO);
    }

    /**
     * 使用指定的日志记录器名称和级别
     */
    public Slf4jLogOutput(String loggerName, LogLevel level) {
        this(LoggerFactory.getLogger(loggerName), level);
    }

    /**
     * 使用指定的日志记录器和级别
     */
    public Slf4jLogOutput(Logger logger, LogLevel level) {
        this.logger = logger;
        this.level = level;
    }

    @Override
    public void output(@NonNull HttpLogData data, @NonNull String formattedLog) {
        switch (level) {
            case TRACE -> logger.trace(formattedLog);
            case DEBUG -> logger.debug(formattedLog);
            case INFO -> logger.info(formattedLog);
            case WARN -> logger.warn(formattedLog);
            case ERROR -> logger.error(formattedLog);
        }
    }

    @Override
    public void outputError(@NonNull HttpLogData data, @NonNull String formattedLog, @NonNull Throwable error) {
        switch (level) {
            case TRACE -> logger.trace(formattedLog, error);
            case DEBUG -> logger.debug(formattedLog, error);
            case INFO -> logger.info(formattedLog, error);
            case WARN -> logger.warn(formattedLog, error);
            case ERROR -> logger.error(formattedLog, error);
        }
    }

    @Override
    public String getName() {
        return "SLF4J[" + logger.getName() + "]";
    }

    @Override
    public boolean isEnabled() {
        return switch (level) {
            case TRACE -> logger.isTraceEnabled();
            case DEBUG -> logger.isDebugEnabled();
            case INFO -> logger.isInfoEnabled();
            case WARN -> logger.isWarnEnabled();
            case ERROR -> logger.isErrorEnabled();
        };
    }
}

