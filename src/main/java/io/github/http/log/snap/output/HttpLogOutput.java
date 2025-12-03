package io.github.http.log.snap.output;

import io.github.http.log.snap.HttpLogData;
import lombok.NonNull;

/**
 * HTTP 日志输出接口
 * 定义日志的输出目标，允许用户自定义日志输出到不同的目的地
 * <p>
 * 内置实现：
 * <ul>
 *   <li>{@link Slf4jLogOutput} - 输出到 SLF4J 日志框架</li>
 *   <li>{@link ConsoleLogOutput} - 输出到控制台</li>
 *   <li>{@link CompositeLogOutput} - 组合多个输出</li>
 * </ul>
 * <p>
 * 自定义实现示例（输出到 Kafka）：
 * <pre>
 * public class KafkaLogOutput implements HttpLogOutput {
 *     private final KafkaProducer producer;
 *     private final String topic;
 *
 *     &#64;Override
 *     public void output(HttpLogData data, String formattedLog) {
 *         producer.send(new ProducerRecord<>(topic, formattedLog));
 *     }
 *
 *     &#64;Override
 *     public void outputError(HttpLogData data, String formattedLog, Throwable error) {
 *         producer.send(new ProducerRecord<>(topic + "-error", formattedLog));
 *     }
 * }
 * </pre>
 *
 * @author http-logging
 */
public interface HttpLogOutput {

    /**
     * 输出正常日志
     *
     * @param data         原始日志数据
     * @param formattedLog 格式化后的日志字符串
     */
    void output(@NonNull HttpLogData data, @NonNull String formattedLog);

    /**
     * 输出错误日志（带异常信息）
     *
     * @param data         原始日志数据
     * @param formattedLog 格式化后的日志字符串
     * @param error        异常信息
     */
    void outputError(@NonNull HttpLogData data, @NonNull String formattedLog, @NonNull Throwable error);

    /**
     * 获取输出名称
     *
     * @return 输出名称
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * 检查输出是否可用
     *
     * @return 是否可用
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 关闭输出（释放资源）
     */
    default void close() {
        // 默认无操作
    }
}

