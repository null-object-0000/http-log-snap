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

    // ==================== 网络地址信息 ====================

    /**
     * 本地地址
     * - 客户端：发起请求的本机 IP:端口
     * - 服务端：服务器 IP:端口
     */
    @Nullable
    private String localAddress;

    /**
     * 远程地址
     * - 客户端：目标服务器 IP:端口
     * - 服务端：请求来源客户端 IP:端口
     */
    @Nullable
    private String remoteAddress;

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
     * 获取规范化后的请求 URL，自动使用当前数据的上下文配置
     * <p>
     * 该方法会自动从 {@link #context} 中读取占位符配置，将 URL 路径中的数字 ID 替换为占位符，
     * 用于降低监控系统中的标签基数，提升查询性能和存储效率。
     * <p>
     * <strong>行为说明：</strong>
     * <ul>
     *   <li>如果上下文中配置了自定义占位符，则按顺序使用自定义占位符替换路径中的数字 ID</li>
     *   <li>如果占位符数量少于数字数量，超出部分使用默认的 {@code {id}} 占位符</li>
     *   <li>如果占位符数量多于数字数量，多余的占位符会被忽略</li>
     *   <li>如果未配置占位符或 context 为 null，所有数字 ID 将替换为默认的 {@code {id}} 占位符</li>
     * </ul>
     * <p>
     * <strong>使用示例：</strong>
     * <pre>{@code
     * // 示例 1：使用默认占位符
     * HttpLogData data = ...; // 假设 URL 为 "https://api.example.com/users/123/orders/456"
     * String normalized = data.getNormalizedRequestUrl();
     * // 结果: "https://api.example.com/users/{id}/orders/{id}"
     *
     * // 示例 2：使用自定义占位符
     * HttpLogContext context = HttpLogContext.builder()
     *     .urlPlaceholders("{showId}", "{ticketId}")
     *     .build();
     * HttpLogData data = HttpLogData.builder()
     *     .context(context)
     *     .request(request) // 假设 URL 为 "https://showopen.maoyan.com/myshow/open/api/info/2434420/1459635/seats"
     *     .build();
     * String normalized = data.getNormalizedRequestUrl();
     * // 结果: "https://showopen.maoyan.com/myshow/open/api/info/{showId}/{ticketId}/seats"
     *
     * // 示例 3：占位符少于数字数量
     * HttpLogContext context = HttpLogContext.builder()
     *     .urlPlaceholders("{showId}")
     *     .build();
     * HttpLogData data = HttpLogData.builder()
     *     .context(context)
     *     .request(request) // 假设 URL 为 "https://api.example.com/info/123/456/789"
     *     .build();
     * String normalized = data.getNormalizedRequestUrl();
     * // 结果: "https://api.example.com/info/{showId}/{id}/{id}"
     *
     * // 示例 4：在监控上报中使用
     * Map<String, Object> tags = new HashMap<>();
     * tags.put("request_url", data.getNormalizedRequestUrl());
     * TrendClient.log("http.client.latency", data.getTotalTimeMs(), tags);
     * }</pre>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>该方法只规范化路径部分，保留协议、主机、端口等信息</li>
     *   <li>URL 的 query 参数部分会被移除（使用 urlWithoutQuery）</li>
     *   <li>如果原始 URL 为 null 或空，返回 null</li>
     * </ul>
     *
     * @return 规范化后的 URL，如果 urlWithoutQuery 为 null 则返回 null
     * @see #context
     * @see HttpLogContext#getUrlPlaceholders()
     * @see Request#getNormalizedUrl(HttpLogContext)
     */
    @Nullable
    public String getNormalizedRequestUrl() {
        return request.getNormalizedUrl(this.context);
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
        /**
         * 无 query 参数的 URL
         */
        private String urlWithoutQuery;
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
            if (isNotBlank(other.getUrlWithoutQuery())) this.urlWithoutQuery = other.getUrlWithoutQuery();
            if (isNotBlank(other.getProtocol())) this.protocol = other.getProtocol();
            if (other.getProxy() != null) this.proxy = other.getProxy();
            if (other.getContentType() != null) this.contentType = other.getContentType();
            if (other.getContentLength() != null) this.contentLength = other.getContentLength();
            if (other.getHeaders() != null) this.headers = other.getHeaders();
            if (other.getIoe() != null) this.ioe = other.getIoe();

            return this;
        }

        /**
         * 从完整 URL 中提取没有 query 参数的部分
         * 如果 URL 为 null 或没有 query 参数，则返回原 URL
         */
        @Nullable
        public static String extractUrlWithoutQuery(@Nullable String url) {
            if (url == null || url.isEmpty()) {
                return url;
            }
            int queryIndex = url.indexOf('?');
            if (queryIndex < 0) {
                return url;
            }
            return url.substring(0, queryIndex);
        }

        /**
         * 获取规范化后的 URL（将路径中的数字 ID 替换为 {id} 占位符）
         * <p>
         * 用于降低监控系统中的标签基数，将动态 URL 聚合为路径模式
         * <p>
         * 示例：
         * <ul>
         *   <li>/api/users/123/orders -> /api/users/{id}/orders</li>
         *   <li>/api/users/123/orders/456 -> /api/users/{id}/orders/{id}</li>
         * </ul>
         *
         * @return 规范化后的 URL，如果 urlWithoutQuery 为 null 则返回 null
         */
        @Nullable
        public String getNormalizedUrl() {
            return UrlNormalizer.normalize(this.urlWithoutQuery);
        }

        /**
         * 获取规范化后的 URL，使用上下文中的自定义占位符
         * <p>
         * 如果上下文中配置了自定义占位符，则使用自定义占位符；否则使用默认的 {id}
         * <p>
         * 示例：
         * <ul>
         *   <li>URL: /api/info/2434420/1459635/seats</li>
         *   <li>占位符: ["{showId}", "{ticketId}"]</li>
         *   <li>结果: /api/info/{showId}/{ticketId}/seats</li>
         * </ul>
         *
         * @param context HTTP 日志上下文，可能包含自定义占位符配置
         * @return 规范化后的 URL，如果 urlWithoutQuery 为 null 则返回 null
         */
        @Nullable
        public String getNormalizedUrl(@Nullable HttpLogContext context) {
            if (context != null) {
                String[] placeholders = context.getUrlPlaceholders();
                if (placeholders != null && placeholders.length > 0) {
                    // 使用自定义占位符
                    return UrlNormalizer.normalize(this.urlWithoutQuery, placeholders);
                }
            }
            // 使用默认的 {id} 占位符
            return getNormalizedUrl();
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
            return (values == null || values.isEmpty()) ? null : values.get(values.size() - 1);
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
