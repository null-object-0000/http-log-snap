package io.github.http.log.snap;

import lombok.NonNull;

/**
 * 日志字符串构建器
 * 提供流畅的 API 来构建格式化的日志字符串
 *
 * @author http-logging
 */
public final class LogStringBuilder implements Appendable, Comparable<LogStringBuilder>, CharSequence {

    private static final String CRLF = "\r\n";
    private static final String SPACE = " ";

    private final StringBuilder logs = new StringBuilder();

    // ==================== 基础追加方法 ====================

    public LogStringBuilder append(long value) {
        logs.append(value);
        return this;
    }

    public LogStringBuilder append(int value) {
        logs.append(value);
        return this;
    }

    public LogStringBuilder append(String str) {
        logs.append(str);
        return this;
    }

    public LogStringBuilder append(String format, Object... args) {
        return this.append(String.format(format, args));
    }

    public LogStringBuilder append(Object obj) {
        logs.append(obj);
        return this;
    }

    @Override
    public LogStringBuilder append(CharSequence csq) {
        logs.append(csq);
        return this;
    }

    @Override
    public LogStringBuilder append(CharSequence csq, int start, int end) {
        logs.append(csq, start, end);
        return this;
    }

    @Override
    public LogStringBuilder append(char c) {
        logs.append(c);
        return this;
    }

    // ==================== 格式化方法 ====================

    /**
     * 追加换行符
     */
    public LogStringBuilder line() {
        return this.append(CRLF);
    }

    /**
     * 追加空格
     */
    public LogStringBuilder space() {
        return this.append(SPACE);
    }

    /**
     * 追加字符串并换行
     */
    public LogStringBuilder appendLine(String str) {
        return this.append(str).line();
    }

    /**
     * 追加格式化字符串并换行
     */
    public LogStringBuilder appendLine(String format, Object... args) {
        return this.append(format, args).line();
    }

    /**
     * 追加字符串并加空格
     */
    public LogStringBuilder appendSpace(String str) {
        return this.append(str).space();
    }

    /**
     * 追加冒号分隔的键值对
     */
    public LogStringBuilder appendKeyValue(String key, Object value) {
        return this.append(key).append(": ").append(value);
    }

    /**
     * 追加冒号分隔的键值对并换行
     */
    public LogStringBuilder appendKeyValueLine(String key, Object value) {
        return this.appendKeyValue(key, value).line();
    }

    /**
     * 条件追加 - 当条件为 true 时追加
     */
    public LogStringBuilder appendIf(boolean condition, String str) {
        if (condition) {
            this.append(str);
        }
        return this;
    }

    /**
     * 条件追加格式化字符串 - 当条件为 true 时追加
     */
    public LogStringBuilder appendIf(boolean condition, String format, Object... args) {
        if (condition) {
            this.append(format, args);
        }
        return this;
    }

    /**
     * 非空追加 - 当字符串非空时追加
     */
    public LogStringBuilder appendIfNotBlank(String str) {
        if (str != null && !str.isBlank()) {
            this.append(str);
        }
        return this;
    }

    /**
     * 追加分隔线
     */
    public LogStringBuilder appendSeparator(char c, int length) {
        for (int i = 0; i < length; i++) {
            logs.append(c);
        }
        return this;
    }

    /**
     * 追加分隔线并换行
     */
    public LogStringBuilder appendSeparatorLine(char c, int length) {
        return this.appendSeparator(c, length).line();
    }

    // ==================== CharSequence 实现 ====================

    @Override
    public int length() {
        return logs.length();
    }

    @Override
    public char charAt(int index) {
        return logs.charAt(index);
    }

    @NonNull
    @Override
    public CharSequence subSequence(int start, int end) {
        return logs.subSequence(start, end);
    }

    @NonNull
    @Override
    public String toString() {
        return logs.toString();
    }

    // ==================== Comparable 实现 ====================

    @Override
    public int compareTo(@NonNull LogStringBuilder o) {
        return logs.compareTo(o.logs);
    }

    // ==================== 其他工具方法 ====================

    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return logs.isEmpty();
    }

    /**
     * 检查是否非空
     */
    public boolean isNotEmpty() {
        return !isEmpty();
    }

    /**
     * 清空内容
     */
    public LogStringBuilder clear() {
        logs.setLength(0);
        return this;
    }
}
