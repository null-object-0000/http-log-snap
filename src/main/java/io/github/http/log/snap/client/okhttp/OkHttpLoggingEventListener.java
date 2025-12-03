package io.github.http.log.snap.client.okhttp;

import io.github.http.log.snap.HttpEvent;
import io.github.http.log.snap.HttpLogContext;
import io.github.http.log.snap.HttpLogData;
import io.github.http.log.snap.HttpRequestLogger;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Dispatcher;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OkHttp 日志事件监听器
 * 监听 HTTP 请求各阶段事件，用于记录请求日志和计算耗时
 *
 * @author http-logging
 */
@Slf4j
public class OkHttpLoggingEventListener extends EventListener {
    @NonNull
    private final HttpRequestLogger logger;

    /**
     * 事件监听器工厂
     * 从请求的 tag 中获取 HttpLogContext 或 HttpRequestLogger
     */
    public static final Factory FACTORY = call -> {
        HttpLogContext context = call.request().tag(HttpLogContext.class);
        HttpRequestLogger logger = call.request().tag(HttpRequestLogger.class);
        return new OkHttpLoggingEventListener(logger == null ? new HttpRequestLogger(context) : logger);
    };

    private OkHttpLoggingEventListener(@NonNull HttpRequestLogger logger) {
        this.logger = logger;
    }

    // ==================== 核心生命周期事件 ====================

    @Override
    public void callStart(@NonNull Call call) {
        logger.recordRequest(buildRequest(call.request()), HttpEvent.CALL_START);
    }

    @Override
    public void callEnd(@NonNull Call call) {
        logger.record(HttpEvent.CALL_END);
    }

    @Override
    public void callFailed(@NonNull Call call, @NonNull IOException ioe) {
        logger.recordResponseException(ioe, HttpEvent.CALL_FAILED);
    }

    // ==================== 代理选择事件 ====================

    @Override
    public void proxySelectStart(@NonNull Call call, @NonNull HttpUrl url) {
        logger.recordRequest(buildRequest(call.request()), HttpEvent.PROXY_SELECT_START);
    }

    @Override
    public void proxySelectEnd(@NonNull Call call, @NonNull HttpUrl url, @NonNull List<Proxy> proxies) {
        logger.recordRequest(buildRequest(call.request()), HttpEvent.PROXY_SELECT_END);
    }

    // ==================== DNS 解析事件 ====================

    @Override
    public void dnsStart(@NonNull Call call, @NonNull String domainName) {
        logger.recordRequest(buildRequest(call.request()), HttpEvent.DNS_START);
    }

    @Override
    public void dnsEnd(@NonNull Call call, @NonNull String domainName, @NonNull List<InetAddress> inetAddressList) {
        logger.recordRequest(buildRequest(call.request()), HttpEvent.DNS_END);
    }

    // ==================== 连接事件 ====================

    @Override
    public void connectStart(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy) {
        logger.recordRequest(buildRequest(call.request(), proxy, null), HttpEvent.CONNECT_START);
    }

    @Override
    public void connectEnd(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy,
                           Protocol protocol) {
        logger.recordRequest(buildRequest(call.request(), proxy, protocol), HttpEvent.CONNECT_END);
    }

    @Override
    public void connectFailed(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy,
                              @Nullable Protocol protocol, @NonNull IOException ioe) {
        // 注意：不要在 connectFailed 时设置 IOException
        // Happy Eyeballs (RFC 6555) 会同时尝试 IPv6 和 IPv4，某个连接失败是正常的
        // 只有 callFailed 才代表真正的请求失败
        logger.recordRequest(buildRequest(call.request(), proxy, protocol), HttpEvent.CONNECT_FAILED);
    }

    @Override
    public void secureConnectStart(@NonNull Call call) {
        logger.recordRequest(buildRequest(call.request()), HttpEvent.SECURE_CONNECT_START);
    }

    @Override
    public void secureConnectEnd(@NonNull Call call, @Nullable Handshake handshake) {
        logger.recordRequest(buildRequest(call.request()), HttpEvent.SECURE_CONNECT_END);
    }

