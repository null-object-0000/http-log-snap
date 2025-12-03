package io.github.http.log.snap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP 请求耗时统计
 * 记录 HTTP 请求各阶段的时间点，支持计算各阶段耗时
 *
 * @author http-logging
 */
@Slf4j
public class HttpTiming {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final AtomicInteger steps = new AtomicInteger(0);

    /**
     * 按事件名称索引的记录（用于按名称查找）
     */
    @Getter
    private final Map<String, EventRecord> valuesByName = new ConcurrentHashMap<>();

    /**
     * 按步骤索引的记录（用于按顺序查找）
     */
    private final Map<Integer, EventRecord> valuesByStep = new ConcurrentHashMap<>();

    // ==================== 记录方法 ====================

    /**
     * 记录事件（使用当前时间）
     */
    public void record(@NonNull Class<?> clazz, @NonNull String event) {
        this.record(clazz, event, System.currentTimeMillis());
    }

    /**
     * 记录事件（使用枚举）
     */
    public void record(@NonNull Class<?> clazz, @NonNull HttpEvent event) {
        this.record(clazz, event.getEventName());
    }

    /**
     * 记录事件（指定时间）
     */
    public void record(@NonNull Class<?> clazz, @NonNull String event, long time) {
        int step = steps.incrementAndGet();
        EventRecord record = new EventRecord(clazz, event, time, step);
        String key = buildKey(clazz, event);
        valuesByName.put(key, record);
        valuesByStep.put(step, record);
    }

    // ==================== 查询方法 ====================

    /**
     * 获取第一个事件记录
     */
    public Optional<EventRecord> getFirstEventRecord() {
        return getEventRecordByStep(1);
    }

    /**
     * 获取最后一个事件记录
     */
    public Optional<EventRecord> getLastEventRecord() {
        return getEventRecordByStep(steps.get());
    }

    /**
     * 按步骤获取事件记录（O(1) 复杂度）
     */
    public Optional<EventRecord> getEventRecordByStep(int step) {
        return Optional.ofNullable(valuesByStep.get(step));
    }

    /**
     * 按步骤获取事件记录（兼容旧方法名）
     *
     * @deprecated 使用 {@link #getEventRecordByStep(int)} 代替
     */
    @Deprecated
    public Optional<EventRecord> getEventRecord(int step) {
        return getEventRecordByStep(step);
    }

    /**
     * 按类和事件名获取事件记录
     */
    public Optional<EventRecord> getEventRecord(@NonNull Class<?> clazz, @NonNull String event) {
        return Optional.ofNullable(valuesByName.get(buildKey(clazz, event)));
    }

    /**
     * 按类和事件枚举获取事件记录
     */
    public Optional<EventRecord> getEventRecord(@NonNull Class<?> clazz, @NonNull HttpEvent event) {
        return getEventRecord(clazz, event.getEventName());
    }

    // ==================== 计算方法 ====================

    /**
     * 计算两个事件之间的耗时
     */
    public long calculateTime(@NonNull Class<?> clazz, @NonNull String beginEvent, @NonNull String endEvent) {
        return calculateTime(clazz, beginEvent, clazz, endEvent);
    }

    /**
     * 计算事件的耗时（自动添加 Start/End 后缀）
     */
    public long calculateTime(@NonNull Class<?> clazz, @NonNull HttpEvent event) {
        return calculateTime(clazz, event.startName(), event.endName());
    }

    /**
     * 计算两个事件之间的耗时（可跨类）
     */
    public long calculateTime(@NonNull Class<?> beginClazz, @NonNull String beginEvent,
                              @NonNull Class<?> endClazz, @NonNull String endEvent) {
        Optional<EventRecord> begin = getEventRecord(beginClazz, beginEvent);
        Optional<EventRecord> end = getEventRecord(endClazz, endEvent);
        if (begin.isPresent() && end.isPresent()) {
            return end.get().getTime() - begin.get().getTime();
        }
        return -1;
    }

