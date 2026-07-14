package com.chinacreator.gzcm.gateway.telemetry;

import com.chinacreator.gzcm.gateway.interceptor.TelemetryInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * P3-5 Telemetry 配置 — 注册 HTTP 拦截器。
 */
@Configuration
public class TelemetryConfig implements WebMvcConfigurer {

    private final TelemetryInterceptor telemetryInterceptor;

    public TelemetryConfig(TelemetryInterceptor telemetryInterceptor) {
        this.telemetryInterceptor = telemetryInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(telemetryInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/telemetry/**"); // 避免自身递归
    }
}
