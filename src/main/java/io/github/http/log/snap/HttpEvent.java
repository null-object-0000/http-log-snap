package io.github.http.log.snap;

import lombok.Getter;

/**
 * HTTP 事件枚举
 * 定义了 HTTP 请求生命周期中的所有事件，同时支持客户端和服务端
 *
 * @author http-logging
 */
@Getter
public enum HttpEvent {

    // ==================== 通用生命周期事件 ====================
    /**
     * 请求开始
     */
    START("start"),
    /**
     * 请求结束
     */
    END("end"),

    // ==================== 准备阶段（客户端专用） ====================
    /**
     * 构建 URI
     */
    BUILD_URI("buildURI"),
    /**
     * 构建配置
     */
    BUILD_CONFIG("buildConfig"),
    /**
     * 构建客户端
     */
    BUILD_CLIENT("buildClient"),
    /**
     * 构建请求
     */
    BUILD_REQUEST("buildRequest"),

    // ==================== 调用阶段（客户端专用） ====================
    /**
     * 调用开始
     */
    CALL_START("callStart"),
    /**
     * 调用结束
     */
    CALL_END("callEnd"),
    /**
     * 调用失败
     */
    CALL_FAILED("callFailed"),

    // ==================== 代理选择阶段（客户端专用） ====================
    /**
     * 代理选择开始
     */
    PROXY_SELECT_START("proxySelectStart"),
    /**
     * 代理选择结束
     */
    PROXY_SELECT_END("proxySelectEnd"),

    // ==================== DNS 解析阶段（客户端专用） ====================
    /**
     * DNS 解析开始
     */
    DNS_START("dnsStart"),
    /**
     * DNS 解析结束
     */
    DNS_END("dnsEnd"),

    // ==================== 连接阶段（客户端专用） ====================
    /**
     * 连接开始
     */
    CONNECT_START("connectStart"),
    /**
     * 连接结束
     */
    CONNECT_END("connectEnd"),
    /**
     * 连接失败
     */
    CONNECT_FAILED("connectFailed"),
    /**
     * 安全连接开始
     */
    SECURE_CONNECT_START("secureConnectStart"),
    /**
     * 安全连接结束
     */
    SECURE_CONNECT_END("secureConnectEnd"),
    /**
     * 连接获取
     */
    CONNECTION_ACQUIRED("connectionAcquired"),
    /**
     * 连接释放
     */
    CONNECTION_RELEASED("connectionReleased"),

    // ==================== 请求阶段（通用） ====================
    /**
     * 请求已接收（服务端）/ 请求开始处理
     */
    REQUEST_RECEIVED("requestReceived"),
    /**
     * 请求头开始（发送/接收/解析）
     */
    REQUEST_HEADERS_START("requestHeadersStart"),
    /**
     * 请求头结束
     */
    REQUEST_HEADERS_END("requestHeadersEnd"),
    /**
     * 请求体开始（发送/读取）
     */
    REQUEST_BODY_START("requestBodyStart"),
    /**
     * 请求体结束
     */
    REQUEST_BODY_END("requestBodyEnd"),
    /**
     * 请求失败/异常
     */
    REQUEST_FAILED("requestFailed"),

    // ==================== 业务处理阶段（服务端专用） ====================
    /**
     * Handler 处理开始
     */
    HANDLER_START("handlerStart"),
    /**
     * Handler 处理结束
     */
    HANDLER_END("handlerEnd"),
    /**
     * Handler 处理异常
     */
    HANDLER_EXCEPTION("handlerException"),

    // ==================== 响应阶段（通用） ====================
    /**
     * 响应构建开始（服务端）
     */
    RESPONSE_BUILD_START("responseBuildStart"),
    /**
     * 响应构建结束（服务端）
     */
    RESPONSE_BUILD_END("responseBuildEnd"),
    /**
     * 响应头开始（发送/接收）
     */
    RESPONSE_HEADERS_START("responseHeadersStart"),
    /**
     * 响应头结束
     */
    RESPONSE_HEADERS_END("responseHeadersEnd"),
    /**
     * 响应体开始（发送/接收）
     */
    RESPONSE_BODY_START("responseBodyStart"),
    /**
     * 响应体结束
     */
    RESPONSE_BODY_END("responseBodyEnd"),
    /**
     * 响应提交完成（服务端）
     */
    RESPONSE_COMMITTED("responseCommitted"),
    /**
     * 响应失败/异常
     */
    RESPONSE_FAILED("responseFailed"),

    // ==================== 其他事件 ====================
    /**
     * 调度队列开始
     */
    DISPATCHER_QUEUE_START("dispatcherQueueStart"),
    /**
     * 调度队列结束
     */
    DISPATCHER_QUEUE_END("dispatcherQueueEnd"),
    /**
     * 请求取消
     */
    CANCELED("canceled"),
    /**
     * 缓存命中
     */
    CACHE_HIT("cacheHit"),
    /**
     * 缓存未命中
     */
    CACHE_MISS("cacheMiss"),
    /**
     * 条件缓存命中
     */
    CACHE_CONDITIONAL_HIT("cacheConditionalHit"),
    /**
     * 重试决策
     */
    RETRY_DECISION("retryDecision"),
    /**
     * 满足失败
     */
    SATISFACTION_FAILURE("satisfactionFailure"),
    /**
     * 异步处理开始
     */
    ASYNC_START("asyncStart"),
    /**
     * 异步处理完成
     */
    ASYNC_COMPLETE("asyncComplete"),
    /**
     * 请求超时
     */
    TIMEOUT("timeout"),
    ;

    private final String eventName;

    HttpEvent(String eventName) {
        this.eventName = eventName;
    }

    /**
     * 获取开始事件名称
     */
    public String startName() {
        return eventName + "Start";
    }

    /**
     * 获取结束事件名称
     */
    public String endName() {
        return eventName + "End";
    }

    /**
     * 获取异常事件名称
     */
    public String exceptionName() {
        return eventName + "Exception";
    }

    @Override
    public String toString() {
        return eventName;
    }
}
