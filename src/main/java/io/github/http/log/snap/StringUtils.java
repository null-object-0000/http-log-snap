package io.github.http.log.snap;

/**
 * 字符串工具类
 * 提供常用的字符串判断方法
 */
public final class StringUtils {

    private StringUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 判断字符串是否非空白
     * <p>
     * 非空白指：字符串不为 null 且去除首尾空白字符后不为空
     *
     * @param str 待判断的字符串
     * @return 如果字符串非空白返回 true，否则返回 false
     */
    public static boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * 判断字符串是否为空白
     * <p>
     * 空白指：字符串为 null 或去除首尾空白字符后为空
     *
     * @param str 待判断的字符串
     * @return 如果字符串为空白返回 true，否则返回 false
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}

