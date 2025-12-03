package io.github.http.log.snap.server.spring;

import io.github.http.log.snap.HttpRequestLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Nullable;

/**
 * Spring MVC Handler 拦截器
 * 用于记录 Controller 方法的执行时间和异常信息
 * <p>
 * 配合 {@link HttpLoggingFilter} 使用，可以更精确地记录业务处理时间
 * <p>
 * 使用示例：
 * <pre>
 * &#64;Configuration
 * public class WebMvcConfig implements WebMvcConfigurer {
 *     &#64;Override
 *     public void addInterceptors(InterceptorRegistry registry) {
 *         registry.addInterceptor(new HttpLoggingHandlerInterceptor())
 *                 .addPathPatterns("/**");
 *     }
 * }
 * </pre>
 *
 * @author http-logging
 */
@Slf4j
public class HttpLoggingHandlerInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger == null) {
            return true;
        }

        // 记录 Handler 信息
        if (handler instanceof HandlerMethod handlerMethod) {
            Class<?> handlerClass = handlerMethod.getBeanType();
            String methodName = handlerMethod.getMethod().getName();
            logger.recordHandlerStart(handlerClass, methodName);
        } else {
            logger.recordHandlerStart();
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           @Nullable ModelAndView modelAndView) {
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger != null) {
            logger.recordHandlerEnd();
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
        HttpRequestLogger logger = HttpRequestLoggerHolder.get();
        if (logger == null) {
            return;
        }

        // 记录异常（如果有）
        if (ex != null) {
            logger.recordHandlerException(ex);
        }
        // 注：responseCommitted 由 HttpLoggingFilter 记录
    }
}
