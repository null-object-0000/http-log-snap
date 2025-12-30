# 📖 HTTP Log Snap 使用指南

本文档详细介绍 HTTP Log Snap 的完整使用方法。

## 目录

- [架构概览](#架构概览)
- [客户端使用（OkHttp）](#客户端使用okhttp)
- [服务端使用（Spring Boot）](#服务端使用spring-boot)
- [日志格式详解](#日志格式详解)
- [耗时指标](#耗时指标)
- [依赖说明](#依赖说明)
- [事件列表](#事件列表)

---

## 架构概览

```
http-log-snap/
├── 核心层 (Core)
│   ├── HttpLogData          # 统一日志数据模型
│   ├── HttpTiming           # 耗时统计（含 WebMetrics）
│   ├── HttpEvent            # 事件枚举定义
│   ├── HttpDirection        # 方向枚举（CLIENT/SERVER）
│   ├── HttpRequestLogger    # 核心日志记录器
│   ├── HttpLogContext       # 日志上下文信息
│   └── LogStringBuilder     # 日志字符串构建工具
│
├── 格式化层 (Formatter) - 可扩展
│   ├── HttpLogFormatter             # 格式化器接口
│   ├── AbstractHttpLogFormatter     # 格式化器抽象基类
│   ├── TextHttpLogFormatter         # 文本格式输出
│   └── JsonHttpLogFormatter         # JSON 格式输出
│
├── 输出层 (Output) - 可扩展
│   ├── HttpLogOutput          # 日志输出接口
│   ├── Slf4jLogOutput         # SLF4J 输出（默认）
│   ├── ConsoleLogOutput       # 控制台输出
│   └── CompositeLogOutput     # 组合输出
│
├── 客户端适配层 (Client Adapter) - 可扩展
│   ├── HttpClientAdapter                  # 客户端适配器接口
│   └── client/okhttp/
│       └── OkHttpLoggingEventListener     # OkHttp 事件监听器
│
├── 服务端适配层 (Server Adapter) - 可扩展
│   ├── HttpServerAdapter                  # 服务端适配器接口
│   ├── AbstractHttpServerAdapter          # 服务端适配器抽象基类
│   └── server/spring/
│       ├── HttpLoggingFilter              # Servlet Filter
│       ├── HttpLoggingHandlerInterceptor  # Spring MVC 拦截器
│       ├── HttpLoggingAutoConfiguration   # Spring Boot 自动配置
│       └── HttpRequestLoggerHolder        # ThreadLocal 持有器
│
└── SPI 支持 (spi/)
    └── HttpLoggingRegistry      # 组件注册中心（支持 SPI 自动发现）
```

---

## 客户端使用（OkHttp）

> ⚠️ **重要说明：请求体和响应体需要手动记录**
>
> OkHttp 的 EventListener 机制只能自动记录请求/响应的**元信息**（URL、方法、状态码、头信息等）和**字节数**，**无法**自动获取请求体和响应体的原始内容。
>
> 如果需要在日志中包含请求体和响应体，需要手动调用：
> - **请求体**：在发送请求前调用 `logger.start(requestBody)`
> - **响应体**：在读取响应后调用 `logger.end(responseBody)`

### 方式一：使用 EventListener.Factory（自动记录元信息）

最简单的方式，自动记录所有 HTTP 元信息：

```java
OkHttpClient client = new OkHttpClient.Builder()
    .eventListenerFactory(OkHttpLoggingEventListener.FACTORY)
    .build();

// 发起请求时可以附加上下文信息
Request request = new Request.Builder()
    .url("https://api.example.com/users")
    .tag(HttpLogContext.class, HttpLogContext.of("获取用户列表"))
    .build();

Response response = client.newCall(request).execute();
// 注意：此方式只会记录元信息，不会记录请求体和响应体内容
```

### 方式二：手动控制日志记录（不使用 EventListener）

完全手动控制，适合不使用 EventListener 的场景：

```java
// 创建日志记录器
HttpRequestLogger logger = HttpRequestLogger.forClient(
    HttpLogContext.builder()
        .interfaceName("用户登录")
        .traceId("trace-123")
        .build()
);

// 【重要】开始记录，并传入请求体
String requestBody = "{\"username\":\"test\"}";
logger.start(requestBody);

// 构建并发送请求（不需要 EventListener）
OkHttpClient client = new OkHttpClient.Builder().build();
Request request = new Request.Builder()
    .url("https://api.example.com/login")
    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
    .build();

// 执行请求并读取响应
try (Response response = client.newCall(request).execute()) {
    String responseBody = response.body().string();
    
    // 【重要】结束记录，并传入响应体
    logger.end(responseBody);
}

// 输出日志（只包含请求体和响应体，无详细耗时指标）
logger.log();
```

### 方式三：结合使用（⭐ 推荐）

结合 EventListener 自动记录的详细耗时指标和手动记录的请求体/响应体，获得最完整的日志信息：

```java
// 1. 创建带有 EventListener 的 OkHttpClient
OkHttpClient client = new OkHttpClient.Builder()
    .eventListenerFactory(OkHttpLoggingEventListener.FACTORY)
    .build();

// 2. 创建 logger 并记录请求体（可配置 URL 规范化占位符）
HttpRequestLogger logger = HttpRequestLogger.forClient(
    HttpLogContext.builder()
        .interfaceName("创建用户")
        .urlPlaceholders("{userId}")  // 配置 URL 规范化占位符（可选）
        .build()
);
String requestBody = "{\"name\":\"张三\",\"age\":25}";
logger.start(requestBody);  // 记录请求体

// 3. 发送请求，绑定 logger 到请求
Request request = new Request.Builder()
    .url("https://api.example.com/users")
    .tag(HttpRequestLogger.class, logger)  // 关键：绑定 logger，EventListener 会使用它
    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
    .build();

// 4. 执行请求并记录响应体
try (Response response = client.newCall(request).execute()) {
    String responseBody = response.body().string();
    logger.end(responseBody);  // 记录响应体
}

// 5. 输出完整日志
// 包含：元信息 + 详细耗时指标 + 请求体 + 响应体
logger.log();
```

---

## 服务端使用（Spring Boot）

### 方式一：自动配置（推荐）

在 `application.yml` 中启用：

```yaml
newbie:
  http:
    logging:
      enabled: true
      format: json                    # json 或 text（默认 json）
      include-request-body: true
      include-response-body: true
      include-headers: true
      include-events: false           # 是否包含完整事件序列（仅 JSON 格式有效）
      max-payload-length: -1           # 最大记录长度（字节，-1 表示无限制）
      exclude-patterns:               # 排除的 URL
        - /actuator/**
        - /health
      headers-to-redact:              # 需要脱敏的请求头
        - Authorization
        - Cookie
      query-params-to-redact:         # 需要脱敏的查询参数
        - token
        - password
```

### 方式二：手动配置 Filter

```java
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilter() {
        HttpLoggingFilter filter = new HttpLoggingFilter();
        filter.setIncludeRequestBody(true);
        filter.setIncludeResponseBody(true);
        filter.setFormatter(new TextHttpLogFormatter());
        filter.addExcludePattern("/actuator/**");
        
        FilterRegistrationBean<HttpLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public WebMvcConfigurer httpLoggingWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new HttpLoggingHandlerInterceptor())
                        .addPathPatterns("/**");
            }
        };
    }
}
```

### 在 Controller 中获取 Logger

```java
@RestController
public class UserController {

    @PostMapping("/api/users")
    public User createUser(@RequestBody UserDTO dto) {
        // 获取当前请求的 Logger
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        
        if (logger != null) {
            // 添加自定义上下文信息
            logger.setHandlerMethod("createUser");
            // 配置 URL 规范化占位符（降低监控标签基数）
            logger.getContext().setUrlPlaceholders(new String[]{"{userId}"});
        }
        
        return userService.create(dto);
    }
}
```

### URL 规范化（降低监控标签基数）

URL 规范化功能可以将路径中的数字 ID 替换为占位符，降低监控系统中的标签基数：

```java
// 在 HttpLogCustomizer 中配置
@Component
public class MyHttpLogCustomizer implements HttpLogCustomizer {
    @Override
    public void customize(HttpRequestLogger logger, HttpServletRequest request) {
        // 根据路径配置不同的占位符
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/info/")) {
            // URL: /api/info/2434420/1459635/seats
            // 规范化后: /api/info/{showId}/{ticketId}/seats
            logger.getContext().setUrlPlaceholders(new String[]{"{showId}", "{ticketId}"});
        } else if (uri.startsWith("/api/users/")) {
            // URL: /api/users/123
            // 规范化后: /api/users/{userId}
            logger.getContext().setUrlPlaceholders(new String[]{"{userId}"});
        }
    }
}
```

规范化后的 URL 会出现在日志的 `normalized_url` 字段中（JSON 格式），或在文本格式中显示为 "Normalized URL" 行。

---

## 日志格式详解

### 文本格式（客户端）

```
15:42:31.123 --- START [NONE] 获取用户信息 (total: 245ms)
15:42:31.125 --- BUILD CLIENT (2ms)
15:42:31.128 --- BUILD REQUEST (3ms)
15:42:31.130 --> CALL START ------------------------------------------------------>
15:42:31.180 --> DNS LOOKUP (50ms)
15:42:31.210 --> CONNECTING (30ms)
15:42:31.215 --> REQUEST START --------------------------------------------------->
GET https://api.example.com/users/123 HTTP/2
Content-Type: application/json
Authorization: ██

15:42:31.220 --> END REQUEST (5ms)
15:42:31.350 <-- RESPONSE START <--------------------------------------------------
HTTP/2 200 OK (130ms)
Content-Type: application/json; charset=utf-8
Content-Length: 256

{"id":123,"name":"张三","email":"zhangsan@example.com"}
15:42:31.368 <-- END RESPONSE (18ms, 256-byte body)
```

### 文本格式（服务端）

```
15:42:31.100 --- START [SERVER] 用户登录接口 -> UserController.login (total: 156ms)
15:42:31.102 --> REQUEST START --------------------------------------------------->
POST http://localhost:8080/api/login HTTP/1.1
Content-Type: application/json
Content-Length: 45

{"username":"zhangsan","password":"****"}
15:42:31.110 --> END REQUEST (8ms)
15:42:31.245 <-- RESPONSE START <--------------------------------------------------
200 OK (handler: 135ms)
Content-Type: application/json

{"code":0,"message":"success","data":{"token":"eyJ..."}}
15:42:31.256 <-- END RESPONSE (11ms, 128-byte body)
```

### JSON 格式

```json
{
  "type": "HTTP_CLIENT",
  "direction": "client",
  "timestamp": 1701234567890,
  "duration_ms": 245,
  "success": true,
  "context": {
    "interface": "获取用户信息",
    "trace_id": "trace-123"
  },
  "request": {
    "method": "GET",
    "url": "https://api.example.com/users/123",
    "protocol": "HTTP/2",
    "headers": {
      "Content-Type": "application/json"
    }
  },
  "response": {
    "code": 200,
    "message": "OK",
    "headers": {},
    "body": "{\"id\":123,\"name\":\"张三\"}",
    "body_bytes": 256
  },
  "timing": {
    "total_ms": 245,
    "metrics": {
      "dns_lookup_ms": 50,
      "connection_ms": 30,
      "request_sent_ms": 5,
      "server_processing_ms": 130,
      "content_download_ms": 18
    }
  }
}
```

---

## 耗时指标

### 客户端指标

| 指标 | 说明 |
|------|------|
| `requestPreparation` | 请求准备时间（开始 → DNS 开始） |
| `dnsLookup` | DNS 解析时间 |
| `connection` | 连接建立时间（包括 TCP 和 TLS） |
| `requestSent` | 请求发送时间 |
| `serverProcessing` | 服务端处理时间（TTFB） |
| `contentDownload` | 响应内容下载时间 |

### 服务端指标

| 指标 | 说明 |
|------|------|
| `frameworkOverhead` | 框架开销时间（请求接收 → Handler 开始） |
| `requestBodyRead` | 请求体读取时间 |
| `handlerExecution` | Handler/Controller 执行时间 |
| `responseBuild` | 响应构建时间 |
| `responseWrite` | 响应发送时间 |

### 获取性能指标

```java
HttpRequestLogger logger = ...;

// 获取 WebMetrics
HttpTiming.WebMetrics metrics = logger.getMetrics();

// 客户端指标
long dnsTime = metrics.dnsLookup();
long connectionTime = metrics.connection();
long ttfb = metrics.serverProcessing();

// 服务端指标
long handlerTime = metrics.handlerExecution();
long responseTime = metrics.responseWrite();

// 打印详细指标
System.out.println(metrics.print());
```

### 打印事件序列

```java
HttpRequestLogger logger = ...;

// 按执行顺序打印所有事件
System.out.println(logger.printSequential());
```

输出：

```
=== HTTP Request Events (按执行顺序) ===

Step   Time                      Event                              Interval    Cumulative
----   ----                      -----                              --------    ----------
1      15:42:31.123              HttpRequestLogger.start            0ms         0ms
2      15:42:31.130              HttpRequestLogger.callStart        7ms         7ms
3      15:42:31.130              HttpRequestLogger.dnsStart         0ms         7ms
4      15:42:31.180              HttpRequestLogger.dnsEnd           50ms        57ms
...
```

---

## 依赖说明

| 依赖 | 作用 | 是否必须 |
|------|------|----------|
| Lombok | 简化代码 | 是（编译时） |
| SLF4J | 日志门面 | 是 |
| Fastjson2 | JSON 格式化 | 否（仅 JsonHttpLogFormatter 需要） |
| OkHttp | 客户端适配 | 否（仅客户端使用时需要） |
| Spring Web/WebMVC | 服务端适配 | 否（仅服务端使用时需要） |
| Spring Boot | 自动配置 | 否（仅自动配置时需要） |
| Jakarta Servlet | Servlet 支持 | 否（仅服务端使用时需要） |

---

## 事件列表

### 通用事件

- `START` / `END` - 请求开始/结束

### 客户端事件

- `BUILD_URI` / `BUILD_CONFIG` / `BUILD_CLIENT` / `BUILD_REQUEST` - 构建阶段
- `CALL_START` / `CALL_END` / `CALL_FAILED` - 调用阶段
- `DNS_START` / `DNS_END` - DNS 解析
- `CONNECT_START` / `CONNECT_END` / `CONNECT_FAILED` - 连接阶段
- `SECURE_CONNECT_START` / `SECURE_CONNECT_END` - TLS 握手
- `CONNECTION_ACQUIRED` / `CONNECTION_RELEASED` - 连接池
- `REQUEST_HEADERS_START` / `REQUEST_HEADERS_END` - 请求头
- `REQUEST_BODY_START` / `REQUEST_BODY_END` - 请求体
- `RESPONSE_HEADERS_START` / `RESPONSE_HEADERS_END` - 响应头
- `RESPONSE_BODY_START` / `RESPONSE_BODY_END` - 响应体

### 服务端事件

- `REQUEST_RECEIVED` - 请求接收
- `HANDLER_START` / `HANDLER_END` / `HANDLER_EXCEPTION` - Handler 处理
- `RESPONSE_BUILD_START` / `RESPONSE_BUILD_END` - 响应构建
- `RESPONSE_COMMITTED` - 响应提交

