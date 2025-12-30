package io.github.http.log.snap.server.spring;

import io.github.http.log.snap.formatter.JsonHttpLogFormatter;
import io.github.http.log.snap.formatter.TextHttpLogFormatter;
import io.github.http.log.snap.server.spring.rule.ExcludeRule;
import io.github.http.log.snap.server.spring.rule.ExcludeRuleProperties;
import io.github.http.log.snap.server.spring.rule.JsonExpressionEvaluator;
import io.github.http.log.snap.server.spring.rule.RuleBase;
import io.github.http.log.snap.server.spring.rule.RulePropertiesBase;
import io.github.http.log.snap.server.spring.rule.SseRule;
import io.github.http.log.snap.server.spring.rule.SseRuleProperties;
import jakarta.servlet.Filter;
import lombok.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;


/**
 * HTTP 日志记录自动配置
 * <p>
 * 通过配置属性控制日志记录行为：
 * <pre>
 * newbie.http.logging.enabled=true                    # 是否启用
 * newbie.http.logging.include-request-body=true       # 是否记录请求体
 * newbie.http.logging.include-response-body=true      # 是否记录响应体
 * newbie.http.logging.include-headers=true            # 是否记录请求头
 * newbie.http.logging.max-payload-length=10240        # 最大记录长度
 * newbie.http.logging.format=json                     # 日志格式：json/text
 * newbie.http.logging.include-events=true             # 是否包含事件序列（仅 JSON 格式有效）
 * newbie.http.logging.exclude-patterns=/health,/actuator/**  # 排除的 URL
 * </pre>
 *
 * @author http-logging
 */
