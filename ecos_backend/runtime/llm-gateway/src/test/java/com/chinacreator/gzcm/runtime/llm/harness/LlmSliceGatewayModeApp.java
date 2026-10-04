package com.chinacreator.gzcm.runtime.llm.harness;

import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import com.chinacreator.gzcm.runtime.llm.security.SecurityEngineBridge;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * F06-22（X-18/R-18）slice — <b>gateway fat-JAR 态</b>。
 *
 * <p>最小 Spring Boot 上下文，仅装配 {@link SecurityEngineBridge} 与一个
 * 桩 {@link OpaPolicyService}（security-engine 同 JVM 且 OPA 可见的业务态）。
 * 该态下 {@code SecurityEngineBridge#evaluate} 应经反射命中 OPA → 裁决放行。</p>
 */
@SpringBootConfiguration
@Configuration
public class LlmSliceGatewayModeApp {

    @Bean
    public SecurityEngineBridge securityEngineBridge(ApplicationContext ctx) {
        return new SecurityEngineBridge(ctx);
    }

    /** 桩 OPA bean：gateway 态 OPA 服务在同 JVM 可见（区别于 aiming service 态 classpath 缺失）。 */
    @Bean
    public OpaPolicyService opaPolicyService() {
        return new OpaPolicyService();
    }
}
