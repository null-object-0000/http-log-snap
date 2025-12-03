package io.github.http.log.snap.server.spring;

import io.github.http.log.snap.HttpRequestLogger;
import jakarta.servlet.http.HttpServletRequest;

/**
 * HTTP 日志定制器
 * <p>
 * 实现此接口可以在请求处理前自定义 Logger，例如：
 * - 设置接口名称
 * - 添加扩展信息（traceId、userId 等）
 * - 根据请求路径设置不同的输出器或格式化器
 * <p>
 * 使用示例：
 * <pre>{@code
 * @Component
 * public class MyHttpLogCustomizer implements HttpLogCustomizer {
 *     @Override
 *     public void customize(HttpRequestLogger logger, HttpServletRequest request) {
 *         // 添加 traceId
 *         String traceId = request.getHeader("X-Trace-Id");
 *         if (traceId != null) {
 *             logger.putExtra("traceId", traceId);
 *         }
 *         
 *         // 添加用户信息
 *         Object userId = request.getAttribute("userId");
 *         if (userId != null) {
 *             logger.putExtra("userId", userId);
 *         }
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface HttpLogCustomizer {

    /**
     * 自定义 Logger
     * <p>
     * 在请求处理开始时调用，可以在此方法中：
     * <ul>
     *   <li>调用 {@link HttpRequestLogger#setInterfaceName(String)} 设置接口名称</li>
     *   <li>调用 {@link HttpRequestLogger#putExtra(String, Object)} 添加扩展信息</li>
     *   <li>调用 {@link HttpRequestLogger#setFormatter} 设置格式化器</li>
     *   <li>调用 {@link HttpRequestLogger#setOutput} 设置输出目标</li>
     * </ul>
     *
     * @param logger  当前请求的日志记录器
     * @param request HTTP 请求对象
     */
    void customize(HttpRequestLogger logger, HttpServletRequest request);
}

