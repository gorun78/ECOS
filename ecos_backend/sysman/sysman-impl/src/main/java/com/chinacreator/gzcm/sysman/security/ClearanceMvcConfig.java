package com.chinacreator.gzcm.sysman.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置 — 注册 ClearanceInterceptor。
 */
@Configuration
public class ClearanceMvcConfig implements WebMvcConfigurer {

    private final ClearanceInterceptor clearanceInterceptor;

    public ClearanceMvcConfig(ClearanceInterceptor clearanceInterceptor) {
        this.clearanceInterceptor = clearanceInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(clearanceInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/**", "/api/v1/engine/**", "/api/health", "/health");
    }
}
