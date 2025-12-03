package io.github.http.log.snap.demo.controller;

import io.github.http.log.snap.HttpRequestLogger;
import io.github.http.log.snap.server.spring.HttpRequestLoggerHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订单接口示例
 * 演示如何添加更多扩展信息
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /**
     * 创建订单
     */
    @PostMapping
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> order) {
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger != null) {
            // 设置接口名称和扩展信息
            logger.setInterfaceName("创建订单")
                  .putExtra("orderId", "ORD" + System.currentTimeMillis())
                  .putExtra("userId", order.get("userId"))
                  .putExtra("productId", order.get("productId"))
                  .putExtra("channel", "web");
        }

        // 模拟创建订单
        return Map.of(
                "code", 0,
                "message", "订单创建成功",
                "data", Map.of(
                        "orderId", "ORD" + System.currentTimeMillis(),
                        "status", "CREATED",
                        "amount", order.get("amount")
                )
        );
    }
}