    /**
     * 获取总耗时
     */
    public long getTotalTime() {
        Optional<EventRecord> first = getFirstEventRecord();
        Optional<EventRecord> last = getLastEventRecord();
        if (first.isPresent() && last.isPresent()) {
            return last.get().getTime() - first.get().getTime();
        }
        return -1;
    }

    // ==================== 格式化方法 ====================

    /**
     * 格式化时间戳为可读字符串
     */
    public String formatTime(long timestamp) {
        if (timestamp < 0) {
            return "N/A";
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return dateTime.format(TIME_FORMATTER);
    }

    /**
     * 格式化耗时
     */
    public String formatDuration(long duration) {
        return duration >= 0 ? duration + "ms" : "N/A";
    }

    // ==================== 打印方法 ====================

    /**
     * 按照执行顺序打印所有事件，包括时间、事件名称和耗时
     */
    public String printSequential() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== HTTP Request Events (按执行顺序) ===\n\n");

        int totalSteps = steps.get();
        if (totalSteps == 0) {
            sb.append("No events recorded (无事件记录)\n");
            return sb.toString();
        }

        // 表头
        sb.append(String.format("%-6s %-25s %-50s %-15s %-15s%n",
                "Step", "Time", "Event", "Interval", "Cumulative"));
        sb.append(String.format("%-6s %-25s %-50s %-15s %-15s%n",
                "----", "----", "-----", "--------", "----------"));

        long firstTime = -1;
        long previousTime = -1;

        // 按步骤顺序遍历（现在是 O(1) 访问）
        for (int step = 1; step <= totalSteps; step++) {
            Optional<EventRecord> recordOpt = getEventRecordByStep(step);
            if (recordOpt.isEmpty()) {
                continue;
            }

            EventRecord record = recordOpt.get();
            long currentTime = record.getTime();

            // 记录第一个事件的时间
            if (firstTime < 0) {
                firstTime = currentTime;
                previousTime = currentTime;
            }

            // 计算相对于前一个事件的耗时
            long interval = currentTime - previousTime;
            // 计算相对于第一个事件的累计耗时
            long cumulative = currentTime - firstTime;
            // 格式化事件名称
            String eventName = record.getClazz().getSimpleName() + "." + record.getEvent();

            // 输出行
            sb.append(String.format("%-6d %-25s %-50s %-15s %-15s%n",
                    record.getStep(),
                    formatTime(currentTime),
                    eventName,
                    formatDuration(interval),
                    formatDuration(cumulative)));

            previousTime = currentTime;
        }

        sb.append("\n");
        return sb.toString();
    }

    /**
     * 重置所有记录
     */
    public void reset() {
        steps.set(0);
        valuesByName.clear();
        valuesByStep.clear();
    }

    // ==================== 辅助方法 ====================

    private static String buildKey(@NonNull Class<?> clazz, @NonNull String event) {
        return clazz.getSimpleName() + "." + event;
    }

    // ==================== 内部类 ====================

    /**
     * 事件记录
     */
    @Data
    @AllArgsConstructor
    public static class EventRecord {
        private Class<?> clazz;
        private String event;
        private long time;
        private int step;
    }

    // ==================== Web Metrics ====================

    /**
     * 获取 Web 性能指标计算器
     */
    public WebMetrics getWebMetrics() {
        return new WebMetrics(this);
    }

    /**
     * HTTP 性能指标
     * 同时支持客户端和服务端的耗时计算
     * <p>
     * 客户端指标：按照浏览器 DevTools Network 面板的标准指标计算
     * 服务端指标：按照请求处理生命周期计算
     */
    public static class WebMetrics {
        private final HttpTiming timing;

        public WebMetrics(HttpTiming timing) {
            this.timing = timing;
        }

        // ==================== 客户端指标 ====================

