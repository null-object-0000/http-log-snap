package io.github.http.log.snap;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.Proxy;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 日志数据模型
 * 包含请求、响应和耗时统计的完整数据
 * <p>
 * 支持客户端和服务端两种场景，通过 {@link HttpDirection} 区分
 *
 * @author http-logging
 */
@Data
@Builder
public class HttpLogData {

    /**
     * HTTP 方向（客户端/服务端）
     * 默认为客户端
     */
    @Builder.Default
    private HttpDirection direction = HttpDirection.CLIENT;

    /**
     * 日志上下文（追踪信息、接口名称等）
     */
    @Nullable
    private HttpLogContext context;

    /**
     * 请求信息
     */
    @NonNull
    private Request request;

    /**
     * 响应信息（可能为 null，如请求失败）
     */
    @Nullable
    private Response response;

    /**
     * 耗时统计
     */
    @NonNull
    private HttpTiming timing;

    /**
     * 开始时间戳（毫秒）
     */
    private long startTimeMs;

    /**
     * 结束时间戳（毫秒）
     */
    private long endTimeMs;

    // ==================== 处理器/执行器信息 ====================

    /**
     * 处理器/执行器类
     * - 客户端：执行器类型（如 OkHttpHttpRequestExecutor）
     * - 服务端：Handler 类（如 Controller）
     */
    @Nullable
    private Class<?> handlerClass;

    /**
     * 处理器/执行器方法名
     * - 客户端：执行方法（如 execute）
     * - 服务端：Handler 方法名
     */
    @Nullable
    private String handlerMethod;

    /**
     * 处理过程中的异常
     */
    @Nullable
    private Throwable exception;

    /**
     * 总耗时（毫秒）
     */
    public long getTotalTimeMs() {
        return endTimeMs - startTimeMs;
    }

    /**
     * 是否请求失败
     */
    public boolean hasFailed() {
        // 客户端：请求异常或响应异常
        // 服务端：处理异常或响应异常
        return (request.getIoe() != null && response == null) ||
                (response != null && response.getIoe() != null) ||
                (exception != null);
    }

    /**
     * 是否为客户端请求
     */
    public boolean isClient() {
        return direction == HttpDirection.CLIENT;
    }

    /**
     * 是否为服务端请求
     */
    public boolean isServer() {
        return direction == HttpDirection.SERVER;
    }

    /**
     * 获取处理器/执行器完整名称
     * - 客户端：OkHttpHttpRequestExecutor.execute
     * - 服务端：UserController.getUser
     */
    @Nullable
    public String getHandlerName() {
        if (handlerClass == null) {
            return null;
        }
        if (handlerMethod == null) {
            return handlerClass.getSimpleName();
        }
        return handlerClass.getSimpleName() + "." + handlerMethod;
    }

    /**
     * 获取异常（如果有）
     * 优先返回 exception 字段，其次是请求/响应中的 IOException
     */
    @Nullable
    public Throwable getException() {
        if (exception != null) {
            return exception;
        }
        if (request.getIoe() != null) {
            return request.getIoe();
        }
        Response resp = this.response;
        if (resp != null && resp.getIoe() != null) {
            return resp.getIoe();
        }
        return null;
    }

    // ==================== 内部类 ====================

    /**
     * HTTP 请求信息
     */
    @Data
    public static class Request {
        private String method;
        private String url;
        private String protocol;
        private Proxy proxy;

        private ContentType contentType;
        private Long contentLength;
        private String body;

        private boolean duplex;
        private boolean oneShot;

        private Headers headers = new Headers();

        private IOException ioe;

        private long byteCount;

        @NonNull
        public Request merge(@Nullable Request other) {
            if (other == null) return this;

            if (isNotBlank(other.getMethod())) this.method = other.getMethod();
            if (isNotBlank(other.getUrl())) this.url = other.getUrl();
            if (isNotBlank(other.getProtocol())) this.protocol = other.getProtocol();
            if (other.getProxy() != null) this.proxy = other.getProxy();
            if (other.getContentType() != null) this.contentType = other.getContentType();
            if (other.getContentLength() != null) this.contentLength = other.getContentLength();
            if (other.getHeaders() != null) this.headers = other.getHeaders();
            if (other.getIoe() != null) this.ioe = other.getIoe();

            return this;
        }
    }

    /**
     * HTTP 响应信息
     */
    @Data
    public static class Response {
        private String protocol;
        private Integer code;
        private String message;

        private ContentType contentType;
        private Long contentLength;
        private String body;

        private Headers headers = new Headers();

        private IOException ioe;

        private long byteCount;

        @NonNull
        public Response merge(@Nullable Response other) {
            if (other == null) return this;

            if (isNotBlank(other.getProtocol())) this.protocol = other.getProtocol();
            if (other.getCode() != null) this.code = other.getCode();
            if (isNotBlank(other.getMessage())) this.message = other.getMessage();
            if (other.getContentType() != null) this.contentType = other.getContentType();
            if (other.getContentLength() != null) this.contentLength = other.getContentLength();
            if (other.getHeaders() != null) this.headers = other.getHeaders();
            if (other.getIoe() != null) this.ioe = other.getIoe();

            return this;
        }
    }

    /**
     * HTTP 头信息
     */
    public static class Headers {
        @NonNull
        private final Map<String, List<String>> headers;
        @NonNull
        private final String[] names;

        public Headers() {
            this(null);
        }

        public Headers(@Nullable Map<String, List<String>> headers) {
            this.headers = (headers == null) ? new HashMap<>() : headers;
            this.names = this.headers.keySet().toArray(new String[0]);
        }

        public String get(String name) {
            List<String> values = this.headers.get(name);
            return (values == null || values.isEmpty()) ? null : values.getLast();
        }

        public int size() {
            return this.headers.size();
        }

        public String name(int index) {
            return (index < 0 || index >= this.names.length) ? null : this.names[index];
        }

        public String value(int index) {
            String name = this.name(index);
            return (name == null) ? null : this.get(name);
        }

        public Map<String, List<String>> toMap() {
            return new HashMap<>(headers);
        }

        public static Headers of(Map<String, List<String>> headers) {
            return new Headers(headers);
        }
    }

    /**
     * Content-Type 信息
     */
    @Data
    public static class ContentType {
        private String type;
        private String subtype;
        private Charset charset;
        private String mediaType;

        @Override
        public String toString() {
            return this.mediaType;
        }
    }

    // ==================== 内部工具方法 ====================

    /**
     * 判断字符串是否非空白
     */
    private static boolean isNotBlank(String str) {
        return str != null && !str.isBlank();
    }
}
