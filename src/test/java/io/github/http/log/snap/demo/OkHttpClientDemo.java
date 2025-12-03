package io.github.http.log.snap.demo;

import io.github.http.log.snap.HttpLogContext;
import io.github.http.log.snap.HttpRequestLogger;
import io.github.http.log.snap.client.okhttp.OkHttpLoggingEventListener;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OkHttp 客户端接入示例
 * <p>
 * 直接在 IDE 中运行此类即可查看日志输出效果
 */
public class OkHttpClientDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== HTTP Log Snap - OkHttp Client Demo ===\n");

        // 1. 创建带有 EventListener 的 OkHttpClient
        OkHttpClient client = new OkHttpClient.Builder()
                .eventListenerFactory(OkHttpLoggingEventListener.FACTORY)
                .build();

        // 2. 创建 Logger 并设置扩展信息
        HttpRequestLogger logger = HttpRequestLogger.forClient(
                HttpLogContext.of("创建订单")
        );
        logger.putExtra("orderId", "ORD123456")
              .putExtra("userId", 10086);

        // 3. 记录请求体并开始
        String requestBody = "{\"product\":\"iPhone\",\"quantity\":1}";
        logger.start(requestBody);

        // 4. 发送请求，绑定 Logger
        Request request = new Request.Builder()
                .url("https://httpbin.org/post")
                .tag(HttpRequestLogger.class, logger)  // 关键：绑定 logger
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        // 5. 执行请求并记录响应体
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            logger.end(responseBody);
            System.out.println("Response: " + response.code() + "\n");
        }

        // 6. 输出日志
        logger.log();
    }
}

