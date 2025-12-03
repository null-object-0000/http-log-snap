package io.github.http.log.snap.demo;

import io.github.http.log.snap.HttpRequestLogger;
import io.github.http.log.snap.server.spring.HttpRequestLoggerHolder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Spring Boot 服务端接入示例
 * <p>
 * 运行此类后访问：
 * - GET  http://localhost:8080/api/users/123
 * - POST http://localhost:8080/api/orders (body: {"product":"iPhone","quantity":1})
 * <p>
 * 配置文件 src/test/resources/application.properties 已启用日志记录
 */
@SpringBootApplication
@RestController
public class SpringBootServerDemo {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootServerDemo.class, args);
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/api/users/{id}")
    public Map<String, Object> getUser(@PathVariable("id") Long id) {
        // 设置扩展信息（可选）
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger != null) {
            logger.setInterfaceName("获取用户信息")
                  .putExtra("userId", id);
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
        // 设置扩展信息（可选）
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger != null) {
            logger.setInterfaceName("创建订单")
                  .putExtra("orderId", "ORD" + System.currentTimeMillis())
                  .putExtra("product", request.get("product"));
        }

        return Map.of(
                "success", true,
                "orderId", "ORD" + System.currentTimeMillis(),
                "message", "订单创建成功"
        );
    }
}

