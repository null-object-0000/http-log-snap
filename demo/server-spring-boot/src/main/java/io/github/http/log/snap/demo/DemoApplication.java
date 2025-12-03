package io.github.http.log.snap.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 服务端接入示例
 * 
 * 演示如何使用 HTTP Log Snap 记录服务端请求日志
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
        System.out.println("\n=== HTTP Log Snap - Spring Boot Server Demo ===");
        System.out.println("Server started at http://localhost:8080");
        System.out.println("\nTest endpoints:");
        System.out.println("  GET  http://localhost:8080/api/users");
        System.out.println("  GET  http://localhost:8080/api/users/123");
        System.out.println("  POST http://localhost:8080/api/users");
        System.out.println("  POST http://localhost:8080/api/orders\n");
    }
}

