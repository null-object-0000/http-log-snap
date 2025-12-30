package io.github.http.log.snap.server.spring.rule;

/**
 * SSE 响应规则：支持基于 URL + body/header 的条件判断
 * 如果匹配规则，则只记录请求报文，不记录响应报文
 * <p>
 * 使用示例：
 * <pre>
 * SseRule rule = new SseRule();
 * rule.setUrlPattern("/api/events");
 * rule.setBodyCondition(body -> body.contains("stream"));
 * rule.setHeaderCondition(condition -> "Accept".equals(condition.getName()) 
 *     && condition.getValue().contains("text/event-stream"));
 * </pre>
 *
 * @author http-logging
 */
public class SseRule extends RuleBase {
}

