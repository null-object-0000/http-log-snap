# HTTP Log Snap 接入示例

本目录包含 HTTP Log Snap 的完整接入示例。

## 示例项目

| 目录 | 说明 | 运行方式 |
|------|------|----------|
| [client-okhttp](./client-okhttp) | OkHttp 客户端接入示例 | `mvn compile exec:java` |
| [server-spring-boot](./server-spring-boot) | Spring Boot 服务端接入示例 | `mvn spring-boot:run` |

## 快速体验

### OkHttp 客户端

```bash
cd client-okhttp
mvn compile exec:java
```

### Spring Boot 服务端

```bash
cd server-spring-boot
mvn spring-boot:run

# 另开终端测试
curl http://localhost:8080/api/users
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":10086,"productId":"P001","amount":99.9}'
```

## 核心用法速查

### 客户端（OkHttp）

```java
// 1. 添加 EventListener
OkHttpClient client = new OkHttpClient.Builder()
    .eventListenerFactory(OkHttpLoggingEventListener.FACTORY)
    .build();

// 2. 发起请求，自动记录日志
Response response = client.newCall(request).execute();
```

### 服务端（Spring Boot）

```yaml
# application.yml
mc:
  http:
    logging:
      enabled: true
```

```java
// Controller 中设置扩展信息
HttpRequestLogger logger = HttpRequestLoggerHolder.get();
if (logger != null) {
    logger.setInterfaceName("创建订单")
          .putExtra("orderId", "ORD123")
          .putExtra("userId", 10086);
}
```

