package io.github.http.log.snap.spi;

import io.github.http.log.snap.client.HttpClientAdapter;
import io.github.http.log.snap.formatter.HttpLogFormatter;
import io.github.http.log.snap.output.HttpLogOutput;
import io.github.http.log.snap.server.HttpServerAdapter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP 日志组件注册中心
 * 支持通过 SPI 机制自动发现和加载自定义组件，也支持手动注册
 * <p>
 * 使用示例：
 * <pre>
 * // 手动注册
 * HttpLoggingRegistry.registerFormatter("custom", new MyFormatter());
 * HttpLoggingRegistry.registerAdapter(new MyClientAdapter());
 * HttpLoggingRegistry.registerOutput("kafka", new KafkaLogOutput());
 *
 * // 获取组件
 * HttpLogFormatter formatter = HttpLoggingRegistry.getFormatter("custom");
 * HttpClientAdapter adapter = HttpLoggingRegistry.getAdapterFor(OkHttpClient.class);
 * </pre>
 * <p>
 * SPI 自动发现：
 * 在 META-INF/services/ 目录下创建对应的 SPI 文件：
 * <ul>
 *   <li>io.github.http.log.snap.formatter.HttpLogFormatter</li>
 *   <li>io.github.http.log.snap.client.HttpClientAdapter</li>
 *   <li>io.github.http.log.snap.output.HttpLogOutput</li>
 * </ul>
 *
 * @author http-logging
 */
@Slf4j
public final class HttpLoggingRegistry {

    private static final Map<String, HttpLogFormatter> formatters = new ConcurrentHashMap<>();
    private static final List<HttpClientAdapter> clientAdapters = new ArrayList<>();
    private static final List<HttpServerAdapter> serverAdapters = new ArrayList<>();
    private static final Map<String, HttpLogOutput> outputs = new ConcurrentHashMap<>();

    private static volatile boolean initialized = false;

    private HttpLoggingRegistry() {
        // 工具类，禁止实例化
    }

    // ==================== 初始化 ====================

    /**
     * 初始化注册中心（通过 SPI 加载组件）
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        // 加载格式化器
        ServiceLoader<HttpLogFormatter> formatterLoader = ServiceLoader.load(HttpLogFormatter.class);
        for (HttpLogFormatter formatter : formatterLoader) {
            registerFormatter(formatter.getFormatType(), formatter);
            log.debug("Loaded formatter via SPI: {}", formatter.getFormatType());
        }

        // 加载客户端适配器
        ServiceLoader<HttpClientAdapter> clientAdapterLoader = ServiceLoader.load(HttpClientAdapter.class);
        for (HttpClientAdapter adapter : clientAdapterLoader) {
            registerClientAdapter(adapter);
            log.debug("Loaded client adapter via SPI: {}", adapter.getClientName());
        }

        // 加载服务端适配器
        ServiceLoader<HttpServerAdapter> serverAdapterLoader = ServiceLoader.load(HttpServerAdapter.class);
        for (HttpServerAdapter adapter : serverAdapterLoader) {
            registerServerAdapter(adapter);
            log.debug("Loaded server adapter via SPI: {}", adapter.getFrameworkName());
        }

        // 加载日志输出
        ServiceLoader<HttpLogOutput> outputLoader = ServiceLoader.load(HttpLogOutput.class);
        for (HttpLogOutput output : outputLoader) {
            registerOutput(output.getName(), output);
            log.debug("Loaded log output via SPI: {}", output.getName());
        }

        initialized = true;
    }

    // ==================== 格式化器 ====================

    /**
     * 注册格式化器
     *
     * @param name      格式化器名称
     * @param formatter 格式化器实例
     */
    public static void registerFormatter(String name, HttpLogFormatter formatter) {
        formatters.put(name.toUpperCase(), formatter);
    }

    /**
     * 获取格式化器
     *
     * @param name 格式化器名称
     * @return 格式化器实例，不存在返回 null
     */
    @Nullable
    public static HttpLogFormatter getFormatter(String name) {
        return formatters.get(name.toUpperCase());
    }

    /**
     * 获取所有已注册的格式化器名称
     */
    public static Set<String> getFormatterNames() {
        return Collections.unmodifiableSet(formatters.keySet());
    }

    /**
     * 移除格式化器
     */
    public static void removeFormatter(String name) {
        formatters.remove(name.toUpperCase());
    }

