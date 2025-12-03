package io.github.http.log.snap.output;

import io.github.http.log.snap.HttpLogData;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 组合日志输出实现
 * 将日志同时输出到多个目标
 * <p>
 * 使用示例：
 * <pre>
 * HttpLogOutput output = CompositeLogOutput.of(
 *     new Slf4jLogOutput(),
 *     new KafkaLogOutput(producer, "http-logs"),
 *     new ElasticsearchLogOutput(client, "http-logs")
 * );
 * </pre>
 *
 * @author http-logging
 */
public class CompositeLogOutput implements HttpLogOutput {

    private final List<HttpLogOutput> outputs;

    /**
     * 使用指定的输出列表
     */
    public CompositeLogOutput(List<HttpLogOutput> outputs) {
        this.outputs = new ArrayList<>(outputs);
    }

    /**
     * 使用可变参数创建
     */
    public static CompositeLogOutput of(HttpLogOutput... outputs) {
        return new CompositeLogOutput(Arrays.asList(outputs));
    }

    /**
     * 添加输出
     */
    public CompositeLogOutput add(HttpLogOutput output) {
        this.outputs.add(output);
        return this;
    }

    /**
     * 移除输出
     */
    public CompositeLogOutput remove(HttpLogOutput output) {
        this.outputs.remove(output);
        return this;
    }

    @Override
    public void output(@NonNull HttpLogData data, @NonNull String formattedLog) {
        for (HttpLogOutput output : outputs) {
            if (output.isEnabled()) {
                try {
                    output.output(data, formattedLog);
                } catch (Exception e) {
                    // 某个输出失败不影响其他输出
                    System.err.println("Failed to output log to " + output.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void outputError(@NonNull HttpLogData data, @NonNull String formattedLog, @NonNull Throwable error) {
        for (HttpLogOutput output : outputs) {
            if (output.isEnabled()) {
                try {
                    output.outputError(data, formattedLog, error);
                } catch (Exception e) {
                    System.err.println("Failed to output error log to " + output.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public String getName() {
        return "Composite[" + outputs.size() + " outputs]";
    }

    @Override
    public boolean isEnabled() {
        return outputs.stream().anyMatch(HttpLogOutput::isEnabled);
    }

    @Override
    public void close() {
        for (HttpLogOutput output : outputs) {
            try {
                output.close();
            } catch (Exception e) {
                System.err.println("Failed to close output " + output.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * 获取输出数量
     */
    public int size() {
        return outputs.size();
    }
}

