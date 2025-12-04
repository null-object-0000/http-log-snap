# HTTP Log Snap 📸

一个轻量级的 Java HTTP 请求/响应日志记录库，**快照式**捕获完整的 HTTP 交互。

同时支持 **客户端**（OkHttp）和 **服务端**（Spring MVC）场景。

## ✨ 为什么选择 HTTP Log Snap？

- 🔄 **双向支持** - 客户端发起请求、服务端处理请求，一套 API 全搞定
- ⏱️ **详细耗时** - DNS、连接、TTFB、下载...每个阶段耗时一目了然
- 📊 **多种格式** - 文本格式便于调试，JSON 格式便于分析
- 🛡️ **脱敏支持** - Authorization、Cookie 等敏感头自动脱敏
- 🎯 **零侵入** - 不改业务代码，Filter/EventListener 即插即用
- 🔧 **可扩展** - 格式化器、输出目标、适配器全部可定制

## 📦 安装

```xml
<dependency>
    <groupId>io.github.null-object-0000</groupId>
    <artifactId>http-log-snap</artifactId>
    <version>0.0.1</version>
</dependency>
```

## 🚀 30 秒上手

### 客户端（OkHttp）

```java
// 1. 创建 OkHttpClient
OkHttpClient client = new OkHttpClient.Builder()
    .eventListenerFactory(OkHttpLoggingEventListener.FACTORY)
    .build();

// 2. 创建 Logger
HttpRequestLogger logger = HttpRequestLogger.forClient(HttpLogContext.of("创建订单"));
logger.putExtra("orderId", "ORD123");

// 3. 发起请求
String body = "{\"name\":\"test\"}";
logger.start(body);
Request request = new Request.Builder()
    .url("https://api.example.com/orders")
    .tag(HttpRequestLogger.class, logger)
    .post(RequestBody.create(body, MediaType.parse("application/json")))
    .build();
try (Response response = client.newCall(request).execute()) {
    logger.end(response.body().string());
}

// 4. 输出日志
logger.log();
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
// 方式1：实现 HttpLogCustomizer 统一定制（推荐）
@Component
public class MyHttpLogCustomizer implements HttpLogCustomizer {
    @Override
    public void customize(HttpRequestLogger logger, HttpServletRequest request) {
        // 添加 traceId
        logger.putExtra("traceId", request.getHeader("X-Trace-Id"));
        // 根据路径设置接口名称
        if (request.getRequestURI().startsWith("/api/users")) {
            logger.setInterfaceName("用户服务");
        }
    }
}

// 方式2：在 Controller 中按需设置
HttpRequestLogger logger = HttpRequestLoggerHolder.get();
if (logger != null) {
    logger.putExtra("orderId", "ORD123");
}

// 方式3：在全局异常处理器中记录异常（如果有 GlobalExceptionHandler）
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public BaseResponse<?> errorHandler(Exception e) {
        HttpLoggingExceptionHandler.record(e);  // 添加这一行
        // ... 原有逻辑
        return BaseResponse.error();
    }
}
```

**搞定！** 服务端自动记录所有 HTTP 请求/响应。

## 📝 日志输出效果

### 客户端日志

```
15:42:31.123 --- LOG EXTRAS -------------------------------------------------------
{"userId":10086,"source":"app"}
15:42:31.123 --- START [NONE] 获取用户信息 (total: 245ms)
15:42:31.180 --> DNS LOOKUP (50ms)
15:42:31.210 --> CONNECTING (30ms) [192.168.1.100:54321 -> 203.0.113.50:443]
15:42:31.215 --> REQUEST START --------------------------------------------------->
GET https://api.example.com/users/123 HTTP/2
Authorization: ██

15:42:31.350 <-- RESPONSE START <--------------------------------------------------
HTTP/2 200 OK (130ms)
Content-Type: application/json

{"id":123,"name":"张三","email":"zhangsan@example.com"}
15:42:31.368 <-- END RESPONSE (18ms, 256-byte body)
```

### 服务端日志

```
15:42:31.100 --- LOG EXTRAS -------------------------------------------------------
{"orderId":"ORD123","channel":"web"}
15:42:31.100 --- START [SERVER] 用户登录 -> UserController.login (total: 156ms) [client: 192.168.1.50:52341]
15:42:31.102 --> REQUEST START --------------------------------------------------->
POST http://localhost:8080/api/login HTTP/1.1
Content-Type: application/json

{"username":"zhangsan","password":"****"}
15:42:31.245 <-- RESPONSE START <--------------------------------------------------
200 OK (handler: 135ms)

{"code":0,"message":"success","data":{"token":"eyJ..."}}
15:42:31.256 <-- END RESPONSE (11ms, 128-byte body)
```

### JSON 格式（便于 ELK 分析）

```json
{
  "type": "HTTP_CLIENT",
  "duration_ms": 245,
  "network": {
    "local_address": "192.168.1.100:54321",
    "remote_address": "203.0.113.50:443"
  },
  "request": {
    "method": "GET",
    "url": "https://api.example.com/users/123"
  },
  "response": {
    "code": 200,
    "body": "{\"id\":123,\"name\":\"张三\"}"
  },
  "timing": {
    "dns_lookup_ms": 50,
    "connection_ms": 30,
    "server_processing_ms": 130
  }
}
```

## ⏱️ 性能指标

| 客户端指标 | 服务端指标 |
|-----------|-----------|
| DNS 解析时间 | 请求体读取时间 |
| TCP/TLS 连接时间 | Handler 执行时间 |
| 请求发送时间 | 响应构建时间 |
| TTFB（首字节时间）| 响应发送时间 |
| 内容下载时间 | 框架开销时间 |

## ⚙️ 默认配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 格式化器 | `TextHttpLogFormatter` | 文本格式，类似 HTTP 原始报文 |
| 输出目标 | `Slf4jLogOutput` | 输出到 SLF4J（INFO 级别） |

**切换格式化器：**

```java
// 使用 JSON 格式
HttpRequestLogger.setDefaultFormatter(new JsonHttpLogFormatter());

// 使用美化的 JSON 格式
HttpRequestLogger.setDefaultFormatter(new JsonHttpLogFormatter(true));

// 使用 JSON 格式 + 记录完整事件序列（用于分析调用链路）
HttpRequestLogger.setDefaultFormatter(new JsonHttpLogFormatter()
    .setPrettyPrint(true)
    .setIncludeEvents(true));
```

**切换输出目标：**

```java
// 输出到控制台
HttpRequestLogger.setDefaultOutput(new ConsoleLogOutput());

// 输出到 SLF4J DEBUG 级别
HttpRequestLogger.setDefaultOutput(new Slf4jLogOutput(Slf4jLogOutput.LogLevel.DEBUG));

// 同时输出到多个目标
HttpRequestLogger.setDefaultOutput(CompositeLogOutput.of(
    new Slf4jLogOutput(),
    new ConsoleLogOutput()
));
```

## 📚 文档

- [📖 使用指南](docs/guide.md) - 完整的使用教程
- [🔧 高级用法](docs/advanced.md) - 自定义格式化器、输出目标、适配器
- 💡 接入示例 - 在 IDE 中直接运行：
  - OkHttp 客户端: `src/test/java/.../demo/OkHttpClientDemo.java`
  - Spring Boot 服务端: `src/test/java/.../demo/SpringBootServerDemo.java`

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License