    @Override
    public void connectionAcquired(@NonNull Call call, @NonNull Connection connection) {
        logger.recordRequest(buildRequest(call.request(), connection), HttpEvent.CONNECTION_ACQUIRED);

        // 记录连接的本地和远程地址
        try {
            java.net.Socket socket = connection.socket();
            if (socket.getLocalSocketAddress() instanceof InetSocketAddress local) {
                logger.setLocalAddress(local.getAddress().getHostAddress() + ":" + local.getPort());
            }
            if (socket.getRemoteSocketAddress() instanceof InetSocketAddress remote) {
                logger.setRemoteAddress(remote.getAddress().getHostAddress() + ":" + remote.getPort());
            }
        } catch (Exception ignored) {
            // 忽略获取地址的异常
        }
    }

    @Override
    public void connectionReleased(@NonNull Call call, @NonNull Connection connection) {
        logger.record(HttpEvent.CONNECTION_RELEASED);
    }

    // ==================== 请求事件 ====================

    @Override
    public void requestHeadersStart(@NonNull Call call) {
        logger.recordRequest(buildRequest(call.request()), HttpEvent.REQUEST_HEADERS_START);
    }

    @Override
    public void requestHeadersEnd(@NonNull Call call, @NonNull Request request) {
        logger.recordRequest(buildRequest(request), HttpEvent.REQUEST_HEADERS_END);
    }

    @Override
    public void requestBodyStart(@NonNull Call call) {
        logger.recordRequest(buildRequest(call.request()), HttpEvent.REQUEST_BODY_START);
    }

    @Override
    public void requestBodyEnd(@NonNull Call call, long byteCount) {
        logger.recordRequest(buildRequest(call.request()), HttpEvent.REQUEST_BODY_END, byteCount);
    }

    @Override
    public void requestFailed(@NonNull Call call, @NonNull IOException ioe) {
        logger.recordRequestException(ioe, HttpEvent.REQUEST_FAILED);
    }

    // ==================== 响应事件 ====================

    @Override
    public void responseHeadersStart(@NonNull Call call) {
        logger.record(HttpEvent.RESPONSE_HEADERS_START);
    }

    @Override
    public void responseHeadersEnd(@NonNull Call call, @NonNull Response response) {
        logger.recordResponse(buildResponse(response), HttpEvent.RESPONSE_HEADERS_END);
    }

    @Override
    public void responseBodyStart(@NonNull Call call) {
        logger.record(HttpEvent.RESPONSE_BODY_START);
    }

    @Override
    public void responseBodyEnd(@NonNull Call call, long byteCount) {
        logger.recordResponse(HttpEvent.RESPONSE_BODY_END, byteCount);
    }

    @Override
    public void responseFailed(@NonNull Call call, @NonNull IOException ioe) {
        logger.recordResponseException(ioe, HttpEvent.RESPONSE_FAILED);
    }

    @Override
    public void followUpDecision(@NonNull Call call, @NonNull Response networkResponse, Request nextRequest) {
        // 不记录
    }

    // ==================== 其他事件（简单记录） ====================

    @Override
    public void dispatcherQueueStart(@NonNull Call call, @NonNull Dispatcher dispatcher) {
        logger.record(HttpEvent.DISPATCHER_QUEUE_START);
    }

    @Override
    public void dispatcherQueueEnd(@NonNull Call call, @NonNull Dispatcher dispatcher) {
        logger.record(HttpEvent.DISPATCHER_QUEUE_END);
    }

    @Override
    public void canceled(@NonNull Call call) {
        logger.record(HttpEvent.CANCELED);
    }

    @Override
    public void satisfactionFailure(@NonNull Call call, @NonNull Response response) {
        logger.record(HttpEvent.SATISFACTION_FAILURE);
    }

    @Override
    public void cacheHit(@NonNull Call call, @NonNull Response response) {
        logger.record(HttpEvent.CACHE_HIT);
    }

