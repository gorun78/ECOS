package com.chinacreator.gzcm.services.aiming.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Aiming Service 横切基础设施 bean。
 *
 * <p>PMO-49 P2-5: agent-service runtime 的若干 Tool (如 {@code AuditAccessLogTool})
 * 通过构造器注入 {@link RestTemplate} 调跨服务 REST (audit/rls/mask)。
 * monolith gateway 时代由 gateway 全局 config 提供该 bean；aiming 独立部署后
 * 需在本 service 自含一份，否则 bean graph 断裂。
 *
 * <p>说明：不做复杂拦截器（ADR-7 头还原在 Phase 3 统一 HeaderAuthInterceptor 落地），
 * 此处仅补最小可用 bean 让 agent runtime 工具链可装配。
 *
 * @author ecos-factory
 * @since PMO-49 P2-5
 */
@Configuration
public class AimingInfrastructureConfig {

    /**
     * 跨服务 REST 调用的共享 RestTemplate（agent 工具链 / RLS / 审计）。
     * 保留默认 SimpleClientHttpRequestFactory；连接池/超时 Phase 3 按需调。
     *
     * @return RestTemplate bean
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
