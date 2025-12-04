package io.github.http.log.snap.formatter;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import io.github.http.log.snap.HttpLogContext;
import io.github.http.log.snap.HttpLogData;
import io.github.http.log.snap.HttpTiming;
import lombok.NonNull;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * JSON 格式 HTTP 日志格式化器
 * 生成结构化的 JSON 日志，便于 ELK 等日志分析系统处理
 *
 * @author http-logging
 */
public class JsonHttpLogFormatter implements HttpLogFormatter {

    private Set<String> headersToRedact = new HashSet<>();
    private Set<String> queryParamsToRedact = new HashSet<>();
    private String redactPlaceholder = "██";

    /**
     * 是否格式化输出（美化 JSON）
     */
    private boolean prettyPrint = false;

    /**
     * 是否包含完整的事件序列（默认只记录计算后的指标）
     * 开启后会在 timing.events 中记录所有事件的详细信息
     */
    private boolean includeEvents = false;

    public JsonHttpLogFormatter() {
    }

    public JsonHttpLogFormatter(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public JsonHttpLogFormatter(boolean prettyPrint, boolean includeEvents) {
        this.prettyPrint = prettyPrint;
        this.includeEvents = includeEvents;
    }

    @Override
    public String format(@NonNull HttpLogData data) {
        JSONObject json = new JSONObject(new LinkedHashMap<>());

        // 基本信息
        json.put("type", data.isServer() ? "HTTP_SERVER" : "HTTP_CLIENT");
        json.put("direction", data.getDirection().getCode());
        json.put("timestamp", data.getStartTimeMs());
        json.put("duration_ms", data.getTotalTimeMs());
        json.put("success", !data.hasFailed());

        // 上下文信息
        if (data.getContext() != null) {
            json.put("context", buildContextJson(data.getContext()));
        }

        // 服务端特有：Handler 信息
        if (data.isServer() && data.getHandlerName() != null) {
            json.put("handler", data.getHandlerName());
        }

        // 网络地址信息
        if (data.getLocalAddress() != null || data.getRemoteAddress() != null) {
            JSONObject network = new JSONObject(new LinkedHashMap<>());
            if (data.getLocalAddress() != null) {
                network.put("local_address", data.getLocalAddress());
            }
            if (data.getRemoteAddress() != null) {
                network.put("remote_address", data.getRemoteAddress());
            }
            json.put("network", network);
        }

        // 请求信息
        json.put("request", buildRequestJson(data.getRequest()));

        // 响应信息
        if (data.getResponse() != null) {
            json.put("response", buildResponseJson(data.getResponse()));
        }

        // 耗时指标
        json.put("timing", buildTimingJson(data));

        // 异常信息
        Throwable exception = data.getException();
        if (exception != null) {
            json.put("error", buildErrorJson(exception));
        }

        if (prettyPrint) {
            return json.toJSONString(JSONWriter.Feature.PrettyFormat);
        }
        return json.toJSONString();
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
    public JsonHttpLogFormatter redactQueryParams(Set<String> paramNames) {
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
    public JsonHttpLogFormatter setRedactPlaceholder(String placeholder) {
        this.redactPlaceholder = placeholder;
        return this;
    }

    @Override
    public String getFormatType() {
        return "JSON";
    }

    /**
     * 设置是否格式化输出
     */
    public JsonHttpLogFormatter setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
        return this;
    }

    /**
     * 设置是否包含完整的事件序列
     * 开启后会在 timing.events 中记录所有事件的详细信息，便于分析调用链路
     *
     * @param includeEvents 是否包含事件序列
     * @return 当前格式化器实例（支持链式调用）
     */
    public JsonHttpLogFormatter setIncludeEvents(boolean includeEvents) {
        this.includeEvents = includeEvents;
        return this;
    }

    // ==================== 构建 JSON 对象 ====================

    private JSONObject buildContextJson(HttpLogContext context) {
        JSONObject json = new JSONObject(new LinkedHashMap<>());
        if (context.getInterfaceName() != null) {
            json.put("interface", context.getInterfaceName());
        }
        if (context.getTraceId() != null) {
            json.put("trace_id", context.getTraceId());
        }
        if (context.getSpanId() != null) {
            json.put("span_id", context.getSpanId());
        }
        if (context.getTag1() != null) {
            json.put("tag1", context.getTag1());
        }
        if (context.getTag2() != null) {
            json.put("tag2", context.getTag2());
        }
        if (context.getExtras() != null && !context.getExtras().isEmpty()) {
            json.put("extras", context.getExtras());
        }
        return json;
    }

    private JSONObject buildRequestJson(HttpLogData.Request request) {
        JSONObject json = new JSONObject(new LinkedHashMap<>());

        json.put("method", request.getMethod());
        json.put("url", redactUrl(request.getUrl()));
        if (request.getProtocol() != null) {
            json.put("protocol", request.getProtocol());
        }
        if (request.getProxy() != null) {
            json.put("proxy", request.getProxy().toString());
        }

        // 请求头
        json.put("headers", buildHeadersJson(request.getHeaders()));

        // 请求体信息
        if (request.getContentType() != null) {
            json.put("content_type", request.getContentType().toString());
        }
        if (request.getContentLength() != null && request.getContentLength() >= 0) {
            json.put("content_length", request.getContentLength());
        }
        if (request.getBody() != null) {
            json.put("body", request.getBody());
            json.put("body_bytes", request.getByteCount());
        }

        // 特殊标记
        if (request.isDuplex()) {
            json.put("duplex", true);
        }
        if (request.isOneShot()) {
            json.put("one_shot", true);
        }

        return json;
    }

    private JSONObject buildResponseJson(HttpLogData.Response response) {
        JSONObject json = new JSONObject(new LinkedHashMap<>());

        json.put("code", response.getCode());
        if (response.getMessage() != null) {
            json.put("message", response.getMessage());
        }
        if (response.getProtocol() != null) {
            json.put("protocol", response.getProtocol());
        }

        // 响应头
        json.put("headers", buildHeadersJson(response.getHeaders()));

        // 响应体信息
        if (response.getContentType() != null) {
            json.put("content_type", response.getContentType().toString());
        }
        if (response.getContentLength() != null && response.getContentLength() >= 0) {
            json.put("content_length", response.getContentLength());
        }
        if (response.getBody() != null) {
            json.put("body", response.getBody());
            json.put("body_bytes", response.getByteCount());
        }

        return json;
    }

    private JSONObject buildHeadersJson(HttpLogData.Headers headers) {
        JSONObject json = new JSONObject(new LinkedHashMap<>());
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.name(i);
            String value = headersToRedact.contains(name) ? "██" : headers.value(i);
            json.put(name, value);
        }
        return json;
    }

    private JSONObject buildTimingJson(HttpLogData data) {
        JSONObject json = new JSONObject(new LinkedHashMap<>());
        HttpTiming timing = data.getTiming();

        json.put("total_ms", timing.getTotalTime());

        HttpTiming.WebMetrics metrics = timing.getWebMetrics();
        JSONObject metricsJson = new JSONObject(new LinkedHashMap<>());

        if (data.isServer()) {
            // 服务端指标
            putIfPositive(metricsJson, "framework_overhead_ms", metrics.frameworkOverhead());
            putIfPositive(metricsJson, "request_body_read_ms", metrics.requestBodyRead());
            putIfPositive(metricsJson, "handler_execution_ms", metrics.handlerExecution());
            putIfPositive(metricsJson, "response_build_ms", metrics.responseBuild());
            putIfPositive(metricsJson, "response_write_ms", metrics.responseWrite());
        } else {
            // 客户端指标
            putIfPositive(metricsJson, "request_preparation_ms", metrics.requestPreparation());
            putIfPositive(metricsJson, "dns_lookup_ms", metrics.dnsLookup());
            putIfPositive(metricsJson, "connection_ms", metrics.connection());
            putIfPositive(metricsJson, "request_sent_ms", metrics.requestSent());
            putIfPositive(metricsJson, "server_processing_ms", metrics.serverProcessing());
            putIfPositive(metricsJson, "content_download_ms", metrics.contentDownload());
        }
        putIfPositive(metricsJson, "finalization_ms", metrics.executionFinalization());
        json.put("metrics", metricsJson);

        // 如果开启了事件序列记录，输出完整的事件列表
        if (includeEvents) {
            json.put("events", buildEventsJson(timing));
        }

        return json;
    }

    /**
     * 构建事件序列 JSON 数组
     * 按执行顺序输出所有事件的详细信息
     */
    private com.alibaba.fastjson2.JSONArray buildEventsJson(HttpTiming timing) {
        com.alibaba.fastjson2.JSONArray eventsArray = new com.alibaba.fastjson2.JSONArray();

        // 获取第一个事件的时间作为基准
        long baseTime = timing.getFirstEventRecord()
                .map(HttpTiming.EventRecord::getTime)
                .orElse(0L);

        long previousTime = baseTime;

        // 按步骤顺序遍历所有事件
        for (int step = 1; ; step++) {
            var recordOpt = timing.getEventRecordByStep(step);
            if (recordOpt.isEmpty()) {
                break;
            }

            HttpTiming.EventRecord record = recordOpt.get();
            long currentTime = record.getTime();

            JSONObject eventJson = new JSONObject(new LinkedHashMap<>());
            eventJson.put("step", record.getStep());
            eventJson.put("event", record.getClazz().getSimpleName() + "." + record.getEvent());
            eventJson.put("timestamp", currentTime);
            eventJson.put("interval_ms", currentTime - previousTime);
            eventJson.put("elapsed_ms", currentTime - baseTime);

            eventsArray.add(eventJson);
            previousTime = currentTime;
        }

        return eventsArray;
    }

    private JSONObject buildErrorJson(Throwable exception) {
        JSONObject json = new JSONObject(new LinkedHashMap<>());
        json.put("type", exception.getClass().getSimpleName());
        json.put("message", exception.getMessage());

        // 堆栈信息（可选，避免日志过大）
        StackTraceElement[] stackTrace = exception.getStackTrace();
        if (stackTrace.length > 0) {
            json.put("location", stackTrace[0].toString());
        }

        return json;
    }

    private void putIfPositive(JSONObject json, String key, long value) {
        if (value >= 0) {
            json.put(key, value);
        }
    }

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
}