        /**
         * 请求准备时间（开始 -> DNS 开始）
         * 客户端专用
         */
        public long requestPreparation() {
            Optional<EventRecord> begin = timing.getFirstEventRecord();
            Optional<EventRecord> end = timing.getEventRecord(HttpRequestLogger.class, HttpEvent.DNS_START);
            return calculateDuration(begin, end);
        }

        /**
         * DNS 解析时间
         * 客户端专用
         */
        public long dnsLookup() {
            return timing.calculateTime(HttpRequestLogger.class, HttpEvent.DNS_START.getEventName(), HttpEvent.DNS_END.getEventName());
        }

        /**
         * 连接建立时间
         * 客户端专用
         */
        public long connection() {
            // 优先使用 connectionAcquired，否则使用 connectFailed
            Optional<EventRecord> connectionAcquired = timing.getEventRecord(HttpRequestLogger.class, HttpEvent.CONNECTION_ACQUIRED);
            String endEvent = connectionAcquired.isPresent()
                    ? HttpEvent.CONNECTION_ACQUIRED.getEventName()
                    : HttpEvent.CONNECT_FAILED.getEventName();
            return timing.calculateTime(HttpRequestLogger.class, HttpEvent.DNS_END.getEventName(), endEvent);
        }

        /**
         * 请求发送时间
         * 客户端专用
         */
        public long requestSent() {
            Optional<EventRecord> requestBodyEnd = timing.getEventRecord(HttpRequestLogger.class, HttpEvent.REQUEST_BODY_END);
            String endEvent = requestBodyEnd.isPresent()
                    ? HttpEvent.REQUEST_BODY_END.getEventName()
                    : HttpEvent.REQUEST_HEADERS_END.getEventName();
            return timing.calculateTime(HttpRequestLogger.class, HttpEvent.CONNECTION_ACQUIRED.getEventName(), endEvent);
        }

        /**
         * 服务端处理时间（TTFB - Time To First Byte）
         * 客户端视角：请求发送完成 -> 收到响应头
         */
        public long serverProcessing() {
            Optional<EventRecord> requestBodyEnd = timing.getEventRecord(HttpRequestLogger.class, HttpEvent.REQUEST_BODY_END);
            String startEvent = requestBodyEnd.isPresent()
                    ? HttpEvent.REQUEST_BODY_END.getEventName()
                    : HttpEvent.REQUEST_HEADERS_END.getEventName();
            return timing.calculateTime(HttpRequestLogger.class, startEvent, HttpEvent.RESPONSE_HEADERS_START.getEventName());
        }

        /**
         * 内容下载时间
         * 客户端专用
         */
        public long contentDownload() {
            Optional<EventRecord> responseBodyEnd = timing.getEventRecord(HttpRequestLogger.class, HttpEvent.RESPONSE_BODY_END);
            String endEvent = responseBodyEnd.isPresent()
                    ? HttpEvent.RESPONSE_BODY_END.getEventName()
                    : HttpEvent.RESPONSE_HEADERS_END.getEventName();
            return timing.calculateTime(HttpRequestLogger.class, HttpEvent.RESPONSE_HEADERS_START.getEventName(), endEvent);
        }

        /**
         * 执行完成时间（响应结束 -> 总结束）
         * 通用
         */
        public long executionFinalization() {
            Optional<EventRecord> begin = timing.getEventRecord(HttpRequestLogger.class, HttpEvent.RESPONSE_BODY_END);
            if (begin.isEmpty()) {
                begin = timing.getEventRecord(HttpRequestLogger.class, HttpEvent.RESPONSE_HEADERS_END);
            }
            Optional<EventRecord> end = timing.getLastEventRecord();
            return calculateDuration(begin, end);
        }

        // ==================== 服务端指标 ====================

        /**
         * 框架开销时间（请求接收 -> Handler 开始）
         * 服务端专用
         */
        public long frameworkOverhead() {
            return timing.calculateTime(HttpRequestLogger.class,
                    HttpEvent.START.getEventName(),
                    HttpEvent.HANDLER_START.getEventName());
        }

