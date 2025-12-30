package io.github.http.log.snap.server.spring.rule;

/**
 * SSE 响应规则配置属性
 * <p>
 * 配置示例：
 * <pre>
 * newbie.http.logging.sse-rules[0].url-pattern=/api/events
 * newbie.http.logging.sse-rules[0].body-contains=stream
 * newbie.http.logging.sse-rules[0].header-name=Accept
 * newbie.http.logging.sse-rules[0].header-value-contains=text/event-stream
 * </pre>
 *
 * @author http-logging
 */
public class SseRuleProperties extends RulePropertiesBase {
}

