package io.github.http.log.snap.formatter;

import com.alibaba.fastjson2.JSON;
import io.github.http.log.snap.*;
import lombok.NonNull;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 文本格式 HTTP 日志格式化器
 * 生成类似 OkHttp HttpLoggingInterceptor 风格的日志输出
 *
 * @author http-logging
 */
public class TextHttpLogFormatter implements HttpLogFormatter {

    /**
     * HTTP 100 Continue 状态码
     */
    private static final int HTTP_CONTINUE = 100;

    private Set<String> headersToRedact = new HashSet<>();
    private Set<String> queryParamsToRedact = new HashSet<>();
    private String redactPlaceholder = "██";

    @Override
    public String format(@NonNull HttpLogData data) {
        LogStringBuilder logs = new LogStringBuilder();

        if (data.isServer()) {
            // 服务端：跳过准备和连接阶段
            logs.append(buildServerStartLogs(data));
        } else {
            // 客户端：完整流程
            logs.append(buildPreparationLogs(data))
                    .append(buildConnectLogs(data));
        }

        logs.append(buildRequestLogs(data));

        // 处理失败情况
        if (data.hasFailed()) {
            String time = data.isServer()
                    ? getEventTime(data.getTiming(), HttpEvent.HANDLER_EXCEPTION, HttpEvent.RESPONSE_FAILED)
                    : getEventTime(data.getTiming(), HttpEvent.RESPONSE_FAILED, HttpEvent.CONNECT_FAILED);
            logs.appendLine("%s <-- HTTP FAILED", time);
            Throwable exception = data.getException();
            if (exception != null) {
                logs.appendLine("    %s: %s", exception.getClass().getSimpleName(), exception.getMessage());
            }
        } else if (data.getResponse() != null) {
            logs.append(buildResponseLogs(data));
        }

        // 扩展信息
        appendContextExtras(logs, data.getTiming(), data.getContext());

        return logs.toString();
    }

    @Override
    public HttpLogFormatter redactHeaders(Set<String> headerNames) {
        if (headerNames != null) {
            this.headersToRedact = new HashSet<>(headerNames);
        }
        return this;
    }

    /**
     * 设置需要脱敏的查询参数名称
     *
     * @param paramNames 需要脱敏的查询参数名称集合
     * @return 当前格式化器实例（支持链式调用）
     */
    public TextHttpLogFormatter redactQueryParams(Set<String> paramNames) {
        if (paramNames != null) {
            this.queryParamsToRedact = new HashSet<>(paramNames);
        }
        return this;
    }

    /**
     * 设置脱敏占位符
     *
     * @param placeholder 脱敏占位符
     * @return 当前格式化器实例（支持链式调用）
     */
    public TextHttpLogFormatter setRedactPlaceholder(String placeholder) {
        this.redactPlaceholder = placeholder;
        return this;
    }

    @Override
    public String getFormatType() {
        return "TEXT";
    }

    // ==================== 日志构建 - 服务端开始阶段 ====================

    private LogStringBuilder buildServerStartLogs(HttpLogData data) {
        LogStringBuilder logs = new LogStringBuilder();
        HttpTiming timing = data.getTiming();
        HttpLogContext context = data.getContext();

        // 开始标题
        logs.line().append(getEventTime(timing, HttpEvent.START)).space().append("--- START [SERVER]");

        // 接口名称
        if (context != null && isNotBlank(context.getInterfaceName())) {
            logs.append(" %s", context.getInterfaceName());
        }

        // Handler 信息
        if (data.getHandlerName() != null) {
            logs.append(" -> %s", data.getHandlerName());
        }

        // 总耗时
        logs.append(" (total: %dms)", data.getTotalTimeMs());

        // 客户端 IP（同一行）
        if (isNotBlank(data.getRemoteAddress())) {
            logs.append(" [client: %s]", data.getRemoteAddress());
        }

        return logs;
    }

    // ==================== 日志构建 - 客户端准备阶段 ====================

    private LogStringBuilder buildPreparationLogs(HttpLogData data) {
        LogStringBuilder logs = new LogStringBuilder();
        HttpTiming timing = data.getTiming();
        HttpLogData.Request request = data.getRequest();
        HttpLogContext context = data.getContext();

        // 开始标题
        logs.line().append(getEventTime(timing, HttpEvent.START)).space().append("--- START");

        // 代理信息
        if (request.getProxy() != null) {
            logs.append(" [%s]", request.getProxy());
        } else {
            logs.append(" [NONE]");
        }

        // 接口名称
        if (context != null && isNotBlank(context.getInterfaceName())) {
            logs.append(" %s", context.getInterfaceName());
        }

        // 总耗时
        logs.append(" (total: %dms)", data.getTotalTimeMs());

        // 构建阶段耗时
        appendBuildPhase(logs, timing, HttpRequestLogger.class, "--- BUILD URI", "buildURI");
        appendBuildPhase(logs, timing, HttpRequestLogger.class, "--- BUILD CONFIG", "buildConfig");
        appendBuildPhase(logs, timing, HttpRequestLogger.class, "--- BUILD CLIENT", "buildClient");
        appendBuildPhase(logs, timing, HttpRequestLogger.class, "--- BUILD REQUEST", "buildRequest");

        return logs;
    }