        /**
         * 请求体读取时间
         * 通用（客户端是发送，服务端是接收）
         */
        public long requestBodyRead() {
            return timing.calculateTime(HttpRequestLogger.class,
                    HttpEvent.REQUEST_BODY_START.getEventName(),
                    HttpEvent.REQUEST_BODY_END.getEventName());
        }

        /**
         * Handler/业务处理时间
         * 服务端专用
         */
        public long handlerExecution() {
            return timing.calculateTime(HttpRequestLogger.class,
                    HttpEvent.HANDLER_START.getEventName(),
                    HttpEvent.HANDLER_END.getEventName());
        }

        /**
         * 响应构建时间
         * 服务端专用
         */
        public long responseBuild() {
            return timing.calculateTime(HttpRequestLogger.class,
                    HttpEvent.RESPONSE_BUILD_START.getEventName(),
                    HttpEvent.RESPONSE_BUILD_END.getEventName());
        }

        /**
         * 响应发送时间
         * 通用（客户端是接收，服务端是发送）
         */
        public long responseWrite() {
            return timing.calculateTime(HttpRequestLogger.class,
                    HttpEvent.RESPONSE_BODY_START.getEventName(),
                    HttpEvent.RESPONSE_BODY_END.getEventName());
        }

        // ==================== 通用指标 ====================

        /**
         * 总处理时间
         */
        public long totalTime() {
            return timing.getTotalTime();
        }

        /**
         * 打印性能指标
         */
        public String print() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== HTTP Metrics Timing (HTTP指标耗时详情) ===\n\n");

            // Summary
            Optional<EventRecord> first = timing.getFirstEventRecord();
            Optional<EventRecord> last = timing.getLastEventRecord();
            long totalStart = first.map(EventRecord::getTime).orElse(-1L);
            long totalEnd = last.map(EventRecord::getTime).orElse(-1L);

            sb.append("Summary (总览):\n");
            sb.append(String.format("  Start Time (开始时间): %s%n", timing.formatTime(totalStart)));
            sb.append(String.format("  End Time   (结束时间): %s%n", timing.formatTime(totalEnd)));
            sb.append(String.format("  Total Time (总耗时):   %s%n", timing.formatDuration(totalTime())));
            sb.append("\n");

            // 客户端指标
            appendMetricLineIfValid(sb, "Request Preparation (客户端)", requestPreparation());
            appendMetricLineIfValid(sb, "DNS Lookup (客户端)", dnsLookup());
            appendMetricLineIfValid(sb, "Connection (客户端)", connection());
            appendMetricLineIfValid(sb, "Request Sent (客户端)", requestSent());
            appendMetricLineIfValid(sb, "Server Processing (客户端)", serverProcessing());
            appendMetricLineIfValid(sb, "Content Download (客户端)", contentDownload());

            // 服务端指标
            appendMetricLineIfValid(sb, "Framework Overhead (服务端)", frameworkOverhead());
            appendMetricLineIfValid(sb, "Request Body Read (通用)", requestBodyRead());
            appendMetricLineIfValid(sb, "Handler Execution (服务端)", handlerExecution());
            appendMetricLineIfValid(sb, "Response Build (服务端)", responseBuild());
            appendMetricLineIfValid(sb, "Response Write (通用)", responseWrite());

            // 通用指标
            appendMetricLineIfValid(sb, "Execution Finalization (通用)", executionFinalization());

            return sb.toString();
        }

        private void appendMetricLineIfValid(StringBuilder sb, String label, long duration) {
            if (duration >= 0) {
                sb.append(String.format("  %-40s %s%n", label + ":", timing.formatDuration(duration)));
            }
        }

        private static long calculateDuration(Optional<EventRecord> begin, Optional<EventRecord> end) {
            if (begin.isPresent() && end.isPresent()) {
                return end.get().getTime() - begin.get().getTime();
            }
            return -1;
        }
    }
}
