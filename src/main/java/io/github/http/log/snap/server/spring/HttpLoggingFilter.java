package io.github.http.log.snap.server.spring;

import io.github.http.log.snap.HttpLogData;
import io.github.http.log.snap.HttpRequestLogger;
import io.github.http.log.snap.formatter.HttpLogFormatter;
import io.github.http.log.snap.formatter.JsonHttpLogFormatter;
import io.github.http.log.snap.formatter.TextHttpLogFormatter;
import io.github.http.log.snap.server.spring.rule.ExcludeRule;
import io.github.http.log.snap.server.spring.rule.RuleBase;
import io.github.http.log.snap.server.spring.rule.SseRule;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

/**
 * Spring HTTP 日志记录 Filter
 * 记录 HTTP 请求和响应的完整报文及各阶段耗时
 * <p>
 * 使用示例：
 * <pre>
 * &#64;Bean
 * public FilterRegistrationBean&lt;HttpLoggingFilter&gt; httpLoggingFilter() {
 *     FilterRegistrationBean&lt;HttpLoggingFilter&gt; registration = new FilterRegistrationBean&lt;&gt;();
 *     registration.setFilter(new HttpLoggingFilter());
 *     registration.addUrlPatterns("/*");
 *     registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
 *     return registration;
 * }
 * </pre>
 *
 * @author http-logging
 */
@Slf4j
public class HttpLoggingFilter extends OncePerRequestFilter {

    /**
     * 默认不记录请求体的最大长度（-1 表示无限制）
     */
    private static final int DEFAULT_MAX_PAYLOAD_LENGTH = -1;

