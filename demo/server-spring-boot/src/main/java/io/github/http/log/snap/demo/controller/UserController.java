package io.github.http.log.snap.demo.controller;

import io.github.http.log.snap.HttpRequestLogger;
import io.github.http.log.snap.server.spring.HttpRequestLoggerHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户接口示例
 * 演示如何在 Controller 中设置扩展信息
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    /**
     * 获取用户列表
     */
    @GetMapping
    public List<Map<String, Object>> listUsers() {
        // 设置扩展信息（可选）
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger != null) {
            logger.setInterfaceName("获取用户列表");
        }

        return List.of(
                Map.of("id", 1, "name", "张三", "email", "zhangsan@example.com"),
                Map.of("id", 2, "name", "李四", "email", "lisi@example.com")
        );
    }

    /**
     * 获取单个用户
     */
    @GetMapping("/{id}")
    public Map<String, Object> getUser(@PathVariable Long id) {
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger != null) {
            logger.setInterfaceName("获取用户详情")
                  .putExtra("userId", id);
        }

        return Map.of(
                "id", id,
                "name", "张三",
                "email", "zhangsan@example.com",
                "phone", "13800138000"
        );
    }

    /**
     * 创建用户
     */
    @PostMapping
    public Map<String, Object> createUser(@RequestBody Map<String, Object> user) {
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger != null) {
            logger.setInterfaceName("创建用户")
                  .putExtra("username", user.get("name"));
        }

        // 模拟创建用户
        return Map.of(
                "code", 0,
                "message", "success",
                "data", Map.of(
                        "id", 12345,
                        "name", user.get("name")
                )
        );
    }
}

