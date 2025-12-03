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

**搞定！** 自动记录所有 HTTP 请求/响应。

## 📝 日志输出效果

### 客户端日志

```
15:42:31.123 --- START [CLIENT] 获取用户信息 (total: 245ms)
15:42:31.180 --> DNS LOOKUP (50ms)
15:42:31.210 --> CONNECTING (30ms)
15:42:31.215 --> REQUEST START ------------------------------------------------>
GET https://api.example.com/users/123 HTTP/2
Authorization: ██

15:42:31.350 <-- RESPONSE START <-----------------------------------------------
HTTP/2 200 OK (130ms)
Content-Type: application/json

{"id":123,"name":"张三","email":"zhangsan@example.com"}
15:42:31.368 <-- END RESPONSE (18ms, 256-byte body)
```

### 服务端日志

```
15:42:31.100 --- START [SERVER] -> UserController.login (total: 156ms)
15:42:31.102 --> REQUEST START ------------------------------------------------>
POST http://localhost:8080/api/login HTTP/1.1
Content-Type: application/json

{"username":"zhangsan","password":"****"}
15:42:31.245 <-- RESPONSE START <-----------------------------------------------
200 OK (handler: 135ms)

{"code":0,"message":"success","data":{"token":"eyJ..."}}
15:42:31.256 <-- END RESPONSE (11ms, 128-byte body)
```

### JSON 格式（便于 ELK 分析）

```json
{
  "type": "HTTP_CLIENT",
  "duration_ms": 245,
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

## 📚 文档

- [📖 使用指南](docs/guide.md) - 完整的使用教程
- [🔧 高级用法](docs/advanced.md) - 自定义格式化器、输出目标、适配器

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License
