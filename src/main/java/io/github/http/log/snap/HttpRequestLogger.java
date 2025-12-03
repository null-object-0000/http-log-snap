package io.github.http.log.snap;

import io.github.http.log.snap.formatter.HttpLogFormatter;
import io.github.http.log.snap.formatter.JsonHttpLogFormatter;
import io.github.http.log.snap.formatter.TextHttpLogFormatter;
import io.github.http.log.snap.output.HttpLogOutput;
import io.github.http.log.snap.output.Slf4jLogOutput;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * HTTP 请求日志记录器
 * 负责记录 HTTP 请求的各个阶段事件和耗时，并生成格式化的日志输出
 * <p>
 * 同时支持客户端（发起请求）和服务端（处理请求）两种场景，通过 {@link HttpDirection} 区分
 * <p>
 * 支持扩展不同的日志输出格式，通过 {@link HttpLogFormatter} 接口实现
 *
 * @author http-logging
 */
@Slf4j
public class HttpRequestLogger {

    /**
     * 默认的日志格式化器（文本格式）
     */
    private static HttpLogFormatter defaultFormatter = new TextHttpLogFormatter();

    /**
     * 默认的日志输出（SLF4J）
     */
    private static HttpLogOutput defaultOutput = new Slf4jLogOutput();

    /**
     * 当前使用的日志格式化器
     */
    @Getter
    @Setter
    private HttpLogFormatter formatter = defaultFormatter;

    /**
     * 当前使用的日志输出
     */
    @Getter
    @Setter
    private HttpLogOutput output = defaultOutput;

    /**
     * HTTP 方向（客户端/服务端）
     */
    @Getter
    private final HttpDirection direction;

    private final HttpTiming httpTiming = new HttpTiming();
    private final HttpLogContext context;

    @NonNull
    private final HttpLogData.Request request = new HttpLogData.Request();
    @Nullable
    private HttpLogData.Response response = null;

    private long startTimeMs;
    private long endTimeMs;

    // ==================== 处理器/执行器信息 ====================

    /**
     * 处理器/执行器类
     * - 客户端：执行器类型（如 OkHttpHttpRequestExecutor）
     * - 服务端：Handler 类（如 Controller）
     */
    @Getter
    @Setter
    private Class<?> handlerClass;

    /**
     * 处理器/执行器方法名
     * - 客户端：执行方法（如 execute）
     * - 服务端：Handler 方法名
     */
    @Getter
    @Setter
    private String handlerMethod;

    /**
     * 处理过程中的异常
     */
    @Getter
    @Setter
    private Throwable exception;

    // ==================== 网络地址信息 ====================

    /**
     * 本地地址
     * - 客户端：发起请求的本机 IP:端口
     * - 服务端：服务器 IP:端口
     */
    @Getter
    @Setter
    private String localAddress;

    /**
     * 远程地址
     * - 客户端：目标服务器 IP:端口
     * - 服务端：请求来源客户端 IP:端口
     */
    @Getter
    @Setter
    private String remoteAddress;

    // ==================== 构造方法 ====================

    /**
     * 创建客户端日志记录器
     */
    public HttpRequestLogger(HttpLogContext context) {
        this(HttpDirection.CLIENT, context);
    }

    /**
     * 创建指定方向的日志记录器
     */
    public HttpRequestLogger(HttpDirection direction, HttpLogContext context) {
        this.direction = direction;
        this.context = context;
    }

    /**
     * 创建服务端日志记录器（便捷方法）
     */
    public static HttpRequestLogger forServer() {
        return new HttpRequestLogger(HttpDirection.SERVER, null);
    }

    /**
     * 创建服务端日志记录器（带上下文）
     */
    public static HttpRequestLogger forServer(HttpLogContext context) {
        return new HttpRequestLogger(HttpDirection.SERVER, context);
    }

    /**
     * 创建客户端日志记录器（便捷方法）
     */
    public static HttpRequestLogger forClient() {
        return new HttpRequestLogger(HttpDirection.CLIENT, null);
    }

    /**
     * 创建客户端日志记录器（带上下文）
     */
    public static HttpRequestLogger forClient(HttpLogContext context) {
        return new HttpRequestLogger(HttpDirection.CLIENT, context);
    }

    // ==================== 静态配置方法 ====================

    /**
     * 设置全局默认的日志格式化器
     */
    public static void setDefaultFormatter(@NonNull HttpLogFormatter formatter) {
        defaultFormatter = formatter;
    }

    /**
     * 获取全局默认的日志格式化器
     */
    public static HttpLogFormatter getDefaultFormatter() {
        return defaultFormatter;
    }

    /**
     * 设置全局默认的日志输出
     */
    public static void setDefaultOutput(@NonNull HttpLogOutput output) {
        defaultOutput = output;
    }

