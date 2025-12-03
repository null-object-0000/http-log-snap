package io.github.http.log.snap.server;

import io.github.http.log.snap.HttpLogContext;
import io.github.http.log.snap.HttpRequestLogger;
import io.github.http.log.snap.formatter.HttpLogFormatter;
import io.github.http.log.snap.output.HttpLogOutput;
import lombok.NonNull;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Predicate;

/**
 * HTTP 服务端适配器接口
 * 用于将不同的 Web 框架与日志记录器集成
 * <p>
 * 实现此接口可以为任意 Web 框架添加日志记录能力，例如：
 * <ul>
 *   <li>Spring MVC - 通过 Filter + Interceptor</li>
 *   <li>Spring WebFlux - 通过 WebFilter</li>
 *   <li>Vert.x - 通过 Handler</li>
 *   <li>Netty - 通过 ChannelHandler</li>
 *   <li>JAX-RS - 通过 ContainerRequestFilter</li>
 *   <li>Servlet - 通过 Filter</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * public class VertxServerAdapter implements HttpServerAdapter {
 *
 *     &#64;Override
 *     public String getFrameworkName() {
 *         return "Vert.x";
 *     }
 *
 *     &#64;Override
 *     public Object install(Object server) {
 *         Router router = (Router) server;
 *         router.route().handler(ctx -> {
 *             HttpRequestLogger logger = createLogger(null);
 *             // ... 记录日志 ...
 *         });
 *         return router;
 *     }
 * }
 * </pre>
 *
 * @author http-logging
 */
public interface HttpServerAdapter {

    /**
     * 获取框架名称
     *
     * @return 框架名称（如 "Spring MVC"、"Vert.x"、"Netty"）
     */
    String getFrameworkName();

    /**
     * 获取框架版本
     *
     * @return 框架版本号
     */
    default String getFrameworkVersion() {
        return "unknown";
    }

    /**
     * 创建日志记录器
     *
     * @param context 日志上下文（可为 null）
     * @return 日志记录器实例
     */
    @NonNull
    default HttpRequestLogger createLogger(@Nullable HttpLogContext context) {
        return HttpRequestLogger.forServer(context);
    }

    /**
     * 将日志记录能力安装到服务端
     * <p>
     * 不同框架有不同的安装方式：
     * <ul>
     *   <li>Spring MVC: 注册 Filter 和 Interceptor</li>
     *   <li>Vert.x: 添加 Route Handler</li>
     *   <li>Netty: 添加 ChannelHandler 到 Pipeline</li>
     * </ul>
     *
     * @param server 服务端实例（如 Router、ChannelPipeline、FilterChain 等）
     * @return 配置后的服务端实例
     * @throws IllegalArgumentException 如果服务端类型不匹配
     */
    Object install(Object server);

    /**
     * 检查是否支持指定的服务端/框架类型
     *
     * @param serverClass 服务端类
     * @return 是否支持
     */
    boolean supports(Class<?> serverClass);

    /**
     * 获取此适配器的优先级（数字越小优先级越高）
     *
     * @return 优先级
     */
    default int getOrder() {
        return 0;
    }

    // ==================== 配置选项 ====================

    /**
     * 获取适配器配置
     *
     * @return 配置对象
     */
    default Config getConfig() {
        return new Config();
    }

    /**
     * 适配器配置类
     */
    class Config {
        /**
         * 是否记录请求体
         */
        private boolean includeRequestBody = true;

        /**
         * 是否记录响应体
         */
        private boolean includeResponseBody = true;

        /**
         * 是否记录请求头
         */
        private boolean includeHeaders = true;

        /**
         * 请求体最大记录长度
         */
        private int maxPayloadLength = 10 * 1024;

        /**
         * 需要脱敏的请求头
         */
        private Set<String> headersToRedact = Set.of("Authorization", "Cookie", "Set-Cookie");

        /**
         * 需要排除的 URL 模式
         */
        private Set<String> excludePatterns = Set.of();

        /**
         * 自定义过滤条件
         */
        private Predicate<Object> shouldLog = request -> true;

        /**
         * 日志格式化器
         */
        private HttpLogFormatter formatter;

        /**
         * 日志输出
         */
        private HttpLogOutput output;

        // Getters and Setters with fluent API

        public boolean isIncludeRequestBody() {
            return includeRequestBody;
        }

        public Config setIncludeRequestBody(boolean includeRequestBody) {
            this.includeRequestBody = includeRequestBody;
            return this;
        }

        public boolean isIncludeResponseBody() {
            return includeResponseBody;
        }

        public Config setIncludeResponseBody(boolean includeResponseBody) {
            this.includeResponseBody = includeResponseBody;
            return this;
        }

        public boolean isIncludeHeaders() {
            return includeHeaders;
        }

        public Config setIncludeHeaders(boolean includeHeaders) {
            this.includeHeaders = includeHeaders;
            return this;
        }

        public int getMaxPayloadLength() {
            return maxPayloadLength;
        }

        public Config setMaxPayloadLength(int maxPayloadLength) {
            this.maxPayloadLength = maxPayloadLength;
            return this;
        }

        public Set<String> getHeadersToRedact() {
            return headersToRedact;
        }

        public Config setHeadersToRedact(Set<String> headersToRedact) {
            this.headersToRedact = headersToRedact;
            return this;
        }

        public Set<String> getExcludePatterns() {
            return excludePatterns;
        }

        public Config setExcludePatterns(Set<String> excludePatterns) {
            this.excludePatterns = excludePatterns;
            return this;
        }

        public Predicate<Object> getShouldLog() {
            return shouldLog;
        }

        public Config setShouldLog(Predicate<Object> shouldLog) {
            this.shouldLog = shouldLog;
            return this;
        }

        public HttpLogFormatter getFormatter() {
            return formatter;
        }

        public Config setFormatter(HttpLogFormatter formatter) {
            this.formatter = formatter;
            return this;
        }

        public HttpLogOutput getOutput() {
            return output;
        }

        public Config setOutput(HttpLogOutput output) {
            this.output = output;
            return this;
        }
    }
}

