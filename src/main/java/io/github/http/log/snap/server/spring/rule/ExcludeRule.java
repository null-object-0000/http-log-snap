package io.github.http.log.snap.server.spring.rule;

/**
 * 排除规则：支持基于 URL + body/header 的条件判断
 * <p>
 * 使用示例：
 * <pre>
 * ExcludeRule rule = new ExcludeRule();
 * rule.setUrlPattern("/api/webhook");
 * rule.setBodyCondition(body -> body.contains("test"));
 * rule.setHeaderCondition(condition -> "X-Skip-Log".equals(condition.getName()));
 * </pre>
 *
 * @author http-logging
 */
public class ExcludeRule extends RuleBase {
}