    /**
     * 获取全局默认的日志输出
     */
    public static HttpLogOutput getDefaultOutput() {
        return defaultOutput;
    }

    // ==================== 生命周期方法 ====================

    /**
     * 开始记录请求（客户端使用）
     */
    public HttpRequestLogger start(String body) {
        this.startTimeMs = System.currentTimeMillis();
        this.request.setBody(body);
        record(HttpEvent.START);
        return this;
    }

    /**
     * 开始记录请求（服务端使用，无请求体）
     */
    public HttpRequestLogger start() {
        this.startTimeMs = System.currentTimeMillis();
        record(HttpEvent.START);
        return this;
    }

    /**
     * 结束记录请求（客户端使用）
     */
    public HttpRequestLogger end(String body) {
        this.endTimeMs = System.currentTimeMillis();
        if (this.response != null) {
            this.response.setBody(body);
        }
        record(HttpEvent.END);
        return this;
    }

    /**
     * 结束记录请求（服务端使用，无响应体）
     */
    public HttpRequestLogger end() {
        this.endTimeMs = System.currentTimeMillis();
        record(HttpEvent.END);
        return this;
    }

    /**
     * 输出日志
     */
    public void log() {
        HttpLogData data = buildLogData();
        String formattedLog = formatter.format(data);
        Throwable ex = data.getException();

        if (ex != null) {
            output.outputError(data, formattedLog, ex);
        } else {
            output.output(data, formattedLog);
        }
    }

    /**
     * 输出日志到指定输出
     */
    public void log(@NonNull HttpLogOutput output) {
        HttpLogData data = buildLogData();
        String formattedLog = formatter.format(data);
        Throwable ex = data.getException();

        if (ex != null) {
            output.outputError(data, formattedLog, ex);
        } else {
            output.output(data, formattedLog);
        }
    }

    // ==================== 事件记录方法 ====================

    /**
     * 记录事件
     */
    public void record(HttpEvent event) {
        httpTiming.record(HttpRequestLogger.class, event.getEventName());
    }

    /**
     * 记录事件（使用字符串）
     */
    public void record(Class<?> clazz, String event) {
        httpTiming.record(clazz, event);
    }

    /**
     * 记录事件（使用字符串，使用当前类）
     */
    public void record(String event) {
        httpTiming.record(HttpRequestLogger.class, event);
    }

