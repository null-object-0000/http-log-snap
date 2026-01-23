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
    private final LogLevel errorLevel;

    /**
     * 日志级别
     */
    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    /**
     * 使用默认日志记录器和 INFO 级别（异常时使用 ERROR 级别）
     */
    public Slf4jLogOutput() {
        this(log, LogLevel.INFO, LogLevel.ERROR);
    }

    /**
     * 使用指定的日志级别（异常时使用 ERROR 级别）
     */
    public Slf4jLogOutput(LogLevel level) {
        this(log, level, LogLevel.ERROR);
    }

    /**
     * 使用指定的正常日志级别和异常日志级别
     */
    public Slf4jLogOutput(LogLevel level, LogLevel errorLevel) {
        this(log, level, errorLevel);
    }

    /**
     * 使用指定的日志记录器名称（异常时使用 ERROR 级别）
     */
    public Slf4jLogOutput(String loggerName) {
        this(LoggerFactory.getLogger(loggerName), LogLevel.INFO, LogLevel.ERROR);
    }

    /**
     * 使用指定的日志记录器名称和级别（异常时使用 ERROR 级别）
     */
    public Slf4jLogOutput(String loggerName, LogLevel level) {
        this(LoggerFactory.getLogger(loggerName), level, LogLevel.ERROR);
    }

    /**
     * 使用指定的日志记录器名称、正常日志级别和异常日志级别
     */
    public Slf4jLogOutput(String loggerName, LogLevel level, LogLevel errorLevel) {
        this(LoggerFactory.getLogger(loggerName), level, errorLevel);
    }

    /**
     * 使用指定的日志记录器和级别（异常时使用 ERROR 级别）
     */
    public Slf4jLogOutput(Logger logger, LogLevel level) {
        this(logger, level, LogLevel.ERROR);
    }

    /**
     * 使用指定的日志记录器、正常日志级别和异常日志级别
     */
    public Slf4jLogOutput(Logger logger, LogLevel level, LogLevel errorLevel) {
        this.logger = logger;
        this.level = level;
        this.errorLevel = errorLevel;
    }

    @Override
    public void output(@NonNull HttpLogData data, @NonNull String formattedLog) {
        switch (level) {
            case TRACE:
                logger.trace(formattedLog);
                break;
            case DEBUG:
                logger.debug(formattedLog);
                break;
            case INFO:
                logger.info(formattedLog);
                break;
            case WARN:
                logger.warn(formattedLog);
                break;
            case ERROR:
                logger.error(formattedLog);
                break;
        }
    }

    @Override
    public void outputError(@NonNull HttpLogData data, @NonNull String formattedLog, @NonNull Throwable error) {
        switch (errorLevel) {
            case TRACE:
                logger.trace(formattedLog, error);
                break;
            case DEBUG:
                logger.debug(formattedLog, error);
                break;
            case INFO:
                logger.info(formattedLog, error);
                break;
            case WARN:
                logger.warn(formattedLog, error);
                break;
            case ERROR:
                logger.error(formattedLog, error);
                break;
        }
    }

    @Override
    public String getName() {
        return "SLF4J[" + logger.getName() + "]";
    }

    @Override
    public boolean isEnabled() {
        switch (level) {
            case TRACE:
                return logger.isTraceEnabled();
            case DEBUG:
                return logger.isDebugEnabled();
            case INFO:
                return logger.isInfoEnabled();
            case WARN:
                return logger.isWarnEnabled();
            case ERROR:
                return logger.isErrorEnabled();
            default:
                return false;
        }
    }
}

