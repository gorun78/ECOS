package com.chinacreator.gzcm.runtime.llm.harness;

import com.chinacreator.gzcm.runtime.llm.security.SecurityEngineBridge;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * F06-22（X-18/R-18）slice — <b>aiming service 态</b>。
 *
 * <p>最小 Spring Boot 上下文，仅装配 {@link SecurityEngineBridge}，<b>不</b>注册任何
 * OPA 桩 bean —— 模拟 aiming service 独立部署态下 security-engine 类不在 classpath
 * （R-18 的现实形态）。该态下 {@code SecurityEngineBridge#evaluate} 反射查不到 OPA
 * → 必须 fail-closed DENY，而非被静默跳过（若 {@code abac-eval-enabled=false}
 * 被误当作放行，即 X-18 绕过面，测试将 FAIL）。
 */
@SpringBootConfiguration
@Configuration
public class LlmSliceServiceModeApp {

    @Bean
    public SecurityEngineBridge securityEngineBridge(ApplicationContext ctx) {
        return new SecurityEngineBridge(ctx);
    }
}
