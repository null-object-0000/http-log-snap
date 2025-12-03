package io.github.http.log.snap.server;

import io.github.http.log.snap.HttpLogContext;
import io.github.http.log.snap.HttpLogData;
import io.github.http.log.snap.HttpRequestLogger;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP 服务端适配器抽象基类
 * 提供通用的日志记录功能，简化自定义服务端适配器的实现
 * <p>
 * 使用示例：
 * <pre>
 * public class VertxServerAdapter extends AbstractHttpServerAdapter {
 *
 *     &#64;Override
 *     public String getFrameworkName() {
 *         return "Vert.x";
 *     }
 *
 *     &#64;Override
 *     public boolean supports(Class<?> serverClass) {
 *         return "io.vertx.ext.web.Router".equals(serverClass.getName());
 *     }
 *
 *     &#64;Override
 *     public Object install(Object server) {
 *         Router router = (Router) server;
 *         router.route().handler(this::handleRequest);
 *         return router;
 *     }
 *
 *     private void handleRequest(RoutingContext ctx) {
 *         HttpRequestLogger logger = startLogging(null);
 *         try {
 *             recordRequest(logger, buildRequest(ctx));
 *             ctx.next();
 *             recordResponse(logger, buildResponse(ctx));
 *         } catch (Exception e) {
 *             recordException(logger, e);
 *             throw e;
 *         } finally {
 *             finishLogging(logger);
 *         }
 *     }
 * }
 * </pre>
 *
 * @author http-logging
 */
@Slf4j
public abstract class AbstractHttpServerAdapter implements HttpServerAdapter {

    @Getter
    protected final Config config;

    public AbstractHttpServerAdapter() {
        this.config = new Config();
    }

    public AbstractHttpServerAdapter(Config config) {
        this.config = config != null ? config : new Config();
    }

    // ==================== 生命周期方法 ====================

    /**
     * 开始日志记录
     *
     * @param context 日志上下文
     * @return 日志记录器
     */
    protected HttpRequestLogger startLogging(@Nullable HttpLogContext context) {
        HttpRequestLogger logger = createLogger(context);
        
        // 应用配置
        if (config.getFormatter() != null) {
            logger.setFormatter(config.getFormatter());
        }
        if (config.getOutput() != null) {
            logger.setOutput(config.getOutput());
        }
        
        logger.start();
        return logger;
    }

    /**
     * 记录请求信息
     *
     * @param logger  日志记录器
     * @param request 请求数据
     */
    protected void recordRequest(@NonNull HttpRequestLogger logger, @NonNull HttpLogData.Request request) {
        logger.recordRequestReceived(request);
        
        // 如果有请求体，记录请求体
        if (config.isIncludeRequestBody() && request.getBody() != null) {
            logger.recordRequestBodyStart();
            String body = truncateBody(request.getBody(), config.getMaxPayloadLength());
            logger.recordRequestBodyEnd(body, request.getByteCount());
        }
    }

    /**
     * 记录 Handler 开始
     *
     * @param logger       日志记录器
     * @param handlerClass Handler 类
     * @param methodName   方法名
     */
    protected void recordHandlerStart(@NonNull HttpRequestLogger logger,
                                       @Nullable Class<?> handlerClass,
                                       @Nullable String methodName) {
        if (handlerClass != null && methodName != null) {
            logger.recordHandlerStart(handlerClass, methodName);
        } else {
            logger.recordHandlerStart();
        }
    }

    /**
     * 记录 Handler 结束
     *
     * @param logger 日志记录器
     */
    protected void recordHandlerEnd(@NonNull HttpRequestLogger logger) {
        logger.recordHandlerEnd();
    }

    /**
     * 记录响应信息
     *
     * @param logger   日志记录器
     * @param response 响应数据
     */
    protected void recordResponse(@NonNull HttpRequestLogger logger, @NonNull HttpLogData.Response response) {
        logger.recordResponseBuild(response);
        
        // 如果有响应体，记录响应体
        if (config.isIncludeResponseBody() && response.getBody() != null) {
            logger.recordResponseBodyStart();
            String body = truncateBody(response.getBody(), config.getMaxPayloadLength());
            logger.recordResponseBodyEnd(body, response.getByteCount());
        }
        
        logger.recordResponseCommitted();
    }

    /**
     * 记录异常
     *
     * @param logger    日志记录器
     * @param exception 异常
     */
    protected void recordException(@NonNull HttpRequestLogger logger, @NonNull Throwable exception) {
        logger.recordHandlerException(exception);
    }

    /**
     * 完成日志记录并输出
     *
     * @param logger 日志记录器
     */
    protected void finishLogging(@NonNull HttpRequestLogger logger) {
        logger.end();
        logger.log();
    }

