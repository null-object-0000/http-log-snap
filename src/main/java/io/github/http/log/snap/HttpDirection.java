package io.github.http.log.snap;

import lombok.Getter;

/**
 * HTTP 请求方向枚举
 * 区分客户端（发起请求）和服务端（处理请求）两种场景
 *
 * @author http-logging
 */
@Getter
public enum HttpDirection {

    /**
     * 客户端（发起 HTTP 请求）
     * 事件流程：构建请求 → 发送请求 → 等待响应 → 接收响应
     */
    CLIENT("client", "HTTP Client"),

    /**
     * 服务端（处理 HTTP 请求）
     * 事件流程：接收请求 → 解析请求 → 业务处理 → 构建响应 → 发送响应
     */
    SERVER("server", "HTTP Server");

    private final String code;
    private final String description;

    HttpDirection(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 是否为客户端
     */
    public boolean isClient() {
        return this == CLIENT;
    }

    /**
     * 是否为服务端
     */
    public boolean isServer() {
        return this == SERVER;
    }

    @Override
    public String toString() {
        return code;
    }
}

