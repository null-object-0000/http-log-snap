package io.github.http.log.snap.server.spring;

import io.github.http.log.snap.formatter.JsonHttpLogFormatter;
import io.github.http.log.snap.formatter.TextHttpLogFormatter;
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
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


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

}

