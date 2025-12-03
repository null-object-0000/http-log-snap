package io.github.http.log.snap;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 日志上下文信息
 * 用于存储与当前 HTTP 请求相关的业务上下文，便于日志追踪和分析
 * <p>
 * 该类完全独立，不依赖任何业务代码
 *
 * @author http-logging
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpLogContext {

    /**
     * 接口名称/标识
     * 例如："用户登录接口"、"getUserInfo"
     */
    @Nullable
    private String interfaceName;

    /**
     * 追踪 ID（用于分布式追踪）
     * 例如：TraceId、RequestId
     */
    @Nullable
    private String traceId;

    /**
     * 跨度 ID（用于分布式追踪）
     */
    @Nullable
    private String spanId;

    /**
     * 过滤标签1（用于日志筛选）
     */
    @Nullable
    private String tag1;

    /**
     * 过滤标签2（用于日志筛选）
     */
    @Nullable
    private String tag2;

    /**
     * 扩展信息（用于存储任意自定义数据）
     */
    @Builder.Default
    private Map<String, Object> extras = new HashMap<>();

    /**
     * 设置标签
     */
    public HttpLogContext setTags(String tag1, String tag2) {
        this.tag1 = tag1;
        this.tag2 = tag2;
        return this;
    }

    /**
     * 添加扩展信息
     */
    public HttpLogContext putExtra(String key, Object value) {
        if (this.extras == null) {
            this.extras = new HashMap<>();
        }
        this.extras.put(key, value);
        return this;
    }

    /**
     * 获取扩展信息
     */
    @Nullable
    public Object getExtra(String key) {
        return (this.extras == null) ? null : this.extras.get(key);
    }

    /**
     * 创建空上下文
     */
    public static HttpLogContext empty() {
        return new HttpLogContext();
    }

    /**
     * 创建带接口名的上下文
     */
    public static HttpLogContext of(String interfaceName) {
        return HttpLogContext.builder().interfaceName(interfaceName).build();
    }

    /**
     * 创建带追踪信息的上下文
     */
    public static HttpLogContext ofTrace(String traceId, String spanId) {
        return HttpLogContext.builder().traceId(traceId).spanId(spanId).build();
    }
}

