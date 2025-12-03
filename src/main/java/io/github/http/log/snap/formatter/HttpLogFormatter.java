package io.github.http.log.snap.formatter;

import io.github.http.log.snap.HttpLogData;
import lombok.NonNull;

import java.util.Set;

/**
 * HTTP 日志格式化器接口
 * 定义将 HTTP 日志数据转换为字符串的规范
 *
 * @author http-logging
 */
public interface HttpLogFormatter {

    /**
     * 格式化日志数据
     *
     * @param data 日志数据
     * @return 格式化后的字符串
     */
    String format(@NonNull HttpLogData data);

    /**
     * 设置需要脱敏的请求头名称
     *
     * @param headerNames 需要脱敏的请求头名称集合
     * @return 当前格式化器实例（支持链式调用）
     */
    HttpLogFormatter redactHeaders(Set<String> headerNames);

    /**
     * 获取格式化器类型名称
     *
     * @return 格式化器类型名称
     */
    String getFormatType();
}