    /**
     * 运行并记录耗时（无返回值）
     */
    public void runWithRecord(Class<?> clazz, String event, Runnable runnable) {
        runWithRecord(clazz, event, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 运行并记录耗时（有返回值）
     */
    public <R> R runWithRecord(Class<?> clazz, String event, Supplier<R> supplier) {
        httpTiming.record(clazz, event + "Start");
        try {
            return supplier.get();
        } catch (Exception e) {
            httpTiming.record(clazz, event + "Exception");
            throw e;
        } finally {
            httpTiming.record(clazz, event + "End");
        }
    }

    /**
     * 打印按顺序排列的事件列表
     */
    public String printSequential() {
        return httpTiming.printSequential();
    }

    // ==================== 请求记录方法 ====================

    /**
     * 合并请求信息并记录事件
     */
    public void recordRequest(@Nullable HttpLogData.Request request, @NonNull HttpEvent event) {
        if (request != null) {
            this.request.merge(request);
        }
        record(event);
    }

    /**
     * 合并请求信息、设置字节数并记录事件
     */
    public void recordRequest(@Nullable HttpLogData.Request request, @NonNull HttpEvent event, long byteCount) {
        if (request != null) {
            this.request.merge(request);
        }
        this.request.setByteCount(byteCount);
        record(event);
    }

    /**
     * 设置请求异常并记录事件
     */
    public void recordRequestException(@NonNull IOException ioe, @NonNull HttpEvent event) {
        this.request.setIoe(ioe);
        record(event);
    }

    /**
     * 记录请求已接收（服务端使用）
     */
    public void recordRequestReceived(@NonNull HttpLogData.Request request) {
        this.request.merge(request);
        record(HttpEvent.REQUEST_RECEIVED);
    }

    /**
     * 记录请求体开始（读取/发送）
     */
    public void recordRequestBodyStart() {
        record(HttpEvent.REQUEST_BODY_START);
    }

    /**
     * 记录请求体结束（读取/发送）
     */
    public void recordRequestBodyEnd(String body, long byteCount) {
        this.request.setBody(body);
        this.request.setByteCount(byteCount);
        record(HttpEvent.REQUEST_BODY_END);
    }

    /**
     * 记录请求异常（服务端使用）
     */
    public void recordRequestException(@NonNull IOException ioe) {
        this.request.setIoe(ioe);
        record(HttpEvent.REQUEST_FAILED);
    }

    // ==================== Handler 记录方法（服务端专用） ====================

    /**
     * 记录 Handler 开始处理
     */
    public void recordHandlerStart(Class<?> handlerClass, String handlerMethod) {
        this.handlerClass = handlerClass;
        this.handlerMethod = handlerMethod;
        record(HttpEvent.HANDLER_START);
    }

    /**
     * 记录 Handler 开始处理（无 Handler 信息）
     */
    public void recordHandlerStart() {
        record(HttpEvent.HANDLER_START);
    }

    /**
     * 记录 Handler 处理结束
     */
    public void recordHandlerEnd() {
        record(HttpEvent.HANDLER_END);
    }

    /**
     * 记录 Handler 处理异常
     */
    public void recordHandlerException(@NonNull Throwable exception) {
        this.exception = exception;
        record(HttpEvent.HANDLER_EXCEPTION);
    }

    // ==================== 响应记录方法 ====================

    /**
     * 合并响应信息并记录事件
     */
    public void recordResponse(@Nullable HttpLogData.Response response, @NonNull HttpEvent event) {
        if (response != null) {
            this.response = (this.response == null) ? response : this.response.merge(response);
        }
        record(event);
    }

    /**
     * 设置响应字节数并记录事件
     */
    public void recordResponse(@NonNull HttpEvent event, long byteCount) {
        HttpLogData.Response resp = (this.response != null) ? this.response : new HttpLogData.Response();
        resp.setByteCount(byteCount);
        this.response = resp;
        record(event);
    }

    /**
     * 设置响应异常并记录事件
     */
    public void recordResponseException(@NonNull IOException ioe, @NonNull HttpEvent event) {
        HttpLogData.Response resp = (this.response != null) ? this.response : new HttpLogData.Response();
        resp.setIoe(ioe);
        this.response = resp;
        record(event);
    }

    /**
     * 记录响应构建（服务端使用）
     */
    public void recordResponseBuild(@NonNull HttpLogData.Response response) {
        this.response = (this.response == null) ? response : this.response.merge(response);
        record(HttpEvent.RESPONSE_BUILD_START);
        record(HttpEvent.RESPONSE_BUILD_END);
    }

    /**
     * 记录响应体开始（发送/接收）
     */
    public void recordResponseBodyStart() {
        record(HttpEvent.RESPONSE_BODY_START);
    }

    /**
     * 记录响应体结束（发送/接收）
     */
    public void recordResponseBodyEnd(String body, long byteCount) {
        HttpLogData.Response resp = (this.response != null) ? this.response : new HttpLogData.Response();
        resp.setBody(body);
        resp.setByteCount(byteCount);
        this.response = resp;
        record(HttpEvent.RESPONSE_BODY_END);
    }

    /**
     * 记录响应提交（服务端使用）
     */
    public void recordResponseCommitted() {
        record(HttpEvent.RESPONSE_COMMITTED);
    }

    // ==================== 数据访问 ====================

    /**
     * 构建日志数据对象
     */
    public HttpLogData buildLogData() {
        return HttpLogData.builder()
                .direction(direction)
                .context(context)
                .request(request)
                .response(response)
                .timing(httpTiming)
                .startTimeMs(startTimeMs)
                .endTimeMs(endTimeMs)
                .localAddress(localAddress)
                .remoteAddress(remoteAddress)
                .handlerClass(handlerClass)
                .handlerMethod(handlerMethod)
                .exception(exception)
                .build();
    }

    /**
     * 获取耗时统计
     */
    public HttpTiming getTiming() {
        return httpTiming;
    }

    /**
     * 获取请求信息
     */
    public HttpLogData.Request getRequest() {
        return request;
    }

    /**
     * 获取响应信息
     */
    @Nullable
    public HttpLogData.Response getResponse() {
        return response;
    }

    /**
     * 获取日志上下文
     */
    @Nullable
    public HttpLogContext getContext() {
        return context;
    }

    /**
     * 是否为服务端
     */
    public boolean isServer() {
        return direction == HttpDirection.SERVER;
    }

    /**
     * 是否为客户端
     */
    public boolean isClient() {
        return direction == HttpDirection.CLIENT;
    }

    // ==================== 格式化输出 ====================

    @Override
    public String toString() {
        return formatter.format(buildLogData());
    }

    /**
     * 使用指定格式化器输出日志
     */
    public String format(@NonNull HttpLogFormatter formatter) {
        return formatter.format(buildLogData());
    }

    /**
     * 输出 JSON 格式日志
     */
    public String toJson() {
        return new JsonHttpLogFormatter().format(buildLogData());
    }

    /**
     * 输出格式化的 JSON 日志（美化输出）
     */
    public String toPrettyJson() {
        return new JsonHttpLogFormatter(true).format(buildLogData());
    }

    // ==================== 性能指标 ====================

    /**
     * 获取性能指标（统一接口，客户端/服务端通用）
     */
    public HttpTiming.WebMetrics getMetrics() {
        return httpTiming.getWebMetrics();
    }
}