@Configuration
@ConditionalOnClass({Filter.class})
@ConditionalOnProperty(prefix = "newbie.http.logging", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(HttpLoggingProperties.class)
public class HttpLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HttpLoggingFilter.class)
    public FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilterRegistration(
            HttpLoggingProperties properties,
            ObjectProvider<HttpLogCustomizer> customizerProvider) {

        HttpLoggingFilter filter = new HttpLoggingFilter();

        // 配置 Filter
        filter.setIncludeRequestBody(properties.isIncludeRequestBody());
        filter.setIncludeResponseBody(properties.isIncludeResponseBody());
        filter.setIncludeHeaders(properties.isIncludeHeaders());
        filter.setMaxPayloadLength(properties.getMaxPayloadLength());

        // 配置定制器（如果有）
        customizerProvider.ifAvailable(filter::setCustomizer);

        // 配置格式化器（TextHttpLogFormatter 会根据 HttpDirection 自动选择格式）
        if ("json".equalsIgnoreCase(properties.getFormat())) {
            JsonHttpLogFormatter jsonFormatter = new JsonHttpLogFormatter();
            jsonFormatter.setIncludeEvents(properties.isIncludeEvents());
            filter.setFormatter(jsonFormatter);
        } else {
            filter.setFormatter(new TextHttpLogFormatter());
        }

        // 配置脱敏头
        if (properties.getHeadersToRedact() != null) {
            filter.setHeadersToRedact(properties.getHeadersToRedact());
        }

        // 配置脱敏查询参数
        if (properties.getQueryParamsToRedact() != null) {
            filter.setQueryParamsToRedact(properties.getQueryParamsToRedact());
        }

        // 配置排除模式
        if (properties.getExcludePatterns() != null) {
            for (String pattern : properties.getExcludePatterns()) {
                filter.addExcludePattern(pattern);
            }
        }

        // 配置条件排除规则
        if (properties.getExcludeRules() != null && !properties.getExcludeRules().isEmpty()) {
            List<ExcludeRule> excludeRules = convertExcludeRules(properties.getExcludeRules());
            for (ExcludeRule rule : excludeRules) {
                filter.addExcludeRule(rule);
            }
        }

        // 配置 SSE 响应规则
        if (properties.getSseRules() != null && !properties.getSseRules().isEmpty()) {
            List<SseRule> sseRules = convertSseRules(properties.getSseRules());
            for (SseRule rule : sseRules) {
                filter.addSseRule(rule);
            }
        }

        // 注册 Filter
        FilterRegistrationBean<HttpLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(properties.getFilterOrder());
        registration.setName("httpLoggingFilter");

        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(HttpLoggingHandlerInterceptor.class)
    @ConditionalOnClass(name = "org.springframework.web.servlet.HandlerInterceptor")
    public HttpLoggingHandlerInterceptor httpLoggingHandlerInterceptor() {
        return new HttpLoggingHandlerInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(HttpLoggingExceptionHandler.class)
    public HttpLoggingExceptionHandler httpLoggingExceptionHandler() {
        return new HttpLoggingExceptionHandler();
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
    public WebMvcConfigurer httpLoggingWebMvcConfigurer(HttpLoggingHandlerInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NonNull InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/**");
            }
        };
    }

    /**
     * 将配置属性转换为排除规则
     */
    private List<ExcludeRule> convertExcludeRules(List<ExcludeRuleProperties> propertiesList) {
        List<ExcludeRule> rules = new ArrayList<>();
        for (ExcludeRuleProperties props : propertiesList) {
            ExcludeRule rule = convertRule(props, ExcludeRule::new);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return rules;
    }

    /**
     * 将配置属性转换为 SSE 响应规则
     */
    private List<SseRule> convertSseRules(List<SseRuleProperties> propertiesList) {
        List<SseRule> rules = new ArrayList<>();
        for (SseRuleProperties props : propertiesList) {
            SseRule rule = convertRule(props, SseRule::new);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return rules;
    }

    /**
     * 通用的规则转换方法
     *
     * @param props 规则配置属性
     * @param ruleFactory 规则对象工厂方法
     * @param <T> 规则类型
     * @return 转换后的规则对象，如果配置无效则返回 null
     */
    private <T extends RuleBase> T convertRule(RulePropertiesBase props, java.util.function.Supplier<T> ruleFactory) {
        if (!StringUtils.hasText(props.getUrlPattern())) {
            return null; // 跳过没有 URL 模式的规则
        }
        
        T rule = ruleFactory.get();
        rule.setUrlPattern(props.getUrlPattern());
        
        // 构建 body 条件
        if (StringUtils.hasText(props.getBodyJsonExpression())) {
            // JSON 表达式（优先级最高）
            String jsonExpression = props.getBodyJsonExpression();
            rule.setBodyCondition(body -> JsonExpressionEvaluator.evaluate(body, jsonExpression));
        } else if (StringUtils.hasText(props.getBodyContains())) {
            String bodyContains = props.getBodyContains();
            rule.setBodyCondition(body -> body != null && body.contains(bodyContains));
        } else if (StringUtils.hasText(props.getBodyMatches())) {
            Pattern bodyPattern = Pattern.compile(props.getBodyMatches());
            rule.setBodyCondition(body -> body != null && bodyPattern.matcher(body).find());
        }
        
        // 构建 header 条件
        if (StringUtils.hasText(props.getHeaderName())) {
            String headerName = props.getHeaderName();
            Predicate<RuleBase.HeaderCondition> headerCondition = null;
            
            if (StringUtils.hasText(props.getHeaderValue())) {
                // 精确匹配 header 值
                String headerValue = props.getHeaderValue();
                headerCondition = condition -> headerName.equalsIgnoreCase(condition.getName()) 
                    && headerValue.equals(condition.getValue());
            } else if (StringUtils.hasText(props.getHeaderValueContains())) {
                // header 值包含指定字符串
                String headerValueContains = props.getHeaderValueContains();
                headerCondition = condition -> headerName.equalsIgnoreCase(condition.getName()) 
                    && condition.getValue() != null && condition.getValue().contains(headerValueContains);
            } else if (StringUtils.hasText(props.getHeaderValueMatches())) {
                // header 值匹配正则表达式
                Pattern headerPattern = Pattern.compile(props.getHeaderValueMatches());
                headerCondition = condition -> headerName.equalsIgnoreCase(condition.getName()) 
                    && condition.getValue() != null && headerPattern.matcher(condition.getValue()).find();
            } else {
                // 只检查 header 名称是否存在
                headerCondition = condition -> headerName.equalsIgnoreCase(condition.getName());
            }
            
            rule.setHeaderCondition(headerCondition);
        }
        
        return rule;
    }

}

