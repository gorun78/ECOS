package com.chinacreator.gzcm.common.security.header;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 详细设计-00 W02 信任链接入点 — 由 common-api 单源自动装配到每个依赖它且为 Web 应用的 JAR。
 *
 * <p><b>默认关闭</b>（{@code ecos.security.header-auth.enabled=false}）。
 * monolith gateway fat-jar 阶段浏览器请求不带 X-ECOS-* 信任头，若强开会
 * 因为缺头一律 403 ECOS-AUTH-010（H10 服务间凭证尚未收口，见 ClearanceMvcConfigT2bTest
 * 对 knowledge/extract 的豁免）。待 service 切流（W01 S1~S4）且网关下行注入信任头后，
 * 相应 service 设该属性为 true 即可启用本拦截器。</p>
 *
 * <p>非 Web 应用 / 缺 webmvc 时不生效；已有同名 Bean 时不重复注册。</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(org.springframework.web.servlet.HandlerInterceptor.class)
@ConditionalOnProperty(prefix = "ecos.security.header-auth", name = "enabled", havingValue = "true")
public class HeaderAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HeaderAuthInterceptor ecosHeaderAuthInterceptor() {
        return new HeaderAuthInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(name = "ecosHeaderAuthMvcConfigurer")
    public WebMvcConfigurer ecosHeaderAuthMvcConfigurer(HeaderAuthInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/api/v1/**", "/api/**");
            }
        };
    }
}
