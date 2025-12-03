package io.github.http.log.snap.formatter;

import io.github.http.log.snap.HttpLogData;
import lombok.Getter;
import lombok.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * HTTP 日志格式化器抽象基类
 * 提供通用的脱敏和配置功能，简化自定义格式化器的实现
 * <p>
 * 使用示例：
 * <pre>
 * public class MyFormatter extends AbstractHttpLogFormatter {
 *     &#64;Override
 *     protected String doFormat(HttpLogData data) {
 *         // 自定义格式化逻辑
 *         return "...";
 *     }
 *
 *     &#64;Override
 *     public String getFormatType() {
 *         return "MY_FORMAT";
 *     }
 * }
 * </pre>
 *
 * @author http-logging
 */
public abstract class AbstractHttpLogFormatter implements HttpLogFormatter {

    /**
     * 需要脱敏的请求头名称
     */
    @Getter
    protected Set<String> headersToRedact = new HashSet<>();

    /**
     * 需要脱敏的查询参数名称
     */
    @Getter
    protected Set<String> queryParamsToRedact = new HashSet<>();

    /**
     * 需要脱敏的请求体字段名称（用于 JSON 格式）
     */
    @Getter
    protected Set<String> bodyFieldsToRedact = new HashSet<>();

    /**
     * 脱敏占位符
     */
    @Getter
    protected String redactPlaceholder = "██";

    /**
     * 是否包含请求头
     */
    @Getter
    protected boolean includeHeaders = true;

    /**
     * 是否包含请求体
     */
    @Getter
    protected boolean includeRequestBody = true;

    /**
     * 是否包含响应体
     */
    @Getter
    protected boolean includeResponseBody = true;

    /**
     * 是否包含耗时指标
     */
    @Getter
    protected boolean includeTimingMetrics = true;

    /**
     * 最大请求体长度（超过则截断）
     */
    @Getter
    protected int maxRequestBodyLength = 10 * 1024;

    /**
     * 最大响应体长度（超过则截断）
     */
    @Getter
    protected int maxResponseBodyLength = 10 * 1024;

    @Override
    public final String format(@NonNull HttpLogData data) {
        return doFormat(data);
    }

    /**
     * 执行格式化（子类实现）
     *
     * @param data 日志数据
     * @return 格式化后的字符串
     */
    protected abstract String doFormat(@NonNull HttpLogData data);

    @Override
    public HttpLogFormatter redactHeaders(Set<String> headerNames) {
        if (headerNames != null) {
            this.headersToRedact = new HashSet<>(headerNames);
        }
        return this;
    }

    /**
     * 设置需要脱敏的查询参数
     */
    public AbstractHttpLogFormatter redactQueryParams(Set<String> paramNames) {
        if (paramNames != null) {
            this.queryParamsToRedact = new HashSet<>(paramNames);
        }
        return this;
    }

    /**
     * 设置需要脱敏的请求体字段
     */
    public AbstractHttpLogFormatter redactBodyFields(Set<String> fieldNames) {
        if (fieldNames != null) {
            this.bodyFieldsToRedact = new HashSet<>(fieldNames);
        }
        return this;
    }

    /**
     * 设置脱敏占位符
     */
    public AbstractHttpLogFormatter setRedactPlaceholder(String placeholder) {
        this.redactPlaceholder = placeholder;
        return this;
    }

    /**
     * 设置是否包含请求头
     */
    public AbstractHttpLogFormatter setIncludeHeaders(boolean include) {
        this.includeHeaders = include;
        return this;
    }

    /**
     * 设置是否包含请求体
     */
    public AbstractHttpLogFormatter setIncludeRequestBody(boolean include) {
        this.includeRequestBody = include;
        return this;
    }

    /**
     * 设置是否包含响应体
     */
    public AbstractHttpLogFormatter setIncludeResponseBody(boolean include) {
        this.includeResponseBody = include;
        return this;
    }

    /**
     * 设置是否包含耗时指标
     */
    public AbstractHttpLogFormatter setIncludeTimingMetrics(boolean include) {
        this.includeTimingMetrics = include;
        return this;
    }

    /**
     * 设置最大请求体长度
     */
    public AbstractHttpLogFormatter setMaxRequestBodyLength(int length) {
        this.maxRequestBodyLength = length;
        return this;
    }

    /**
     * 设置最大响应体长度
     */
    public AbstractHttpLogFormatter setMaxResponseBodyLength(int length) {
        this.maxResponseBodyLength = length;
        return this;
    }

    // ==================== 工具方法 ====================

    /**
     * 对请求头值进行脱敏
     */
    protected String redactHeaderValue(String name, String value) {
        if (headersToRedact.contains(name) || headersToRedact.contains(name.toLowerCase())) {
            return redactPlaceholder;
        }
        return value;
    }

    /**
     * 对 URL 中的查询参数进行脱敏
     *
     * @param url 原始 URL
     * @return 脱敏后的 URL
     */
    protected String redactUrl(String url) {
        if (url == null || queryParamsToRedact.isEmpty()) {
            return url;
        }

        int queryStart = url.indexOf('?');
        if (queryStart == -1) {
            return url;
        }

        String baseUrl = url.substring(0, queryStart);
        String queryString = url.substring(queryStart + 1);

        // 处理 fragment（#后面的部分）
        String fragment = "";
        int fragmentStart = queryString.indexOf('#');
        if (fragmentStart != -1) {
            fragment = queryString.substring(fragmentStart);
            queryString = queryString.substring(0, fragmentStart);
        }

        // 解析并脱敏查询参数
        StringBuilder redactedQuery = new StringBuilder();
        String[] pairs = queryString.split("&");
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                redactedQuery.append("&");
            }

            String pair = pairs[i];
            int eqIndex = pair.indexOf('=');
            if (eqIndex == -1) {
                redactedQuery.append(pair);
            } else {
                String paramName = pair.substring(0, eqIndex);
                String paramValue = pair.substring(eqIndex + 1);

                redactedQuery.append(paramName).append("=");
                if (shouldRedactQueryParam(paramName)) {
                    redactedQuery.append(redactPlaceholder);
                } else {
                    redactedQuery.append(paramValue);
                }
            }
        }

        return baseUrl + "?" + redactedQuery + fragment;
    }

    /**
     * 判断查询参数是否需要脱敏
     */
    protected boolean shouldRedactQueryParam(String paramName) {
        return queryParamsToRedact.contains(paramName) ||
                queryParamsToRedact.contains(paramName.toLowerCase());
    }

    /**
     * 截断字符串
     */
    protected String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "... [truncated " + (str.length() - maxLength) + " chars]";
    }

    /**
     * 判断字符串是否非空白
     */
    protected static boolean isNotBlank(String str) {
        return str != null && !str.isBlank();
    }

    /**
     * 判断字符串是否为空白
     */
    protected static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }
}

