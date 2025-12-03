# OkHttp 客户端接入示例

本示例演示如何在 OkHttp 客户端中集成 HTTP Log Snap。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.null-object-0000</groupId>
    <artifactId>http-log-snap</artifactId>
    <version>0.0.1</version>
</dependency>
```

### 2. 使用示例

```java
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
        .url("https://api.example.com/orders")
        .tag(HttpRequestLogger.class, logger)  // 关键：绑定 logger
        .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
        .build();

// 5. 执行请求并记录响应体
try (Response response = client.newCall(request).execute()) {
    String responseBody = response.body().string();
    logger.end(responseBody);
}

// 6. 输出日志
logger.log();
```

## 运行示例

```bash
mvn compile exec:java
```

## 日志输出效果

```
15:42:31.123 --- LOG EXTRAS -------------------------------------------------->
{"orderId":"ORD123456","userId":10086}
15:42:31.123 --- START [NONE] 创建订单 (total: 245ms)
15:42:31.180 --> DNS LOOKUP (50ms)
15:42:31.210 --> CONNECTING (30ms) [192.168.1.100:54321 -> 203.0.113.50:443]
15:42:31.215 --> REQUEST START --------------------------------------------------->
POST https://httpbin.org/post HTTP/2
Content-Type: application/json

{"product":"iPhone","quantity":1}
15:42:31.220 --> END REQUEST (5ms, 35-byte body)
15:42:31.350 <-- RESPONSE START <--------------------------------------------------
HTTP/2 200 OK (130ms)

{"success":true}
15:42:31.368 <-- END RESPONSE (18ms, 16-byte body)
```
