package com.chinacreator.gzcm.runtime.llm.harness;

import com.chinacreator.gzcm.runtime.llm.security.SecurityEngineBridge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * F06-22 / W164 → C146（前提门禁）：<b>ai-engine + llm-gateway</b> 的
 * {@code @SpringBootTest} 薄切片验收载体 — <b>gateway fat-JAR 态</b>分支。
 *
 * <p>此前全仓 {@code @SpringBootTest} 0 命中（X-87），任何「集成断言」无处安放
 * —— 本类把「网关 fat-JAR 态」的薄切片<b>实际启动</b>并断言链路上的关键
 * bean（{@link SecurityEngineBridge}）可解析，即「目录建成即有可启动用例」
 * （对应 {@code AiIntegrationCoverageTest#aiEngineAndGatewayHaveAtLeastOneRealIT}
 * 防「目录建成但无用例」的计数锚）。</p>
 *
 * <p>切片刻意离线：不装配真库 / 真 Kafka / 真 provider，只连
 * {@link SecurityEngineBridge} 与一个桩
 * {@link com.chinacreator.gzcm.engine.security.service.OpaPolicyService}
 * （「同 JVM OPA 可见」的 gateway 态形态）。webEnvironment=MOCK 走真实 Spring
 * 容器启动路径，与生产启动同形。ABAC 两态差异化断言另见
 * {@link GatewayAbacModeTest}（service 态 fail-closed DENY）与
 * {@link #abacAllowInGatewayMode}（本类 gateway 态 ALLOW）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = LlmSliceGatewayModeApp.class)
class AiTestHarnessSmokeTest {

    @Autowired
    SecurityEngineBridge bridge;

    @Autowired
    ApplicationContext ctx;

    @Test
    @DisplayName("F06-22 springBootTestSliceBootsInBothModes / gateway 态 — bridge + ctx 均在位")
    void springBootTestSliceBootsInBothModes() {
        assertNotNull(bridge, "gateway 态 SecurityEngineBridge 应已装配（若失败 = 切片启动失败）");
        assertNotNull(ctx, "gateway 态 ApplicationContext 应已启动（若失败切片未成体系）");
    }

    @Test
    @DisplayName("F06-22 gateway 态 — OPA 桩在场 evaluate 放行（ALLOW）")
    void abacAllowInGatewayMode() {
        // gateway fat-JAR 态：security-engine 的 OpaPolicyService 与 llm-gateway 同 JVM 可见，
        // 反射 findBeanByClassName 命中桩 bean → evaluate 返回 ALLOW。
        boolean verdict = bridge.evaluate("pm-operator", "default",
                "llm-gateway.call", java.util.Map.of("provider", "deepseek"));
        org.junit.jupiter.api.Assertions.assertTrue(verdict,
                "gateway 态 OPA 桩 bean 在场，evaluate 应放行（裁决 allow=true）");
    }
}
