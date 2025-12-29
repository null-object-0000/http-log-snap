package io.github.http.log.snap;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * URL 规范化工具类
 * 用于将 URL 中的动态参数（如数字 ID）替换为占位符，降低监控系统中的标签基数
 * <p>
 * 使用示例：
 * <pre>
 * // 基本用法：所有数字替换为 {id}
 * String normalized = UrlNormalizer.normalize("https://api.example.com/users/123/orders/456");
 * // 结果: "https://api.example.com/users/{id}/orders/{id}"
 *
 * // 多个占位符：为不同位置的数字指定不同的占位符名称
 * String url = "https://showopen.maoyan.com/myshow/open/api/info/2434420/1459635/seats";
 * String normalized = UrlNormalizer.normalize(url, "{showId}", "{ticketId}");
 * // 结果: "https://showopen.maoyan.com/myshow/open/api/info/{showId}/{ticketId}/seats"
 *
 * // 占位符少于数字数量：超出部分使用默认 {id}
 * String url2 = "https://api.example.com/info/123/456/789";
 * String normalized2 = UrlNormalizer.normalize(url2, "{showId}", "{ticketId}");
 * // 结果: "https://api.example.com/info/{showId}/{ticketId}/{id}"
 *
 * // 占位符多于数字数量：多余的占位符会被忽略
 * String url3 = "https://api.example.com/info/123";
 * String normalized3 = UrlNormalizer.normalize(url3, "{showId}", "{ticketId}", "{extraId}");
 * // 结果: "https://api.example.com/info/{showId}"
 * </pre>
 *
 * @author http-logging
 */
public class UrlNormalizer {

    /**
     * 默认的数字 ID 模式：匹配路径中的数字段
     */
    private static final Pattern DEFAULT_ID_PATTERN = Pattern.compile("/\\d+");

    /**
     * 默认的占位符
     */
    private static final String DEFAULT_PLACEHOLDER = "{id}";

    /**
     * 规范化 URL，将路径中的所有数字 ID 替换为占位符
     * <p>
     * 如果不提供占位符，所有数字将替换为默认的 {id} 占位符
     * <p>
     * 如果提供占位符，将按顺序使用：
     * <ul>
     *   <li>占位符数量少于数字数量：超出部分使用默认的 {id}</li>
     *   <li>占位符数量多于数字数量：多余的占位符会被忽略</li>
     * </ul>
     * <p>
     * 示例：
     * <ul>
     *   <li>normalize("/api/info/123/456") -> "/api/info/{id}/{id}"</li>
     *   <li>normalize("/api/info/123/456", "{showId}", "{ticketId}") -> "/api/info/{showId}/{ticketId}"</li>
     *   <li>normalize("/api/info/123/456/789", "{showId}") -> "/api/info/{showId}/{id}/{id}"</li>
     * </ul>
     *
     * @param url         原始 URL（不包含 query 参数）
     * @param placeholders 可选的占位符数组，按顺序对应路径中的数字 ID
     * @return 规范化后的 URL，如果输入为 null 或空字符串则返回原值
     */
    @Nullable
    public static String normalize(@Nullable String url, String... placeholders) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        // 如果没有提供占位符，使用默认的 {id}
        if (placeholders == null || placeholders.length == 0) {
            return normalize(url, DEFAULT_ID_PATTERN, DEFAULT_PLACEHOLDER);
        }

        try {
            URI uri = URI.create(url);
            String path = uri.getPath();

            // 使用正则表达式查找所有数字段的位置
            java.util.regex.Matcher matcher = DEFAULT_ID_PATTERN.matcher(path);
            StringBuilder normalizedPath = new StringBuilder();
            int lastEnd = 0;
            int placeholderIndex = 0;

            while (matcher.find()) {
                // 添加匹配前的部分
                normalizedPath.append(path, lastEnd, matcher.start());

                // 选择占位符：如果还有自定义占位符就用自定义的，否则用默认的
                String placeholder;
                if (placeholderIndex < placeholders.length) {
                    placeholder = placeholders[placeholderIndex];
                    placeholderIndex++;
                } else {
                    placeholder = DEFAULT_PLACEHOLDER;
                }

                // 添加占位符
                normalizedPath.append("/").append(placeholder);

                lastEnd = matcher.end();
            }

            // 添加剩余部分
            if (lastEnd < path.length()) {
                normalizedPath.append(path, lastEnd, path.length());
            }

            // 重建 URL（保留协议、主机、端口等）
            StringBuilder normalizedUrl = new StringBuilder();
            normalizedUrl.append(uri.getScheme()).append("://").append(uri.getHost());
            if (uri.getPort() != -1) {
                normalizedUrl.append(":").append(uri.getPort());
            }
            normalizedUrl.append(normalizedPath);

            return normalizedUrl.toString();
        } catch (Exception e) {
            // 如果 URI 解析失败，使用简单的正则替换作为降级方案
            // 降级方案中，只使用第一个占位符或默认占位符
            String fallbackPlaceholder = placeholders[0];
            return DEFAULT_ID_PATTERN.matcher(url).replaceAll("/" + fallbackPlaceholder);
        }
    }

    /**
     * 规范化 URL，使用自定义的模式和占位符
     *
     * @param url         原始 URL（不包含 query 参数）
     * @param pattern     用于匹配需要替换的模式
     * @param placeholder 替换后的占位符
     * @return 规范化后的 URL，如果输入为 null 或空字符串则返回原值
     */
    @Nullable
    public static String normalize(@Nullable String url, Pattern pattern, String placeholder) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        try {
            URI uri = URI.create(url);
            String path = uri.getPath();

            // 使用自定义模式替换路径中的匹配项
            String normalizedPath = pattern.matcher(path).replaceAll("/" + placeholder);

            // 重建 URL（保留协议、主机、端口等）
            StringBuilder normalizedUrl = new StringBuilder();
            normalizedUrl.append(uri.getScheme()).append("://").append(uri.getHost());
            if (uri.getPort() != -1) {
                normalizedUrl.append(":").append(uri.getPort());
            }
            normalizedUrl.append(normalizedPath);

            return normalizedUrl.toString();
        } catch (Exception e) {
            // 如果 URI 解析失败，使用简单的正则替换作为降级方案
            return pattern.matcher(url).replaceAll("/" + placeholder);
        }
    }

    /**
     * 规范化 URL，使用自定义的正则表达式字符串
     *
     * @param url           原始 URL（不包含 query 参数）
     * @param regexPattern  正则表达式模式（字符串形式）
     * @param placeholder   替换后的占位符
     * @return 规范化后的 URL，如果输入为 null 或空字符串则返回原值
     */
    @Nullable
    public static String normalize(@Nullable String url, String regexPattern, String placeholder) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        return normalize(url, Pattern.compile(regexPattern), placeholder);
    }

}

