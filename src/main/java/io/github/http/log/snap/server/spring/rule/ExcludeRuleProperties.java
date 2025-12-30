package io.github.http.log.snap.server.spring.rule;

/**
 * 条件排除规则配置属性
 * <p>
 * 配置示例：
 * <pre>
 * newbie.http.logging.exclude-rules[0].url-pattern=/api/webhook
 * newbie.http.logging.exclude-rules[0].body-contains=test
 * newbie.http.logging.exclude-rules[0].header-name=X-Skip-Log
 * newbie.http.logging.exclude-rules[0].header-value=skip
 * </pre>
 *
 * @author http-logging
 */
public class ExcludeRuleProperties extends RulePropertiesBase {
}