    // ==================== 判断方法 ====================

    /**
     * 判断是否需要记录该请求的日志
     *
     * @param uri 请求 URI
     * @return 是否需要记录
     */
    protected boolean shouldLog(String uri) {
        // 检查排除模式
        for (String pattern : config.getExcludePatterns()) {
            if (matchesPattern(uri, pattern)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断是否需要记录该请求的日志（使用自定义条件）
     *
     * @param request 请求对象
     * @return 是否需要记录
     */
    protected boolean shouldLog(Object request) {
        return config.getShouldLog().test(request);
    }

    // ==================== 工具方法 ====================

    /**
     * 构建请求头
     *
     * @param headerNames  头名称枚举
     * @param headerGetter 获取头值的函数
     * @return 请求头对象
     */
    protected HttpLogData.Headers buildHeaders(Enumeration<String> headerNames,
                                                java.util.function.BiFunction<String, Object, List<String>> headerGetter) {
        if (!config.isIncludeHeaders()) {
            return new HttpLogData.Headers();
        }
        
        Map<String, List<String>> headers = new LinkedHashMap<>();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            List<String> values = headerGetter.apply(name, null);
            headers.put(name, values);
        }
        return HttpLogData.Headers.of(headers);
    }

    /**
     * 构建响应头
     *
     * @param headerNames  头名称集合
     * @param headerGetter 获取头值的函数
     * @return 响应头对象
     */
    protected HttpLogData.Headers buildHeaders(Collection<String> headerNames,
                                                java.util.function.Function<String, Collection<String>> headerGetter) {
        if (!config.isIncludeHeaders()) {
            return new HttpLogData.Headers();
        }
        
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : headerNames) {
            Collection<String> values = headerGetter.apply(name);
            headers.put(name, new ArrayList<>(values));
        }
        return HttpLogData.Headers.of(headers);
    }

    /**
     * 解析 Content-Type
     *
     * @param contentTypeHeader Content-Type 头值
     * @return ContentType 对象
     */
    @Nullable
    protected HttpLogData.ContentType parseContentType(String contentTypeHeader) {
        if (contentTypeHeader == null || contentTypeHeader.isBlank()) {
            return null;
        }

        HttpLogData.ContentType contentType = new HttpLogData.ContentType();
        contentType.setMediaType(contentTypeHeader);

        // 解析 type/subtype
        String[] parts = contentTypeHeader.split(";")[0].trim().split("/");
        if (parts.length >= 2) {
            contentType.setType(parts[0].trim());
            contentType.setSubtype(parts[1].trim());
        }

        // 解析 charset
        if (contentTypeHeader.contains("charset=")) {
            String charset = contentTypeHeader.substring(contentTypeHeader.indexOf("charset=") + 8);
            if (charset.contains(";")) {
                charset = charset.substring(0, charset.indexOf(";"));
            }
            try {
                contentType.setCharset(Charset.forName(charset.trim()));
            } catch (Exception ignored) {
            }
        }

        return contentType;
    }

    /**
     * 截断请求体/响应体
     *
     * @param body      原始内容
     * @param maxLength 最大长度
     * @return 截断后的内容
     */
    protected String truncateBody(String body, int maxLength) {
        if (body == null || body.length() <= maxLength) {
            return body;
        }
        return body.substring(0, maxLength) + "... [truncated " + (body.length() - maxLength) + " chars]";
    }

    /**
     * 截断字节数组
     *
     * @param body      原始字节数组
     * @param charset   字符集
     * @param maxLength 最大长度
     * @return 截断后的字符串
     */
    protected String truncateBody(byte[] body, Charset charset, int maxLength) {
        if (body == null || body.length == 0) {
            return "";
        }
        
        Charset cs = charset != null ? charset : StandardCharsets.UTF_8;
        
        if (body.length <= maxLength) {
            return new String(body, cs);
        }
        
        return new String(body, 0, maxLength, cs) +
                "... [truncated " + (body.length - maxLength) + " bytes]";
    }

    /**
     * 获取 HTTP 状态消息
     *
     * @param status 状态码
     * @return 状态消息
     */
    protected String getStatusMessage(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "";
        };
    }

    /**
     * 匹配 URL 模式（简单的 ant 风格）
     *
     * @param uri     请求 URI
     * @param pattern 模式
     * @return 是否匹配
     */
    protected boolean matchesPattern(String uri, String pattern) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return uri.startsWith(prefix);
        } else if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return uri.startsWith(prefix) && !uri.substring(prefix.length()).contains("/");
        } else if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return uri.matches(regex);
        } else {
            return uri.equals(pattern);
        }
    }
}

