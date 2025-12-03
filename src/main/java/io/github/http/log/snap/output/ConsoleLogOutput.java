package io.github.http.log.snap.output;

import io.github.http.log.snap.HttpLogData;
import lombok.NonNull;

import java.io.PrintStream;

/**
 * 控制台日志输出实现
 * 将 HTTP 日志输出到标准输出/错误输出
 *
 * @author http-logging
 */
public class ConsoleLogOutput implements HttpLogOutput {

    private final PrintStream out;
    private final PrintStream err;

    /**
     * 使用默认的标准输出和标准错误输出
     */
    public ConsoleLogOutput() {
        this(System.out, System.err);
    }

    /**
     * 使用指定的输出流
     */
    public ConsoleLogOutput(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    @Override
    public void output(@NonNull HttpLogData data, @NonNull String formattedLog) {
        out.println(formattedLog);
    }

    @Override
    public void outputError(@NonNull HttpLogData data, @NonNull String formattedLog, @NonNull Throwable error) {
        err.println(formattedLog);
        error.printStackTrace(err);
    }

    @Override
    public String getName() {
        return "Console";
    }
}

