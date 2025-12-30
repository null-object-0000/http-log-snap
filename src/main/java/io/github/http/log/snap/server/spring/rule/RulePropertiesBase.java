package io.github.http.log.snap.server.spring.rule;

import lombok.Data;

/**
 * 规则配置属性基类
 *
 * @author http-logging
 */
@Data
public abstract class RulePropertiesBase {

    /**
     * URL 模式（支持 ant 风格）
     */
    private String urlPattern;

    /**
     * Body 必须包含的字符串
     */
    private String bodyContains;

    /**
     * Body 必须匹配的正则表达式
     */
    private String bodyMatches;

    /**
     * Body JSON 表达式
     */
    private String bodyJsonExpression;

    /**
     * Header 名称
     */
    private String headerName;

    /**
     * Header 值
     */
    private String headerValue;

    /**
     * Header 值必须包含的字符串
     */
    private String headerValueContains;

    /**
     * Header 值必须匹配的正则表达式
     */
    private String headerValueMatches;
}