    @Override
    public void cacheMiss(@NonNull Call call) {
        logger.record(HttpEvent.CACHE_MISS);
    }

    @Override
    public void cacheConditionalHit(@NonNull Call call, @NonNull Response cachedResponse) {
        logger.record(HttpEvent.CACHE_CONDITIONAL_HIT);
    }

    @Override
    public void retryDecision(@NonNull Call call, @NonNull IOException exception, boolean retry) {
        logger.record(HttpEvent.RETRY_DECISION);
    }

    // ==================== URL 脱敏 ====================

    private final Set<String> queryParamsNameToRedact = new HashSet<>();

    private String redactUrl(HttpUrl url) {
        if (queryParamsNameToRedact.isEmpty() || url.querySize() == 0) {
            return url.toString();
        }

        HttpUrl.Builder builder = url.newBuilder().query(null);
        for (int i = 0; i < url.querySize(); i++) {
            String parameterName = url.queryParameterName(i);
            String newValue = queryParamsNameToRedact.contains(parameterName) ? "██" : url.queryParameterValue(i);
            builder.addEncodedQueryParameter(parameterName, newValue);
        }
        return builder.toString();
    }

    // ==================== 构建请求/响应对象 ====================

    @NonNull
    private HttpLogData.Request buildRequest(@NonNull Request request) {
        return buildRequest(request, null, null, null);
    }

    @NonNull
    private HttpLogData.Request buildRequest(@NonNull Request request, @NonNull Connection connection) {
        return buildRequest(request, connection.route().proxy(), connection.protocol(), null);
    }

    @NonNull
    private HttpLogData.Request buildRequest(@NonNull Request request, @Nullable Proxy proxy,
                                             @Nullable Protocol protocol) {
        return buildRequest(request, proxy, protocol, null);
    }

    @NonNull
    private HttpLogData.Request buildRequest(@NonNull Request request, @Nullable Proxy proxy,
                                             @Nullable Protocol protocol, @Nullable IOException ioe) {
        HttpLogData.Request logRequest = new HttpLogData.Request();
        logRequest.setUrl(redactUrl(request.url()));
        logRequest.setMethod(request.method());
        logRequest.setProtocol(protocol == null ? null : protocol.toString().toUpperCase());
        logRequest.setProxy(proxy);

        RequestBody body = request.body();
        if (body != null) {
            logRequest.setContentType(buildMediaType(body.contentType()));
            try {
                logRequest.setContentLength(body.contentLength());
            } catch (IOException ignored) {
                // 忽略获取 content length 的异常
            }
            logRequest.setDuplex(body.isDuplex());
            logRequest.setOneShot(body.isOneShot());
        }

        logRequest.setHeaders(buildHeaders(request.headers()));
        logRequest.setIoe(ioe);
        return logRequest;
    }

    @NonNull
    private HttpLogData.Response buildResponse(@NonNull Response response) {
        HttpLogData.Response logResponse = new HttpLogData.Response();
        logResponse.setCode(response.code());
        logResponse.setMessage(response.message());
        logResponse.setProtocol(response.protocol().toString().toUpperCase());

        ResponseBody body = response.body();
        if (body != null) {
            logResponse.setContentType(buildMediaType(body.contentType()));
            logResponse.setContentLength(body.contentLength());
        }

        logResponse.setHeaders(buildHeaders(response.headers()));
        return logResponse;
    }

    @NonNull
    private HttpLogData.Headers buildHeaders(@NonNull Headers headers) {
        Map<String, List<String>> map = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.name(i);
            String value = headers.value(i);
            map.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
        return HttpLogData.Headers.of(map);
    }

    @Nullable
    private HttpLogData.ContentType buildMediaType(@Nullable MediaType mediaType) {
        if (mediaType == null) {
            return null;
        }

        HttpLogData.ContentType contentType = new HttpLogData.ContentType();
        contentType.setType(mediaType.type());
        contentType.setSubtype(mediaType.subtype());
        contentType.setCharset(mediaType.charset());
        contentType.setMediaType(mediaType.toString());
        return contentType;
    }
}
