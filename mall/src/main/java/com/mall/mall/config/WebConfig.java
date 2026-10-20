package com.mall.mall.config;

import com.mall.mall.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录鉴权拦截器。
 *
 * 拦截 /api/** 下所有接口，只放行「不需要登录」的几个：
 *   - 注册、登录：还没登录，自然不能要令牌；
 *   - /api/pay/callback：支付平台的回调通知，是第三方服务器打过来的，它不可能持有店家的令牌。
 *     真实项目里这个接口靠「验签」来确认身份（本项目学习期从简，见阶段七文档）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 免登录白名单 */
    private static final String[] WHITE_LIST = {
            "/api/merchants/register",
            "/api/merchants/login",
            "/api/pay/callback"
    };

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(WHITE_LIST);
    }
}
