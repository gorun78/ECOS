package com.chinacreator.gzcm.services.buszhi.config;

import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * W02（详细设计-00 C.2.2，M0）— buszhi 信任链装配：显式构建并注册 {@link HeaderAuthInterceptor}。
 *
 * <p>ADR-15 S2 门禁：默认关闭（ecos.header-auth.enabled=true 开启）。S2 测试双绿
 * （TrustHeaderStripTest + HeaderAuthDenyTest）之前不得打开，避免空壳 service
 * 独立启动期把存量无头内部流量全打 403。
 *
 * <p>HeaderAuthInterceptor 非 @Component（防止 5 个空壳 service 误挂）——
 * 由各 service 在此<b>显式构建并注册</b>一次（设计 C.2.2 要求）。
 */
@Configuration
@ConditionalOnProperty(name = "ecos.header-auth.enabled", havingValue = "true")
public class HeaderAuthWebMvcConfig implements WebMvcConfigurer {

    private final HeaderAuthInterceptor interceptor;

    public HeaderAuthWebMvcConfig(@Value("${spring.application.name:ecos-buszhi}") String serviceId,
                                  @Value("${ecos.service.shared-secret:}") String sharedSecret) {
        this.interceptor = new HeaderAuthInterceptor(serviceId, sharedSecret);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**", "/api/v1/**");
    }
}
