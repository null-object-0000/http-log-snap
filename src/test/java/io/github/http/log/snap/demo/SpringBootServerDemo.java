package io.github.http.log.snap.demo;

import io.github.http.log.snap.HttpRequestLogger;
import io.github.http.log.snap.server.spring.HttpLogCustomizer;
import io.github.http.log.snap.server.spring.HttpRequestLoggerHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Spring Boot 服务端接入示例
 * <p>
 * 运行此类后访问：
 * - GET  http://localhost:8080/api/users/123
 * - POST http://localhost:8080/api/orders (body: {"product":"iPhone","quantity":1})
 * <p>
 * 配置文件 src/test/resources/application.yml 已启用日志记录
 */
@SpringBootApplication
@RestController
public class SpringBootServerDemo {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootServerDemo.class, args);
    }

    /**
     * 日志定制器示例
     * <p>
     * 在请求处理前自动添加 traceId 等通用信息
     */
    @Component
    static class MyHttpLogCustomizer implements HttpLogCustomizer {
        @Override
        public void customize(HttpRequestLogger logger, HttpServletRequest request) {
            // 从请求头获取 traceId，没有则生成一个
            String traceId = request.getHeader("X-Trace-Id");
            if (traceId == null) {
                traceId = UUID.randomUUID().toString().substring(0, 8);
            }
            logger.putExtra("traceId", traceId);

            // 可以根据路径设置接口名称
            String path = request.getRequestURI();
            if (path.startsWith("/api/users")) {
                logger.setInterfaceName("用户服务");
            } else if (path.startsWith("/api/orders")) {
                logger.setInterfaceName("订单服务");
            }
        }
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/api/users/{id}")
    public Map<String, Object> getUser(@PathVariable("id") Long id) {
        // 在 Controller 中添加业务相关的扩展信息（可选）
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger != null) {
            logger.putExtra("userId", id);
        }

        return Map.of(
                "id", id,
                "name", "张三",
                "email", "zhangsan@example.com"
        );
    }

    /**
     * 创建订单
     */
    @PostMapping("/api/orders")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> request) {
        // 在 Controller 中添加业务相关的扩展信息（可选）
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger != null) {
            logger.putExtra("orderId", "ORD" + System.currentTimeMillis())
                  .putExtra("product", request.get("product"));
        }

        return Map.of(
                "success", true,
                "orderId", "ORD" + System.currentTimeMillis(),
                "message", "订单创建成功"
        );
    }

    /**
     * 异常测试接口
     * GET http://localhost:8080/api/error
     */
    @GetMapping("/api/error")
    public Map<String, Object> testError() {
        throw new RuntimeException("测试异常：业务处理失败");
    }
}

