package io.github.http.log.snap.server.spring;

import io.github.http.log.snap.HttpRequestLogger;

import javax.annotation.Nullable;

/**
 * HttpRequestLogger 线程本地持有器
 * 用于在 Filter 和 Interceptor 之间共享 HttpRequestLogger 实例
 *
 * @author http-logging
 */
public class HttpRequestLoggerHolder {

    private static final ThreadLocal<HttpRequestLogger> LOGGER_HOLDER = new ThreadLocal<>();

    /**
     * 获取当前线程的 HttpRequestLogger
     *
     * @return 当前线程的 HttpRequestLogger，如果没有则返回 null
     */
    @Nullable
    public static HttpRequestLogger get() {
        return LOGGER_HOLDER.get();
    }

    /**
     * 设置当前线程的 HttpRequestLogger
     *
     * @param logger HttpRequestLogger 实例
     */
    public static void set(HttpRequestLogger logger) {
        LOGGER_HOLDER.set(logger);
    }

    /**
     * 清除当前线程的 HttpRequestLogger
     */
    public static void clear() {
        LOGGER_HOLDER.remove();
    }

    /**
     * 获取当前线程的 HttpRequestLogger，如果没有则创建一个新的（服务端模式）
     *
     * @return 当前线程的 HttpRequestLogger
     */
    public static HttpRequestLogger getOrCreate() {
        HttpRequestLogger logger = LOGGER_HOLDER.get();
        if (logger == null) {
            logger = HttpRequestLogger.forServer();
            LOGGER_HOLDER.set(logger);
        }
        return logger;
    }

    /**
     * 在当前线程执行操作，完成后自动清理
     *
     * @param logger   要使用的 HttpRequestLogger
     * @param runnable 要执行的操作
     */
    public static void runWith(HttpRequestLogger logger, Runnable runnable) {
        HttpRequestLogger previous = LOGGER_HOLDER.get();
        try {
            LOGGER_HOLDER.set(logger);
            runnable.run();
        } finally {
            if (previous != null) {
                LOGGER_HOLDER.set(previous);
            } else {
                LOGGER_HOLDER.remove();
            }
        }
    }
}

