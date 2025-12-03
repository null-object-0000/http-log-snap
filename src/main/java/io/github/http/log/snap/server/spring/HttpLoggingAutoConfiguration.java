package io.github.http.log.snap.server.spring;

import io.github.http.log.snap.formatter.JsonHttpLogFormatter;
import io.github.http.log.snap.formatter.TextHttpLogFormatter;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;

/**
 * HTTP 日志记录自动配置
 * <p>
 * 通过配置属性控制日志记录行为：
 * <pre>
 * mc.http.logging.enabled=true                    # 是否启用
 * mc.http.logging.include-request-body=true       # 是否记录请求体
 * mc.http.logging.include-response-body=true      # 是否记录响应体
 * mc.http.logging.include-headers=true            # 是否记录请求头
 * mc.http.logging.max-payload-length=10240        # 最大记录长度
 * mc.http.logging.format=json                     # 日志格式：json/text
 * mc.http.logging.exclude-patterns=/health,/actuator/**  # 排除的 URL
 * </pre>
 *
 * @author http-logging
 */
@Configuration
@ConditionalOnClass({Filter.class})
@ConditionalOnProperty(prefix = "mc.http.logging", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(HttpLoggingAutoConfiguration.HttpLoggingProperties.class)
public class HttpLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HttpLoggingFilter.class)
    public FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilterRegistration(
            HttpLoggingProperties properties) {

        HttpLoggingFilter filter = new HttpLoggingFilter();

        // 配置 Filter
        filter.setIncludeRequestBody(properties.isIncludeRequestBody());
        filter.setIncludeResponseBody(properties.isIncludeResponseBody());
        filter.setIncludeHeaders(properties.isIncludeHeaders());
        filter.setMaxPayloadLength(properties.getMaxPayloadLength());

        // 配置格式化器（TextHttpLogFormatter 会根据 HttpDirection 自动选择格式）
        if ("text".equalsIgnoreCase(properties.getFormat())) {
            filter.setFormatter(new TextHttpLogFormatter());
        } else {
            filter.setFormatter(new JsonHttpLogFormatter());
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
    @ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
    public WebMvcConfigurer httpLoggingWebMvcConfigurer(HttpLoggingHandlerInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/**");
            }
        };
    }

    /**
     * HTTP 日志记录配置属性
     */
    @ConfigurationProperties(prefix = "mc.http.logging")
    public static class HttpLoggingProperties {

        /**
         * 是否启用 HTTP 日志记录
         */
        private boolean enabled = false;

        /**
         * 是否记录请求体
         */
        private boolean includeRequestBody = true;

        /**
         * 是否记录响应体
         */
        private boolean includeResponseBody = true;

        /**
         * 是否记录请求头
         */
        private boolean includeHeaders = true;

        /**
         * 请求体/响应体最大记录长度（字节）
         */
        private int maxPayloadLength = 10 * 1024;

        /**
         * 日志格式：json 或 text
         */
        private String format = "json";

        /**
         * 需要脱敏的请求头名称
         */
        private Set<String> headersToRedact;

        /**
         * 需要脱敏的查询参数名称
         */
        private Set<String> queryParamsToRedact;

        /**
         * 需要排除的 URL 模式（支持 ant 风格）
         */
        private String[] excludePatterns;

        /**
         * Filter 顺序（默认最高优先级）
         */
        private int filterOrder = Ordered.HIGHEST_PRECEDENCE + 10;

        // Getters and Setters

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isIncludeRequestBody() {
            return includeRequestBody;
        }

        public void setIncludeRequestBody(boolean includeRequestBody) {
            this.includeRequestBody = includeRequestBody;
        }

        public boolean isIncludeResponseBody() {
            return includeResponseBody;
        }

        public void setIncludeResponseBody(boolean includeResponseBody) {
            this.includeResponseBody = includeResponseBody;
        }

        public boolean isIncludeHeaders() {
            return includeHeaders;
        }

        public void setIncludeHeaders(boolean includeHeaders) {
            this.includeHeaders = includeHeaders;
        }

        public int getMaxPayloadLength() {
            return maxPayloadLength;
        }

        public void setMaxPayloadLength(int maxPayloadLength) {
            this.maxPayloadLength = maxPayloadLength;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public Set<String> getHeadersToRedact() {
            return headersToRedact;
        }

        public void setHeadersToRedact(Set<String> headersToRedact) {
            this.headersToRedact = headersToRedact;
        }

        public Set<String> getQueryParamsToRedact() {
            return queryParamsToRedact;
        }

        public void setQueryParamsToRedact(Set<String> queryParamsToRedact) {
            this.queryParamsToRedact = queryParamsToRedact;
        }

        public String[] getExcludePatterns() {
            return excludePatterns;
        }

        public void setExcludePatterns(String[] excludePatterns) {
            this.excludePatterns = excludePatterns;
        }

        public int getFilterOrder() {
            return filterOrder;
        }

        public void setFilterOrder(int filterOrder) {
            this.filterOrder = filterOrder;
        }
    }
}

