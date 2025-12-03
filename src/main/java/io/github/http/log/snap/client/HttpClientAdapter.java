package io.github.http.log.snap.client;

import io.github.http.log.snap.HttpLogContext;
import io.github.http.log.snap.HttpRequestLogger;
import lombok.NonNull;

import javax.annotation.Nullable;

/**
 * HTTP 客户端适配器接口
 * 用于将不同的 HTTP 客户端库与日志记录器集成
 * <p>
 * 实现此接口可以为任意 HTTP 客户端添加日志记录能力，例如：
 * <ul>
 *   <li>OkHttp - 通过 EventListener</li>
 *   <li>Apache HttpClient - 通过 Interceptor</li>
 *   <li>Java 11+ HttpClient - 通过包装器</li>
 *   <li>Retrofit - 通过 OkHttp 适配器</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * public class MyHttpClientAdapter implements HttpClientAdapter {
 *
 *     &#64;Override
 *     public String getClientName() {
 *         return "MyHttpClient";
 *     }
 *
 *     &#64;Override
 *     public HttpRequestLogger createLogger(HttpLogContext context) {
 *         return HttpRequestLogger.forClient(context);
 *     }
 *
 *     &#64;Override
 *     public void install(Object client) {
 *         // 将日志记录能力安装到客户端
 *     }
 * }
 * </pre>
 *
 * @author http-logging
 */
public interface HttpClientAdapter {

    /**
     * 获取客户端名称
     *
     * @return 客户端名称（如 "OkHttp"、"Apache HttpClient"）
     */
    String getClientName();

    /**
     * 获取客户端版本
     *
     * @return 客户端版本号
     */
    default String getClientVersion() {
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
        return HttpRequestLogger.forClient(context);
    }

    /**
     * 将日志记录能力安装到 HTTP 客户端
     * <p>
     * 不同客户端有不同的安装方式：
     * <ul>
     *   <li>OkHttp: client.newBuilder().eventListenerFactory(...)</li>
     *   <li>Apache: client.addInterceptorFirst(...)</li>
     * </ul>
     *
     * @param client HTTP 客户端实例
     * @return 配置后的客户端实例（可能是新实例或原实例）
     * @throws IllegalArgumentException 如果客户端类型不匹配
     */
    Object install(Object client);

    /**
     * 检查是否支持指定的客户端类型
     *
     * @param clientClass 客户端类
     * @return 是否支持
     */
    boolean supports(Class<?> clientClass);

    /**
     * 获取此适配器的优先级（数字越小优先级越高）
     *
     * @return 优先级
     */
    default int getOrder() {
        return 0;
    }
}

