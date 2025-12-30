package io.github.http.log.snap.server.spring.rule;

import lombok.Data;

import java.util.function.Predicate;

/**
 * 规则基类：支持基于 URL + body/header 的条件判断
 *
 * @author http-logging
 */
@Data
public abstract class RuleBase {

    /**
     * URL 模式（支持 ant 风格）
     */
    private String urlPattern;

    /**
     * Body 条件判断
     * 参数为请求体的字符串内容
     */
    private Predicate<String> bodyCondition;

    /**
     * Header 条件判断
     */
    private Predicate<HeaderCondition> headerCondition;

    /**
     * Header 条件判断的辅助类
     */
    @Data
    public static class HeaderCondition {
        private final String name;
        private final String value;

        public HeaderCondition(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}

