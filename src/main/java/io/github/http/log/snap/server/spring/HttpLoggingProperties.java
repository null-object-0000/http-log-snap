package io.github.http.log.snap.server.spring;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

import java.util.Set;

/**
 * HTTP 日志记录配置属性
 *
 * @author http-logging
 */
@Data
@ConfigurationProperties(prefix = "mc.http.logging")
public class HttpLoggingProperties {

    /**
     * 是否启用 HTTP 日志记录
     */
    private boolean enabled = false;

    /**
     * 是否记录请求体
     */
    private boolean includeRequestBody = true;

    /**
     * 是否记录响应体
     */
    private boolean includeResponseBody = true;

    /**
     * 是否记录请求头
     */
    private boolean includeHeaders = true;

    /**
     * 请求体/响应体最大记录长度（字节，-1 表示无限制）
     */
    private int maxPayloadLength = -1;

    /**
     * 日志格式：json 或 text
     */
    private String format = "json";

    /**
     * 是否包含完整的事件序列（仅 JSON 格式有效）
     * 开启后会在 timing.events 中记录所有事件的详细信息
     */
    private boolean includeEvents = false;

    /**
     * 需要脱敏的请求头名称
     */
    private Set<String> headersToRedact;

    /**
     * 需要脱敏的查询参数名称
     */
    private Set<String> queryParamsToRedact;

    /**
     * 需要排除的 URL 模式（支持 ant 风格）
     */
    private String[] excludePatterns;

    /**
     * Filter 顺序（默认最高优先级）
     */
    private int filterOrder = Ordered.HIGHEST_PRECEDENCE + 10;
}