    private void appendBuildPhase(LogStringBuilder logs, HttpTiming timing, Class<?> clazz,
                                  String title, String eventPrefix) {
        long duration = timing.calculateTime(clazz, eventPrefix + "Start", eventPrefix + "End");
        if (duration > 0) {
            String time = getEventTime(timing, clazz, eventPrefix + "End");
            logs.line().append(time).space().append(title).append(" (%dms)", duration);
        }
    }

    /**
     * 输出上下文扩展信息（独立一行，位于最前面，JSON 格式）
     */
    private void appendContextExtras(LogStringBuilder logs, HttpTiming timing, HttpLogContext context) {
        if (context == null || context.getExtras() == null || context.getExtras().isEmpty()) {
            return;
        }
        String time = getEventTime(timing, HttpEvent.START);
        String json = JSON.toJSONString(context.getExtras());
        logs.append(time).space().appendLine("--- LOG EXTRAS -------------------------------------------------------").append(json);
    }

    // ==================== 日志构建 - 连接阶段 ====================

    private LogStringBuilder buildConnectLogs(HttpLogData data) {
        LogStringBuilder logs = new LogStringBuilder();
        HttpTiming timing = data.getTiming();
        HttpTiming.WebMetrics metrics = timing.getWebMetrics();

        long dnsLookup = metrics.dnsLookup();
        long connection = metrics.connection();

        if (dnsLookup >= 0 || connection >= 0) {
            logs.line().append(getEventTime(timing, HttpEvent.CALL_START)).space()
                    .append("--> CALL START ------------------------------------------------------>");
        }

        if (dnsLookup >= 0) {
            logs.line().append(getEventTime(timing, HttpEvent.DNS_END)).space()
                    .append("--> DNS LOOKUP (%dms)", dnsLookup);
        }

        if (connection >= 0) {
            String time = getEventTime(timing, HttpEvent.CONNECT_END, HttpEvent.CONNECT_FAILED);
            logs.line().append(time).space().append("--> CONNECTING (%dms)", connection);

            // 显示连接地址信息（同一行）
            if (isNotBlank(data.getLocalAddress()) && isNotBlank(data.getRemoteAddress())) {
                logs.append(" [%s -> %s]", data.getLocalAddress(), data.getRemoteAddress());
            } else if (isNotBlank(data.getRemoteAddress())) {
                logs.append(" [-> %s]", data.getRemoteAddress());
            } else if (isNotBlank(data.getLocalAddress())) {
                logs.append(" [%s ->]", data.getLocalAddress());
            }
        }

        return logs;
    }

    // ==================== 日志构建 - 请求阶段 ====================

    private LogStringBuilder buildRequestLogs(HttpLogData data) {
        LogStringBuilder logs = new LogStringBuilder();
        HttpTiming timing = data.getTiming();
        HttpLogData.Request request = data.getRequest();

        // 请求开始时间（兼容客户端和服务端）
        String startTime = data.isServer()
                ? getEventTime(timing, HttpEvent.REQUEST_RECEIVED, HttpEvent.START)
                : getEventTime(timing, HttpEvent.REQUEST_HEADERS_START, HttpEvent.CONNECT_FAILED);

        // 对 URL 进行脱敏处理
        String displayUrl = redactUrl(request.getUrl());

        logs.line().append(startTime).space()
                .appendLine("--> REQUEST START --------------------------------------------------->")
                .append("%s %s", request.getMethod(), displayUrl);

        if (isNotBlank(request.getProtocol())) {
            logs.append(" %s", request.getProtocol());
        }
        logs.line();

        // 请求头
        appendRequestHeaders(logs, request);

        // 请求体和结束
        appendRequestBody(logs, data);

        return logs;
    }

