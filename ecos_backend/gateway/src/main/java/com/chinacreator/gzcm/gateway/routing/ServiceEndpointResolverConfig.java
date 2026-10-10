package com.chinacreator.gzcm.gateway.routing;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * W01（详细设计-00 C.4，M0）装配 Bean：gateway 启动时读取 classpath
 * {@code route/route-manifest.json} 构造 {@link ServiceEndpointResolver}。
 */
@Configuration
public class ServiceEndpointResolverConfig {

    @Bean
    public ServiceEndpointResolver serviceEndpointResolver(
            @Value("${server.port:8080}") int gatewayPort,
            @Value("${ecos.route.host:127.0.0.1}") String host) {
        try (InputStream is = ServiceEndpointResolverConfig.class
                .getResourceAsStream("/route/route-manifest.json")) {
            if (is == null) {
                throw new IllegalStateException("/route/route-manifest.json 未找到（构建期未拷贝）");
            }
            return new ServiceEndpointResolver(is, gatewayPort, host);
        } catch (IOException e) {
            throw new IllegalStateException("route-manifest 解析失败", e);
        }
    }
}
