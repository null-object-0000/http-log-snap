# 🔧 高级用法

本文档介绍 HTTP Log Snap 的高级配置和自定义扩展。

## 目录

- [格式化器配置](#格式化器配置)
- [自定义输出目标](#自定义输出目标)
- [自定义格式化器](#自定义格式化器)
- [SPI 扩展机制](#spi-扩展机制)
- [Spring Boot 高级配置](#spring-boot-高级配置)
- [条件排除规则](#条件排除规则)
- [SSE 响应规则](#sse-响应规则)
  - [基本用法](#基本用法-1)
  - [配置示例](#配置示例-1)
  - [完整配置示例](#完整配置示例-1)
  - [代码配置方式](#代码配置方式-1)
  - [规则优先级](#规则优先级-1)
  - [与排除规则的区别](#与排除规则的区别-1)
  - [规则类的继承关系](#规则类的继承关系-1)
  - [使用场景](#使用场景-1)
  - [日志输出示例](#日志输出示例)
  - [最佳实践](#最佳实践)
- [已知限制](#已知限制)

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

---

## 条件排除规则

除了简单的 URL 模式排除（`exclude-patterns`），还支持基于 URL + body/header 条件的灵活排除规则（`exclude-rules`）。

### 基本用法

条件排除规则允许你在 URL 匹配的基础上，进一步根据请求体（body）或请求头（header）的内容来决定是否排除日志记录。

### JSON 表达式过滤

当请求体为 JSON 格式时，可以使用表达式来过滤：

**支持的表达式格式：**

- **布尔值比较**：`stream == true`、`enabled != false`
- **数字比较**：`count > 10`、`age >= 18`、`score <= 100`
- **字符串比较**：`name == "test"`、`status != "error"`（字符串需要用引号包裹）
- **嵌套字段访问**：`status.code == 200`、`user.profile.age >= 18`（使用点号访问嵌套字段）
- **null 值比较**：`value == null`、`data != null`

**支持的操作符：**

- `==`：等于
- `!=`：不等于
- `>`：大于
- `<`：小于
- `>=`：大于等于
- `<=`：小于等于

**配置示例：**

```yaml
newbie:
  http:
    logging:
      exclude-rules:
        # 当 stream 字段为 true 时排除
        - url-pattern: /api/webhook
          body-json-expression: "stream == true"
        
        # 当 status.code 等于 200 时排除
        - url-pattern: /api/status
          body-json-expression: "status.code == 200"
        
        # 当 count 大于 100 时排除
        - url-pattern: /api/stats
          body-json-expression: "count > 100"
        
        # 当 order.status 等于 "completed" 时排除
        - url-pattern: /api/order
          body-json-expression: "order.status == \"completed\""
```

### Body 条件过滤

除了 JSON 表达式，还支持简单的字符串匹配：

```yaml
exclude-rules:
  # body 包含特定字符串时排除
  - url-pattern: /api/upload
    body-contains: "skip-log"
  
  # body 匹配正则表达式时排除
  - url-pattern: /api/data
    body-matches: ".*test.*"
```

### Header 条件过滤

支持基于请求头的条件判断：

```yaml
exclude-rules:
  # header 值精确匹配时排除
  - url-pattern: /api/test
    header-name: X-Skip-Log
    header-value: "true"
  
  # header 值包含特定字符串时排除
  - url-pattern: /api/bot
    header-name: User-Agent
    header-value-contains: "bot"
  
  # header 值匹配正则表达式时排除
  - url-pattern: /api/monitor
    header-name: X-Monitor-Type
    header-value-matches: ".*health.*"
  
  # 只检查 header 是否存在（不检查值）
  - url-pattern: /api/internal
    header-name: X-Internal-Request
```

### 条件优先级

1. **JSON 表达式**（`body-json-expression`）：如果设置，优先使用 JSON 表达式
2. **Body 匹配**（`body-contains` 或 `body-matches`）：如果未设置 JSON 表达式，使用 body 匹配
3. **Header 条件**：可以单独使用或与 body 条件组合
4. **仅 URL 匹配**：如果 URL 匹配但未设置任何 body/header 条件，则直接排除（向后兼容）

### 组合条件

同一个规则可以同时设置 body 和 header 条件，**所有条件都满足时才排除**：

```yaml
exclude-rules:
  # URL 匹配 + body 条件 + header 条件（所有条件都满足时才排除）
  - url-pattern: /api/webhook
    body-json-expression: "stream == true"
    header-name: X-Skip-Log
    header-value: "true"
```

### 完整配置示例

```yaml
newbie:
  http:
    logging:
      enabled: true
      exclude-patterns:              # 简单排除：直接排除 URL 模式
        - /actuator/**
        - /health
      exclude-rules:                  # 条件排除：URL 匹配 + body/header 条件
        # JSON 表达式过滤
        - url-pattern: /api/webhook
          body-json-expression: "stream == true"
        
        # Body 字符串匹配
        - url-pattern: /api/upload
          body-contains: "skip-log"
        
        # Header 条件过滤
        - url-pattern: /api/test
          header-name: X-Skip-Log
          header-value: "true"
        
        # 组合条件
        - url-pattern: /api/monitor
          body-json-expression: "type == \"health\""
          header-name: X-Internal
          header-value: "true"
```

### 代码配置方式

```java
import io.github.http.log.snap.server.spring.rule.ExcludeRule;
import io.github.http.log.snap.server.spring.rule.JsonExpressionEvaluator;
import io.github.http.log.snap.server.spring.rule.RuleBase;

@Configuration
public class HttpLoggingConfig {
    
    @Bean
    public HttpLoggingFilter httpLoggingFilter() {
        HttpLoggingFilter filter = new HttpLoggingFilter();
        
        // 添加条件排除规则
        ExcludeRule rule1 = new ExcludeRule();
        rule1.setUrlPattern("/api/webhook");
        rule1.setBodyCondition(body -> {
            // 使用 JSON 表达式求值器
            return JsonExpressionEvaluator.evaluate(body, "stream == true");
        });
        filter.addExcludeRule(rule1);
        
        // 添加 header 条件
        ExcludeRule rule2 = new ExcludeRule();
        rule2.setUrlPattern("/api/test");
        rule2.setHeaderCondition((RuleBase.HeaderCondition condition) -> 
            "X-Skip-Log".equalsIgnoreCase(condition.getName()) 
            && "true".equals(condition.getValue())
        );
        filter.addExcludeRule(rule2);
        
        return filter;
    }
}
```

> 💡 **提示：** `ExcludeRule` 和 `SseRule` 都继承自 `RuleBase`，共享相同的字段和方法。`HeaderCondition` 是 `RuleBase` 的内部类。

---

---

## SSE 响应规则

SSE（Server-Sent Events）是一种服务器向客户端推送数据的 Web 标准，使用长连接进行流式传输。由于 SSE 响应是持续推送的流，不适合完整记录响应体。

HTTP Log Snap 提供了 **SSE 响应规则**机制，允许您配置规则来匹配 SSE 响应，**只记录请求报文，不记录响应报文**（响应仅记录状态码）。

### 基本用法

SSE 响应规则与条件排除规则使用相同的规则基类（`RuleBase`），支持基于 URL + body/header 条件的灵活匹配。当请求匹配 SSE 规则时，会记录完整的请求信息（包括请求头、请求体），但只记录响应的状态码，不记录响应头和响应体。

**工作原理：**

1. 请求阶段：正常记录完整的请求信息
2. 响应阶段：检查是否匹配 SSE 规则
   - 如果匹配：只记录响应状态码，跳过响应头和响应体
   - 如果不匹配：正常记录完整的响应信息

> 💡 **提示：** SSE 响应规则和条件排除规则共享相同的配置结构和匹配逻辑，只是处理方式不同：
> - **排除规则**（`exclude-rules`）：完全排除日志记录（请求和响应都不记录）
> - **SSE 规则**（`sse-rules`）：只记录请求，不记录响应（响应只记录状态码）

### 配置示例

**方式1：通过 URL 模式匹配（推荐）**

```yaml
newbie:
  http:
    logging:
      sse-rules:
        # 匹配 SSE 相关的 URL 路径，只记录请求
        - url-pattern: /api/events/**
        - url-pattern: /api/stream/**
        - url-pattern: /api/sse/**
```

**方式2：通过请求头匹配**

```yaml
newbie:
  http:
    logging:
      sse-rules:
        # 通过 Accept header 匹配 SSE 请求
        - url-pattern: /**
          header-name: Accept
          header-value-contains: "text/event-stream"
```

**方式3：通过 JSON 表达式匹配**

```yaml
newbie:
  http:
    logging:
      sse-rules:
        # 当请求体中的 stream 字段为 true 时，只记录请求
        - url-pattern: /api/webhook
          body-json-expression: "stream == true"
        
        # 当请求体中的 type 字段为 "sse" 时，只记录请求
        - url-pattern: /api/notifications
          body-json-expression: "type == \"sse\""
```

**方式4：组合条件匹配**

```yaml
newbie:
  http:
    logging:
      sse-rules:
        # URL 匹配 + header 条件
        - url-pattern: /api/events
          header-name: Accept
          header-value-contains: "text/event-stream"
        
        # URL 匹配 + body 条件
        - url-pattern: /api/stream
          body-contains: "stream"
```

### 完整配置示例

```yaml
newbie:
  http:
    logging:
      enabled: true
      sse-rules:
        # 简单 URL 匹配
        - url-pattern: /api/events/**
        
        # Header 条件匹配
        - url-pattern: /api/notifications
          header-name: Accept
          header-value-contains: "text/event-stream"
        
        # JSON 表达式匹配
        - url-pattern: /api/webhook
          body-json-expression: "stream == true"
        
        # 组合条件
        - url-pattern: /api/stream
          header-name: X-Stream-Type
          header-value: "sse"
          body-contains: "event"
```

### 代码配置方式

```java
import io.github.http.log.snap.server.spring.rule.RuleBase;
import io.github.http.log.snap.server.spring.rule.SseRule;

@Configuration
public class HttpLoggingConfig {
    
    @Bean
    public HttpLoggingFilter httpLoggingFilter() {
        HttpLoggingFilter filter = new HttpLoggingFilter();
        
        // 添加 SSE 响应规则
        SseRule rule1 = new SseRule();
        rule1.setUrlPattern("/api/events/**");
        filter.addSseRule(rule1);
        
        // 添加带 header 条件的 SSE 规则
        SseRule rule2 = new SseRule();
        rule2.setUrlPattern("/api/notifications");
        rule2.setHeaderCondition((RuleBase.HeaderCondition condition) -> 
            "Accept".equalsIgnoreCase(condition.getName()) 
            && condition.getValue() != null 
            && condition.getValue().contains("text/event-stream")
        );
        filter.addSseRule(rule2);
        
        return filter;
    }
}
```

> 💡 **提示：** `SseRule` 继承自 `RuleBase`，与 `ExcludeRule` 共享相同的字段和方法。两者都支持 URL 模式、body 条件和 header 条件。

### 规则优先级

SSE 响应规则的检查在响应记录阶段进行，优先级如下：

1. **排除规则**（`exclude-rules`）：如果匹配，完全不记录日志
2. **SSE 规则**（`sse-rules`）：如果匹配，只记录请求，不记录响应
3. **正常记录**：如果都不匹配，记录完整的请求和响应

### 与排除规则的区别

| 规则类型 | 匹配后的行为 | 规则类 |
|---------|------------|--------|
| `exclude-rules` | 完全不记录日志（请求和响应都不记录） | `ExcludeRule`（继承 `RuleBase`） |
| `sse-rules` | 只记录请求，不记录响应（响应只记录状态码） | `SseRule`（继承 `RuleBase`） |

### 规则类的继承关系

所有规则类都继承自 `RuleBase`，共享相同的字段和方法：

```
RuleBase（基类）
├── urlPattern: String
├── bodyCondition: Predicate<String>
├── headerCondition: Predicate<HeaderCondition>
└── HeaderCondition（内部类）
    ├── name: String
    └── value: String

ExcludeRule（排除规则）
└── 继承自 RuleBase

SseRule（SSE 响应规则）
└── 继承自 RuleBase
```

这种设计使得两种规则类型可以复用相同的匹配逻辑和配置结构，代码更加简洁和易于维护。

### 使用场景

- **SSE 流式响应**：Server-Sent Events 长连接流式传输，响应是持续推送的流
- **实时数据推送**：股票行情、实时通知等持续推送数据的接口
- **事件流**：服务器事件流、日志流等需要保持长连接的接口
- **大文件下载**：只需要记录请求信息，不需要记录响应体（可选场景）

### 日志输出示例

**匹配 SSE 规则的请求日志：**

```json
{
  "type": "HTTP_SERVER",
  "direction": "SERVER",
  "timestamp": 1704067200000,
  "duration_ms": 150,
  "request": {
    "method": "GET",
    "url": "http://localhost:8080/api/events",
    "headers": {
      "Accept": ["text/event-stream"],
      "Cache-Control": ["no-cache"]
    },
    "body": ""
  },
  "response": {
    "code": 200
    // 注意：响应头、响应体不记录
  }
}
```

**文本格式输出：**

```
15:42:31.100 --- START [SERVER] SSE 事件流 (total: 150ms) [client: 192.168.1.50:52341]
15:42:31.102 --> REQUEST START --------------------------------------------------->
GET http://localhost:8080/api/events HTTP/1.1
Accept: text/event-stream
Cache-Control: no-cache

15:42:31.250 <-- RESPONSE START <--------------------------------------------------
200 OK
// 注意：响应头、响应体不记录
15:42:31.250 <-- END RESPONSE (0ms)
```

### 最佳实践

1. **明确识别 SSE 接口**：使用 URL 模式匹配 SSE 相关的路径
2. **使用 Header 条件**：通过 `Accept: text/event-stream` header 精确匹配
3. **组合条件**：结合 URL 和 header 条件，提高匹配准确性
4. **避免误匹配**：确保规则不会匹配到非 SSE 接口

**推荐配置：**

```yaml
newbie:
  http:
    logging:
      sse-rules:
        # 方式1：URL 模式匹配（适用于 SSE 接口路径明确的情况）
        - url-pattern: /api/events/**
        
        # 方式2：Header 条件匹配（更精确，推荐）
        - url-pattern: /**
          header-name: Accept
          header-value-contains: "text/event-stream"
        
        # 方式3：组合条件（最精确）
        - url-pattern: /api/stream/**
          header-name: Accept
          header-value-contains: "text/event-stream"
```

---

## 已知限制

### 流式响应支持

HTTP Log Snap 针对 SSE（Server-Sent Events）流式响应提供了专门的规则支持（`sse-rules`），可以只记录请求报文，不记录响应体。

**对于其他类型的流式响应：**

如果您的应用中有其他类型的流式响应接口（如 WebSocket、长轮询等），建议：

1. **使用排除规则**（`exclude-rules`）：完全排除这些请求的日志记录
2. **使用 SSE 响应规则**（`sse-rules`）：如果响应格式类似 SSE，可以只记录请求

**示例：处理 WebSocket 握手请求**

```yaml
newbie:
  http:
    logging:
      sse-rules:
        # WebSocket 握手请求，只记录请求
        - url-pattern: /ws/**
          header-name: Upgrade
          header-value: "websocket"
```

**示例：处理长轮询请求**

```yaml
newbie:
  http:
    logging:
      exclude-rules:
        # 长轮询请求，完全排除
        - url-pattern: /api/poll/**
          header-name: X-Polling
          header-value: "true"
```
