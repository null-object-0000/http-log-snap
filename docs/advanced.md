# 🔧 高级用法

本文档介绍 HTTP Log Snap 的高级配置和自定义扩展。

## 目录

- [格式化器配置](#格式化器配置)
- [自定义输出目标](#自定义输出目标)
- [自定义格式化器](#自定义格式化器)
- [SPI 扩展机制](#spi-扩展机制)
- [Spring Boot 高级配置](#spring-boot-高级配置)

---

## 格式化器配置

### TextHttpLogFormatter

文本格式化器生成类似 OkHttp HttpLoggingInterceptor 风格的日志输出。

```java
TextHttpLogFormatter formatter = new TextHttpLogFormatter()
    .redactHeaders(Set.of("Authorization", "Cookie", "X-Api-Key"))
    .redactQueryParams(Set.of("token", "password", "secret"))
    .setRedactPlaceholder("***");  // 自定义脱敏占位符，默认 ██

// 设置为全局默认
HttpRequestLogger.setDefaultFormatter(formatter);
```

**输出示例：**
```
15:42:31.123 --- START [NONE] 获取用户信息 (total: 245ms)
15:42:31.180 --> DNS LOOKUP (50ms)
15:42:31.210 --> CONNECTING (30ms)
15:42:31.215 --> REQUEST START --------------------------------------------------->
GET https://api.example.com/users/123?token=*** HTTP/2
Authorization: ***

15:42:31.350 <-- RESPONSE START <--------------------------------------------------
HTTP/2 200 OK (130ms)
Content-Type: application/json

{"id":123,"name":"张三"}
15:42:31.368 <-- END RESPONSE (18ms, 256-byte body)
```

### JsonHttpLogFormatter

JSON 格式化器适合日志分析和结构化存储：

```java
// 紧凑 JSON（默认）
JsonHttpLogFormatter formatter = new JsonHttpLogFormatter();

// 美化 JSON（便于阅读）
JsonHttpLogFormatter prettyFormatter = new JsonHttpLogFormatter(true);

// 配置脱敏
formatter.redactHeaders(Set.of("Authorization"))
         .redactQueryParams(Set.of("token"));

// 包含完整事件序列（用于分析调用链路）
JsonHttpLogFormatter formatterWithEvents = new JsonHttpLogFormatter()
    .setIncludeEvents(true);  // 开启后会在 timing.events 中记录所有事件的详细信息

// 设置为全局默认
HttpRequestLogger.setDefaultFormatter(formatter);
```

---

## 自定义输出目标

### 内置输出

```java
// SLF4J 输出（默认）
HttpLogOutput slf4jOutput = new Slf4jLogOutput();
HttpLogOutput slf4jDebug = new Slf4jLogOutput(Slf4jLogOutput.LogLevel.DEBUG);
HttpLogOutput slf4jCustomLogger = new Slf4jLogOutput("com.example.http");

// 控制台输出
HttpLogOutput consoleOutput = new ConsoleLogOutput();
HttpLogOutput customConsole = new ConsoleLogOutput(System.out, System.err);

// 组合输出（同时输出到多个目标）
HttpLogOutput compositeOutput = CompositeLogOutput.of(
    new Slf4jLogOutput(),
    new ConsoleLogOutput(),
    new MyKafkaLogOutput()
);

// 设置全局默认输出
HttpRequestLogger.setDefaultOutput(compositeOutput);
```

### 实现自定义输出

```java
public class KafkaLogOutput implements HttpLogOutput {
    
    private final KafkaProducer<String, String> producer;
    private final String topic;
    
    public KafkaLogOutput(KafkaProducer<String, String> producer, String topic) {
        this.producer = producer;
        this.topic = topic;
    }
    
    @Override
    public void output(@NonNull HttpLogData data, @NonNull String formattedLog) {
        producer.send(new ProducerRecord<>(topic, formattedLog));
    }
    
    @Override
    public void outputError(@NonNull HttpLogData data, @NonNull String formattedLog, 
                           @NonNull Throwable error) {
        // 错误日志发送到单独的 topic
        producer.send(new ProducerRecord<>(topic + "-error", formattedLog));
    }
    
    @Override
    public String getName() {
        return "Kafka:" + topic;
    }
    
    @Override
    public void close() {
        producer.close();
    }
}
```

### 处理重试后成功的场景

在使用 OkHttp 等支持自动重试的 HTTP 客户端时，可能会遇到以下场景：
- 请求过程中发生了异常（如连接中断、超时等）
- 客户端自动重试后最终成功（返回了成功的响应，状态码 200-299）
- 但 `HttpRequestLogger.log()` 仍然会调用 `outputError()`，因为异常信息被保留在 `HttpLogData` 中

**原因：**
`http-log-snap` 作为底层日志库，只负责**记录事实**（发生了什么），不包含业务判断逻辑。当 OkHttp 的 `EventListener` 监听到 `responseFailed` 事件时，会将异常记录到 `Response.ioe` 中，即使后续重试成功，这个异常信息也会被保留。

**处理建议：**
在自定义的 `HttpLogOutput` 实现中，根据业务需求判断是否应该将"重试后成功"的场景当作错误处理：

```java
@Override
public void outputError(@NonNull HttpLogData data, @NonNull String formattedLog, 
                       @NonNull Throwable error) {
    // 检查响应是否最终成功（状态码 200-299）
    boolean isSuccess = isResponseSuccessful(data);
    
    if (isSuccess) {
        // 请求最终成功，但过程中有异常（可能是重试导致的）
        // 根据业务需求决定如何处理：
        // 1. 当作成功处理，使用 info 级别输出
        // 2. 记录重试信息，便于监控和分析
        String retryMessage = String.format("请求最终成功，但在重试过程中遇到异常: %s - %s", 
                error.getClass().getSimpleName(), error.getMessage());
        output(data, formattedLog + " | " + retryMessage);
    } else {
        // 请求最终失败，按错误处理
        // 发送到错误 topic 或使用错误级别日志
        producer.send(new ProducerRecord<>(topic + "-error", formattedLog));
    }
}

/**
 * 检查响应是否成功
 */
private boolean isResponseSuccessful(@NonNull HttpLogData data) {
    HttpLogData.Response response = data.getResponse();
    if (response == null) {
        return false;
    }
    Integer code = response.getCode();
    if (code == null) {
        return false;
    }
    // HTTP 状态码 200-299 表示成功
    return code >= 200 && code < 300;
}
```

**设计原则：**
- **底层库（http-log-snap）**：只负责记录事实，不做业务判断
- **上层实现（自定义 HttpLogOutput）**：根据业务需求决定如何处理不同的场景
- 这样设计的好处是保持底层库的通用性和灵活性，不同业务可以根据自己的需求定制处理逻辑

---

## 自定义格式化器

### 使用 AbstractHttpLogFormatter

```java
public class MyCustomFormatter extends AbstractHttpLogFormatter {
    
    @Override
    protected String doFormat(HttpLogData data) {
        StringBuilder sb = new StringBuilder();
        
        // 自定义格式
        sb.append("[").append(data.getDirection()).append("] ");
        sb.append(data.getRequest().getMethod()).append(" ");
        sb.append(data.getRequest().getUrl());
        
        if (data.getResponse() != null) {
            sb.append(" -> ").append(data.getResponse().getCode());
        }
        
        sb.append(" (").append(data.getTotalTimeMs()).append("ms)");
        
        return sb.toString();
    }
    
    @Override
    public String getFormatType() {
        return "MY_CUSTOM";
    }
}
```

### 直接实现 HttpLogFormatter

```java
public class XmlLogFormatter implements HttpLogFormatter {
    
    @Override
    public String format(@NonNull HttpLogData data) {
        return """
            <httpLog>
                <direction>%s</direction>
                <method>%s</method>
                <url>%s</url>
                <status>%d</status>
                <duration>%d</duration>
            </httpLog>
            """.formatted(
                data.getDirection(),
                data.getRequest().getMethod(),
                data.getRequest().getUrl(),
                data.getResponse() != null ? data.getResponse().getCode() : 0,
                data.getTotalTimeMs()
            );
    }
    
    @Override
    public HttpLogFormatter redactHeaders(Set<String> headerNames) {
        // 实现脱敏逻辑
        return this;
    }
    
    @Override
    public String getFormatType() {
        return "XML";
    }
}
```

---

## SPI 扩展机制

HTTP Log Snap 支持 Java SPI 机制自动发现和加载组件。

### 注册自定义组件

1. 创建 `META-INF/services/io.github.http.log.snap.spi.HttpLoggingRegistry` 文件

2. 添加自定义 Registry 实现：

```java
public class MyHttpLoggingRegistry implements HttpLoggingRegistry {
    
    @Override
    public void register() {
        // 注册自定义格式化器
        registerFormatter("my-format", new MyCustomFormatter());
        
        // 注册自定义输出
        registerOutput("kafka", new KafkaLogOutput(producer, "http-logs"));
    }
}
```

### 使用注册的组件

```java
// 通过名称获取格式化器
HttpLogFormatter formatter = HttpLoggingRegistry.getFormatter("my-format");

// 通过名称获取输出
HttpLogOutput output = HttpLoggingRegistry.getOutput("kafka");
```

---

## Spring Boot 高级配置

### 完整配置示例

```yaml
newbie:
  http:
    logging:
      enabled: true
      
      # 格式化配置
      format: json                      # json 或 text（默认 json）
      include-events: false             # 是否包含完整事件序列（仅 JSON 格式有效）
      
      # 内容配置
      include-request-body: true
      include-response-body: true
      include-headers: true
      max-payload-length: -1            # 最大记录长度（字节，-1 表示无限制）
      
      # 排除配置
      exclude-patterns:
        - /actuator/**
        - /health
        - /favicon.ico
        - "*.css"
        - "*.js"
      
      # 脱敏配置
      headers-to-redact:
        - Authorization
        - Cookie
        - X-Api-Key
      query-params-to-redact:
        - token
        - password
        - secret
```

### 手动配置 Filter

```java
@Configuration
public class HttpLoggingConfig {
    
    @Bean
    public FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilter() {
        HttpLoggingFilter filter = new HttpLoggingFilter();
        filter.setIncludeRequestBody(true);
        filter.setIncludeResponseBody(true);
        filter.setFormatter(new TextHttpLogFormatter()
            .redactHeaders(Set.of("Authorization")));
        filter.addExcludePattern("/actuator/**");
        
        FilterRegistrationBean<HttpLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
```

### 条件日志记录

```java
@Configuration
public class HttpLoggingConfig {
    
    @Bean
    public HttpLoggingFilter httpLoggingFilter() {
        HttpLoggingFilter filter = new HttpLoggingFilter();
        
        // 条件记录：只记录慢请求
        filter.setLogCondition(data -> data.getTotalTimeMs() > 1000);
        
        // 条件记录：只记录失败请求
        filter.setLogCondition(HttpLogData::hasFailed);
        
        return filter;
    }
}
```

### URL 规范化配置

URL 规范化功能可以将路径中的数字 ID 替换为占位符，降低监控系统中的标签基数：

```java
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
        }
    }
}
```

规范化后的 URL 会出现在日志的 `normalized_url` 字段中（JSON 格式），或在文本格式中显示为 "Normalized URL" 行。如果规范化后的 URL 与原始 URL 一致，则不会显示 `normalized_url` 字段，避免冗余。
