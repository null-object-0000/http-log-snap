# Spring Boot 服务端接入示例

本示例演示如何在 Spring Boot 服务端中集成 HTTP Log Snap。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.null-object-0000</groupId>
    <artifactId>http-log-snap</artifactId>
    <version>0.0.1</version>
</dependency>
```

### 2. 配置 application.yml

```yaml
mc:
  http:
    logging:
      enabled: true
```

### 3. 在 Controller 中设置扩展信息（可选）

```java
@RestController
public class UserController {

    @PostMapping("/api/users")
    public User createUser(@RequestBody UserDTO dto) {
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger != null) {
            logger.setInterfaceName("创建用户")
                  .putExtra("userId", dto.getUserId())
                  .putExtra("channel", "web");
        }
        return userService.create(dto);
    }
}
```

## 运行示例

```bash
mvn spring-boot:run
```

## 测试接口

```bash
# 获取用户列表
curl http://localhost:8080/api/users

# 获取单个用户
curl http://localhost:8080/api/users/123

# 创建用户
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"王五","email":"wangwu@example.com"}'

# 创建订单（带更多扩展信息）
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":10086,"productId":"P001","amount":99.9}'
```

## 日志输出效果

```
15:42:31.100 --- LOG EXTRAS -------------------------------------------------------
{"orderId":"ORD1701234567890","userId":10086,"productId":"P001","channel":"web"}
15:42:31.100 --- START [SERVER] 创建订单 -> OrderController.createOrder (total: 56ms) [client: 127.0.0.1:52341]
15:42:31.102 --> REQUEST START --------------------------------------------------->
POST http://localhost:8080/api/orders HTTP/1.1
Content-Type: application/json

{"userId":10086,"productId":"P001","amount":99.9}
15:42:31.145 <-- RESPONSE START <--------------------------------------------------
200 OK (handler: 35ms)

{"code":0,"message":"订单创建成功","data":{"orderId":"ORD1701234567890","status":"CREATED"}}
15:42:31.156 <-- END RESPONSE (11ms, 89-byte body)
```

## 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `enabled` | 是否启用日志记录 | `true` |
| `format` | 日志格式：text 或 json | `text` |
| `include-request-body` | 是否记录请求体 | `true` |
| `include-response-body` | 是否记录响应体 | `true` |
| `max-payload-length` | 最大记录长度（字节） | `10240` |
| `exclude-patterns` | 排除的 URL 模式 | `[]` |
| `headers-to-redact` | 需要脱敏的请求头 | `[]` |
| `query-params-to-redact` | 需要脱敏的查询参数 | `[]` |