    /**
     * 默认需要脱敏的请求头
     */
    private static final Set<String> DEFAULT_HEADERS_TO_REDACT = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "Authorization", "Cookie", "Set-Cookie", "X-Auth-Token"
    )));

    /**
     * 是否记录请求体
     */
    @Getter
    @Setter
    private boolean includeRequestBody = true;

    /**
     * 是否记录响应体
     */
    @Getter
    @Setter
    private boolean includeResponseBody = true;

    /**
     * 是否记录请求头
     */
    @Getter
    @Setter
    private boolean includeHeaders = true;

    /**
     * 请求体最大记录长度（超过则截断）
     */
    @Getter
    @Setter
    private int maxPayloadLength = DEFAULT_MAX_PAYLOAD_LENGTH;

    /**
     * 需要脱敏的请求头名称
     */
    @Getter
    private Set<String> headersToRedact = new HashSet<>(DEFAULT_HEADERS_TO_REDACT);

    /**
     * 需要脱敏的查询参数名称
     */
    @Getter
    private Set<String> queryParamsToRedact = new HashSet<>();

    /**
     * 日志格式化器
     */
    @Getter
    @Setter
    private HttpLogFormatter formatter;

    /**
     * 需要排除的 URL 模式（支持 ant 风格）
     */
    @Getter
    private final List<String> excludePatterns = new ArrayList<>();

    /**
     * 条件排除规则（支持 URL + body/header 条件判断）
     */
    @Getter
    private final List<ExcludeRule> excludeRules = new ArrayList<>();

    /**
     * SSE 响应规则（支持 URL + body/header 条件判断）
     * 如果匹配规则，则只记录请求报文，不记录响应报文
     */
    @Getter
    private final List<SseRule> sseRules = new ArrayList<>();

    /**
     * 自定义过滤条件（返回 true 表示需要记录日志）
     */
    @Getter
    @Setter
    private Predicate<HttpServletRequest> shouldLog = request -> true;

    /**
     * 日志定制器（可选）
     */
    @Getter
    @Setter
    private HttpLogCustomizer customizer;

    /**
     * 设置需要脱敏的请求头
     */
    public void setHeadersToRedact(Set<String> headers) {
        this.headersToRedact = headers != null ? new HashSet<>(headers) : new HashSet<>();
    }

    /**
     * 添加需要脱敏的请求头
     */
    public void addHeaderToRedact(String headerName) {
        this.headersToRedact.add(headerName);
    }

    /**
     * 设置需要脱敏的查询参数
     */
    public void setQueryParamsToRedact(Set<String> params) {
        this.queryParamsToRedact = params != null ? new HashSet<>(params) : new HashSet<>();
    }

    /**
     * 添加需要脱敏的查询参数
     */
    public void addQueryParamToRedact(String paramName) {
        this.queryParamsToRedact.add(paramName);
    }

    /**
     * 添加需要排除的 URL 模式
     */
    public void addExcludePattern(String pattern) {
        this.excludePatterns.add(pattern);
    }

    /**
     * 添加条件排除规则
     */
    public void addExcludeRule(ExcludeRule rule) {
        if (rule != null) {
            this.excludeRules.add(rule);
        }
    }

    /**
     * 设置条件排除规则列表
     */
    public void setExcludeRules(List<ExcludeRule> rules) {
        this.excludeRules.clear();
        if (rules != null) {
            this.excludeRules.addAll(rules);
        }
    }

    /**
     * 添加 SSE 响应规则
     */
    public void addSseRule(SseRule rule) {
        if (rule != null) {
            this.sseRules.add(rule);
        }
    }

    /**
     * 设置 SSE 响应规则列表
     */
    public void setSseRules(List<SseRule> rules) {
        this.sseRules.clear();
        if (rules != null) {
            this.sseRules.addAll(rules);
        }
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // 检查是否需要记录日志（基础 URL 模式检查）
        if (!shouldLog(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 包装请求和响应以支持多次读取（需要在检查 body 条件之前包装）
        CachedBodyHttpServletRequest wrappedRequest;
        try {
            wrappedRequest = new CachedBodyHttpServletRequest(request);
        } catch (IOException e) {
            // 如果包装失败，直接放行
            filterChain.doFilter(request, response);
            return;
        }
        CachedBodyHttpServletResponse wrappedResponse = new CachedBodyHttpServletResponse(response);

        // 检查条件排除规则（需要 body/header 条件时）
        if (shouldExcludeByCondition(wrappedRequest)) {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            return;
        }

        // 创建日志记录器（服务端模式）
        HttpRequestLogger logger = HttpRequestLogger.forServer();
        HttpRequestLoggerHolder.set(logger);

        // 记录客户端和服务器地址
        logger.setRemoteAddress(request.getRemoteAddr() + ":" + request.getRemotePort());
        logger.setLocalAddress(request.getLocalAddr() + ":" + request.getLocalPort());

        // 调用定制器（如果有）
        if (customizer != null) {
            customizer.customize(logger, request);
        }

        try {
            // 开始记录
            logger.start();

            // 记录请求信息
            recordRequest(logger, wrappedRequest);

            // 执行过滤器链
            // 注：Handler 的 start/end/exception 由 HttpLoggingHandlerInterceptor 记录
                filterChain.doFilter(wrappedRequest, wrappedResponse);

            } catch (Exception e) {
            // 记录异常
                if (logger.getException() == null) {
                    logger.recordHandlerException(e);
                }
                throw e;
        } finally {
            try {
                // 检查是否匹配 SSE 规则（只记录请求，不记录响应）
                boolean shouldSkipResponse = shouldSkipResponseBySseRule(wrappedRequest, wrappedResponse);
                
                if (!shouldSkipResponse) {
                    // 确保响应数据已完全写入缓存
                    try {
                        wrappedResponse.ensureFlushed();
                    } catch (IOException e) {
                        // 忽略 flush 异常，继续记录
                    }
                    
                    // 记录响应信息（无论成功还是异常都记录）
                    recordResponse(logger, wrappedResponse);
                } else {
                    // 匹配 SSE 规则，只记录请求，不记录响应
                    // 记录一个简化的响应信息（仅状态码）
                    recordSseResponse(logger, wrappedResponse);
                }

                // 结束记录
                logger.end();

                // 输出日志
                outputLog(logger);
            } finally {
                HttpRequestLoggerHolder.clear();
            }
        }
    }

    /**
     * 判断是否需要记录该请求的日志
     */
    protected boolean shouldLog(HttpServletRequest request) {
        // 检查排除模式
        String uri = request.getRequestURI();
        for (String pattern : excludePatterns) {
            if (matchesPattern(uri, pattern)) {
                return false;
            }
        }

        // 检查自定义条件
        return shouldLog.test(request);
    }

    /**
     * 检查是否需要根据条件排除规则排除该请求
     * 此方法在 request 被包装后调用，可以访问 body 和 header
     */
    protected boolean shouldExcludeByCondition(CachedBodyHttpServletRequest request) {
        String uri = request.getRequestURI();
        
        for (ExcludeRule rule : excludeRules) {
            if (rule.getUrlPattern() == null) {
                continue;
            }
            
            // 首先检查 URL 是否匹配
            if (!matchesPattern(uri, rule.getUrlPattern())) {
                continue;
            }
            
            // URL 匹配后，检查 body 条件
            if (rule.getBodyCondition() != null) {
                try {
                    String body = request.getCachedBodyString();
                    if (rule.getBodyCondition().test(body)) {
                        return true; // 排除
                    }
                } catch (IOException e) {
                    // 读取 body 失败，跳过 body 条件检查
                }
            }
            
            // 检查 header 条件
            if (rule.getHeaderCondition() != null) {
                Enumeration<String> headerNames = request.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    String headerValue = request.getHeader(headerName);
                    RuleBase.HeaderCondition condition = 
                        new RuleBase.HeaderCondition(headerName, headerValue);
                    if (rule.getHeaderCondition().test(condition)) {
                        return true; // 排除
                    }
                }
            }
            
            // 如果 URL 匹配但没有设置 body 和 header 条件，则排除（保持向后兼容）
            if (rule.getBodyCondition() == null && rule.getHeaderCondition() == null) {
                return true; // 排除
            }
        }
        
        return false;
    }

    /**
     * 检查是否需要根据 SSE 规则跳过响应记录
     * 此方法在 request 和 response 都被包装后调用
     */
    protected boolean shouldSkipResponseBySseRule(CachedBodyHttpServletRequest request, CachedBodyHttpServletResponse response) {
        String uri = request.getRequestURI();
        
        for (SseRule rule : sseRules) {
            if (rule.getUrlPattern() == null) {
                continue;
            }
            
            // 首先检查 URL 是否匹配
            if (!matchesPattern(uri, rule.getUrlPattern())) {
                continue;
            }
            
            // URL 匹配后，检查 body 条件
            if (rule.getBodyCondition() != null) {
                try {
                    String body = request.getCachedBodyString();
                    if (rule.getBodyCondition().test(body)) {
                        return true; // 跳过响应记录
                    }
                } catch (IOException e) {
                    // 读取 body 失败，跳过 body 条件检查
                }
            }
            
            // 检查 header 条件
            if (rule.getHeaderCondition() != null) {
                Enumeration<String> headerNames = request.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    String headerValue = request.getHeader(headerName);
                    RuleBase.HeaderCondition condition = 
                        new RuleBase.HeaderCondition(headerName, headerValue);
                    if (rule.getHeaderCondition().test(condition)) {
                        return true; // 跳过响应记录
                    }
                }
            }
            
            // 如果 URL 匹配但没有设置 body 和 header 条件，则跳过响应记录
            if (rule.getBodyCondition() == null && rule.getHeaderCondition() == null) {
                return true; // 跳过响应记录
            }
        }
        
        return false;
    }

    /**
     * 记录 SSE 响应信息（仅记录状态码，不记录响应体）
     */
    protected void recordSseResponse(HttpRequestLogger logger, CachedBodyHttpServletResponse response) {
        HttpLogData.Response responseData = new HttpLogData.Response();
        
        // 基本信息
        responseData.setCode(response.getStatus());
        responseData.setMessage(getStatusMessage(response.getStatus()));
        
        // 不记录响应头、响应体等信息
        logger.recordResponseBuild(responseData);
        logger.recordResponseCommitted();
    }

    /**
     * 记录请求信息
     */
    protected void recordRequest(HttpRequestLogger logger, CachedBodyHttpServletRequest request) {
        HttpLogData.Request requestData = new HttpLogData.Request();

        // 基本信息
        requestData.setMethod(request.getMethod());
        String url = buildRequestUrl(request);
        requestData.setUrl(url);
        requestData.setUrlWithoutQuery(HttpLogData.Request.extractUrlWithoutQuery(url));
        requestData.setProtocol(request.getProtocol());

        // 请求头
        if (includeHeaders) {
            requestData.setHeaders(buildHeaders(request));
        }

        // Content-Type
        if (request.getContentType() != null) {
            requestData.setContentType(parseContentType(request.getContentType()));
        }

        // Content-Length
        if (request.getContentLength() > 0) {
            requestData.setContentLength((long) request.getContentLength());
        }

        // 记录请求已接收
        logger.recordRequestReceived(requestData);

        // 请求体
        if (includeRequestBody && hasRequestBody(request)) {
            logger.recordRequestBodyStart();
            try {
                byte[] body = request.getCachedBody();
                String bodyString = truncateBody(body, getCharset(request));
                logger.recordRequestBodyEnd(bodyString, body.length);
            } catch (IOException e) {
                logger.recordRequestException(e);
            }
        }
    }

    /**
     * 记录响应信息
     */
    protected void recordResponse(HttpRequestLogger logger, CachedBodyHttpServletResponse response) {
        HttpLogData.Response responseData = new HttpLogData.Response();

        // 基本信息
        responseData.setCode(response.getStatus());
        responseData.setMessage(getStatusMessage(response.getStatus()));

        // 响应头
        if (includeHeaders) {
            responseData.setHeaders(buildHeaders(response));
        }

        // Content-Type
        if (response.getContentType() != null) {
            responseData.setContentType(parseContentType(response.getContentType()));
        }

        // Content-Length
        String contentLength = response.getHeader("Content-Length");
        if (contentLength != null) {
            try {
                responseData.setContentLength(Long.parseLong(contentLength));
            } catch (NumberFormatException ignored) {
            }
        }

        // 记录响应构建
        logger.recordResponseBuild(responseData);

        // 响应体
        if (includeResponseBody) {
            logger.recordResponseBodyStart();
            byte[] body = response.getCachedBody();
            String bodyString = truncateBody(body, getCharset(response));
            logger.recordResponseBodyEnd(bodyString, body != null ? body.length : 0);
        }

        logger.recordResponseCommitted();
    }

    /**
     * 输出日志
     */
    protected void outputLog(HttpRequestLogger logger) {
        if (formatter != null) {
            logger.setFormatter(formatter);
        }
        HttpLogFormatter logFormatter = logger.getFormatter();
        if (logFormatter != null) {
            if (!headersToRedact.isEmpty()) {
                logFormatter.redactHeaders(headersToRedact);
            }
            // 设置查询参数脱敏
            if (!queryParamsToRedact.isEmpty()) {
                if (logFormatter instanceof TextHttpLogFormatter) {
                    ((TextHttpLogFormatter) logFormatter).redactQueryParams(queryParamsToRedact);
                } else if (logFormatter instanceof JsonHttpLogFormatter) {
                    ((JsonHttpLogFormatter) logFormatter).redactQueryParams(queryParamsToRedact);
                }
            }
        }
        logger.log();
    }

    // ==================== 辅助方法 ====================

    private String buildRequestUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder();
        url.append(request.getRequestURL());
        String queryString = request.getQueryString();
        if (queryString != null) {
            url.append("?").append(queryString);
        }
        return url.toString();
    }

    private HttpLogData.Headers buildHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            List<String> values = Collections.list(request.getHeaders(name));
            headers.put(name, values);
        }
        return HttpLogData.Headers.of(headers);
    }

    private HttpLogData.Headers buildHeaders(HttpServletResponse response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            List<String> values = new ArrayList<>(response.getHeaders(name));
            headers.put(name, values);
        }
        return HttpLogData.Headers.of(headers);
    }

    @Nullable
    private HttpLogData.ContentType parseContentType(String contentTypeHeader) {
        if (contentTypeHeader == null) {
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

    private boolean hasRequestBody(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equalsIgnoreCase(method) ||
                "PUT".equalsIgnoreCase(method) ||
                "PATCH".equalsIgnoreCase(method);
    }

    private String truncateBody(byte[] body, Charset charset) {
        if (body == null || body.length == 0) {
            return "";
        }

        // maxPayloadLength <= 0 表示无限制
        if (maxPayloadLength <= 0 || body.length <= maxPayloadLength) {
            return new String(body, charset);
        }

        return new String(body, 0, maxPayloadLength, charset) +
                "... [truncated " + (body.length - maxPayloadLength) + " bytes]";
    }

    private Charset getCharset(HttpServletRequest request) {
        String encoding = request.getCharacterEncoding();
        if (encoding != null) {
            try {
                return Charset.forName(encoding);
            } catch (Exception ignored) {
            }
        }
        return StandardCharsets.UTF_8;
    }

    private Charset getCharset(HttpServletResponse response) {
        // JSON 类型强制使用 UTF-8（RFC 8259）
        String contentType = response.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            return StandardCharsets.UTF_8;
        }
        
        String encoding = response.getCharacterEncoding();
        if (encoding != null && !encoding.equalsIgnoreCase("ISO-8859-1")) {
            // 忽略 Tomcat 默认的 ISO-8859-1，使用 UTF-8
            try {
                return Charset.forName(encoding);
            } catch (Exception ignored) {
            }
        }
        return StandardCharsets.UTF_8;
    }

    private String getStatusMessage(int status) {
        switch (status) {
            case 200:
                return "OK";
            case 201:
                return "Created";
            case 204:
                return "No Content";
            case 301:
                return "Moved Permanently";
            case 302:
                return "Found";
            case 304:
                return "Not Modified";
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 405:
                return "Method Not Allowed";
            case 500:
                return "Internal Server Error";
            case 502:
                return "Bad Gateway";
            case 503:
                return "Service Unavailable";
            default:
                return "";
        }
    }

    private boolean matchesPattern(String uri, String pattern) {
        // 简单的 ant 风格匹配
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