    private void appendRequestHeaders(LogStringBuilder logs, HttpLogData.Request request) {
        if (request.getBody() != null) {
            if (request.getContentType() != null && request.getHeaders().get("Content-Type") == null) {
                logs.appendKeyValueLine("Content-Type", request.getContentType());
            }
            if (request.getContentLength() != null && request.getContentLength() != -1L &&
                    request.getHeaders().get("Content-Length") == null) {
                logs.appendKeyValueLine("Content-Length", request.getContentLength());
            }
        }

        HttpLogData.Headers headers = request.getHeaders();
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.name(i);
            String value = headersToRedact.contains(name) ? "██" : headers.value(i);
            logs.appendKeyValueLine(name, value);
        }
    }

    private void appendRequestBody(LogStringBuilder logs, HttpLogData data) {
        HttpTiming timing = data.getTiming();
        HttpLogData.Request request = data.getRequest();

        // 耗时和结束时间（兼容客户端和服务端）
        String requestTime;
        String endTime;
        if (data.isServer()) {
            HttpTiming.WebMetrics metrics = timing.getWebMetrics();
            long bodyRead = metrics.requestBodyRead();
            requestTime = bodyRead >= 0 ? String.format("%dms", bodyRead) : "N/A";
            endTime = getEventTime(timing, HttpEvent.REQUEST_BODY_END, HttpEvent.REQUEST_RECEIVED);
        } else {
            HttpTiming.WebMetrics metrics = timing.getWebMetrics();
            requestTime = metrics.requestSent() >= 0 ? String.format("%dms", metrics.requestSent()) : "N/A";
            endTime = getEventTime(timing, HttpEvent.REQUEST_BODY_END, HttpEvent.REQUEST_HEADERS_END, HttpEvent.CONNECT_FAILED);
        }

        if (request.getBody() == null) {
            logs.append(endTime).space().appendLine("--> END REQUEST (%s)", requestTime);
        } else if (bodyHasUnknownEncoding(request.getHeaders())) {
            logs.append(endTime).space().appendLine("--> END REQUEST (%s, encoded body omitted)", requestTime);
        } else if (request.isDuplex()) {
            logs.append(endTime).space().appendLine("--> END REQUEST (%s, duplex request body omitted)", requestTime);
        } else if (request.isOneShot()) {
            logs.append(endTime).space().appendLine("--> END REQUEST (%s, one-shot body omitted)", requestTime);
        } else {
            String body = request.getBody();
            if (isNotBlank(body)) {
                logs.line().appendLine(body);
            }

            logs.append(endTime).space();
            int bodySize = request.getBody().getBytes(StandardCharsets.UTF_8).length;
            if ("gzip".equalsIgnoreCase(request.getHeaders().get("Content-Encoding"))) {
                logs.appendLine("--> END REQUEST (%s, %d-byte, %d-gzipped-byte body)",
                        requestTime, bodySize, request.getByteCount());
            } else {
                logs.appendLine("--> END REQUEST (%s, %d-byte body)", requestTime, bodySize);
            }
        }
    }

    // ==================== 日志构建 - 响应阶段 ====================

    private LogStringBuilder buildResponseLogs(HttpLogData data) {
        HttpLogData.Response resp = data.getResponse();
        if (resp == null) {
            return new LogStringBuilder();
        }

        LogStringBuilder logs = new LogStringBuilder();
        HttpTiming timing = data.getTiming();

        // 响应开始时间（兼容客户端和服务端）
        String startTime = data.isServer()
                ? getEventTime(timing, HttpEvent.RESPONSE_BUILD_START, HttpEvent.HANDLER_END)
                : getEventTime(timing, HttpEvent.RESPONSE_HEADERS_START);

        logs.append(startTime).space()
                .appendLine("<-- RESPONSE START <--------------------------------------------------");

        // 状态行
        if (resp.getProtocol() != null) {
            logs.append("%s ", resp.getProtocol());
        }
        logs.append("%s", resp.getCode());
        if (isNotBlank(resp.getMessage())) {
            logs.append(" %s", resp.getMessage());
        }

        // 处理时间（兼容客户端和服务端）
        if (data.isServer()) {
            HttpTiming.WebMetrics metrics = timing.getWebMetrics();
            long handlerTime = metrics.handlerExecution();
            if (handlerTime >= 0) {
                logs.appendLine(" (handler: %dms)", handlerTime);
            } else {
                logs.line();
            }
        } else {
            HttpTiming.WebMetrics metrics = timing.getWebMetrics();
            logs.appendLine(" (%dms)", metrics.serverProcessing());
        }

        // 响应头
        appendResponseHeaders(logs, resp);
        // 响应体
        appendResponseBody(logs, data, resp);

        return logs;
    }

    private void appendResponseHeaders(LogStringBuilder logs, HttpLogData.Response resp) {
        HttpLogData.Headers headers = resp.getHeaders();
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.name(i);
            String value = headersToRedact.contains(name) ? "██" : headers.value(i);
            logs.appendKeyValueLine(name, value);
        }
    }

    private void appendResponseBody(LogStringBuilder logs, HttpLogData data, HttpLogData.Response resp) {
        if (!promisesBody(data.getRequest(), resp)) {
            logs.append("<-- END RESPONSE");
        } else if (bodyHasUnknownEncoding(resp.getHeaders())) {
            logs.append("<-- END RESPONSE (encoded body omitted)");
        } else if (bodyIsStreaming(resp)) {
            logs.append("<-- END RESPONSE (streaming)");
        } else {
            // 输出响应体
            String body = resp.getBody();
            if (isNotBlank(body)) {
                logs.line().append(body);
            }

            HttpTiming timing = data.getTiming();
            int bodySize = (body != null) ? body.getBytes(StandardCharsets.UTF_8).length : 0;

            // 耗时和结束时间（兼容客户端和服务端）
            String endTime;
            long responseTime;
            if (data.isServer()) {
                HttpTiming.WebMetrics metrics = timing.getWebMetrics();
                responseTime = metrics.responseWrite();
                endTime = getEventTime(timing, HttpEvent.RESPONSE_BODY_END, HttpEvent.END);
            } else {
                responseTime = timing.getWebMetrics().contentDownload();
                endTime = getEventTime(timing, HttpEvent.RESPONSE_BODY_END);
            }

            logs.line().append(endTime).space()
                    .append("<-- END RESPONSE (%dms, %d-byte", responseTime, bodySize);

            if ("gzip".equalsIgnoreCase(resp.getHeaders().get("Content-Encoding"))) {
                logs.append(", %d-gzipped-byte", resp.getByteCount());
            }
            logs.appendLine(" body)");
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 对 URL 中的查询参数进行脱敏
     */
    private String redactUrl(String url) {
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
                if (queryParamsToRedact.contains(paramName) ||
                        queryParamsToRedact.contains(paramName.toLowerCase())) {
                    redactedQuery.append(redactPlaceholder);
                } else {
                    redactedQuery.append(paramValue);
                }
            }
        }

        return baseUrl + "?" + redactedQuery + fragment;
    }

    /**
     * 获取事件时间
     */
    private String getEventTime(HttpTiming timing, HttpEvent... events) {
        for (HttpEvent event : events) {
            Optional<HttpTiming.EventRecord> record = timing.getEventRecord(HttpRequestLogger.class, event.getEventName());
            if (record.isPresent()) {
                return timing.formatTime(record.get().getTime());
            }
        }
        return "";
    }

    private String getEventTime(HttpTiming timing, Class<?> clazz, String event) {
        Optional<HttpTiming.EventRecord> record = timing.getEventRecord(clazz, event);
        return record.map(r -> timing.formatTime(r.getTime())).orElse("");
    }

    private boolean bodyHasUnknownEncoding(HttpLogData.Headers headers) {
        String contentEncoding = headers.get("Content-Encoding");
        if (contentEncoding == null) {
            return false;
        }
        return !contentEncoding.equalsIgnoreCase("identity") &&
                !contentEncoding.equalsIgnoreCase("gzip");
    }

    private boolean bodyIsStreaming(HttpLogData.Response response) {
        HttpLogData.ContentType contentType = response.getContentType();
        return contentType != null &&
                "text".equals(contentType.getType()) &&
                "event-stream".equals(contentType.getSubtype());
    }

    /**
     * 判断响应是否包含 body（RFC 7231）
     */
    private boolean promisesBody(HttpLogData.Request request, HttpLogData.Response response) {
        if ("HEAD".equals(request.getMethod())) {
            return false;
        }

        int responseCode = response.getCode();
        if ((responseCode < HTTP_CONTINUE || responseCode >= 200) &&
                responseCode != HttpURLConnection.HTTP_NO_CONTENT &&
                responseCode != HttpURLConnection.HTTP_NOT_MODIFIED) {
            return true;
        }

        return headersContentLength(response) != -1L ||
                "chunked".equalsIgnoreCase(response.getHeaders().get("Transfer-Encoding"));
    }

    private long headersContentLength(HttpLogData.Response response) {
        String contentLengthHeader = response.getHeaders().get("Content-Length");
        if (contentLengthHeader != null) {
            try {
                return Long.parseLong(contentLengthHeader);
            } catch (NumberFormatException e) {
                return -1L;
            }
        }
        return -1L;
    }

    /**
     * 判断字符串是否非空白
     */
    private static boolean isNotBlank(String str) {
        return str != null && !str.isBlank();
    }
}