    // ==================== 客户端适配器 ====================

    /**
     * 注册客户端适配器
     */
    public static synchronized void registerClientAdapter(HttpClientAdapter adapter) {
        clientAdapters.add(adapter);
        // 按优先级排序
        clientAdapters.sort(Comparator.comparingInt(HttpClientAdapter::getOrder));
    }

    /**
     * 注册客户端适配器（兼容旧方法名）
     *
     * @deprecated 使用 {@link #registerClientAdapter(HttpClientAdapter)} 代替
     */
    @Deprecated
    public static synchronized void registerAdapter(HttpClientAdapter adapter) {
        registerClientAdapter(adapter);
    }

    /**
     * 获取适用于指定客户端类型的适配器
     *
     * @param clientClass 客户端类
     * @return 适配器实例，不存在返回 null
     */
    @Nullable
    public static HttpClientAdapter getClientAdapterFor(Class<?> clientClass) {
        for (HttpClientAdapter adapter : clientAdapters) {
            if (adapter.supports(clientClass)) {
                return adapter;
            }
        }
        return null;
    }

    /**
     * 获取适用于指定客户端类型的适配器（兼容旧方法名）
     *
     * @deprecated 使用 {@link #getClientAdapterFor(Class)} 代替
     */
    @Deprecated
    @Nullable
    public static HttpClientAdapter getAdapterFor(Class<?> clientClass) {
        return getClientAdapterFor(clientClass);
    }

    /**
     * 获取所有已注册的客户端适配器
     */
    public static List<HttpClientAdapter> getClientAdapters() {
        return Collections.unmodifiableList(clientAdapters);
    }

    /**
     * 获取所有已注册的客户端适配器（兼容旧方法名）
     *
     * @deprecated 使用 {@link #getClientAdapters()} 代替
     */
    @Deprecated
    public static List<HttpClientAdapter> getAdapters() {
        return getClientAdapters();
    }

    /**
     * 移除客户端适配器
     */
    public static synchronized void removeClientAdapter(HttpClientAdapter adapter) {
        clientAdapters.remove(adapter);
    }

    // ==================== 服务端适配器 ====================

    /**
     * 注册服务端适配器
     */
    public static synchronized void registerServerAdapter(HttpServerAdapter adapter) {
        serverAdapters.add(adapter);
        // 按优先级排序
        serverAdapters.sort(Comparator.comparingInt(HttpServerAdapter::getOrder));
    }

    /**
     * 获取适用于指定服务端/框架类型的适配器
     *
     * @param serverClass 服务端类
     * @return 适配器实例，不存在返回 null
     */
    @Nullable
    public static HttpServerAdapter getServerAdapterFor(Class<?> serverClass) {
        for (HttpServerAdapter adapter : serverAdapters) {
            if (adapter.supports(serverClass)) {
                return adapter;
            }
        }
        return null;
    }

    /**
     * 获取所有已注册的服务端适配器
     */
    public static List<HttpServerAdapter> getServerAdapters() {
        return Collections.unmodifiableList(serverAdapters);
    }

    /**
     * 移除服务端适配器
     */
    public static synchronized void removeServerAdapter(HttpServerAdapter adapter) {
        serverAdapters.remove(adapter);
    }

    // ==================== 日志输出 ====================

    /**
     * 注册日志输出
     *
     * @param name   输出名称
     * @param output 输出实例
     */
    public static void registerOutput(String name, HttpLogOutput output) {
        outputs.put(name, output);
    }

    /**
     * 获取日志输出
     *
     * @param name 输出名称
     * @return 输出实例，不存在返回 null
     */
    @Nullable
    public static HttpLogOutput getOutput(String name) {
        return outputs.get(name);
    }

    /**
     * 获取所有已注册的输出名称
     */
    public static Set<String> getOutputNames() {
        return Collections.unmodifiableSet(outputs.keySet());
    }

    /**
     * 移除日志输出
     */
    public static void removeOutput(String name) {
        HttpLogOutput output = outputs.remove(name);
        if (output != null) {
            output.close();
        }
    }

    // ==================== 清理 ====================

    /**
     * 清空所有注册的组件
     */
    public static synchronized void clear() {
        formatters.clear();
        clientAdapters.clear();
        serverAdapters.clear();
        outputs.values().forEach(HttpLogOutput::close);
        outputs.clear();
        initialized = false;
    }
}

