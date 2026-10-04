package com.chinacreator.gzcm.engine.ai.harness;

import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.engine.ai.service.AgentConfigResolver;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F06-22 / W164 → C146（前提门禁）：ai-engine 侧的 <b>{@code @SpringBootTest} 薄切片</b>。
 *
 * <p>此前全仓 {@code @SpringBootTest} 0 命中（X-87），任何「集成断言」无处安放。
 * 本类在 {@code engine/ai-engine/ai-engine-impl/src/test} 实际启动一个最小
 * Spring 容器（{@link AiSliceApp}：仅装配 {@link AiSecurityEngineClient} +
 * {@link AgentConfigResolver}，无 DB / 无外网 / 无扫描），断言两件事：
 * <ol>
 *   <li>{@code springBootTestSliceBootsInBothModes} — 切片能启动且关键 bean 在位
 *       （证「目录建成即有可启动用例」，是 {@code AiIntegrationCoverageTest}
 *       计数断言的实物锚）；</li>
 *   <li>security REST 不可达时 {@link AiSecurityEngineClient#evaluate} 必须
 *       <b>fail-closed DENY</b>（铁律 §2.4：security 不可用一律默认 DENY，不降级放行）。</li>
 * </ol>
 *
 * <p>两态矩阵（4.5）里 ai-engine 32 Controller 在两态「同鉴权水平」，本 slice
 * 复现该底线的切片面：REST 端点两态一致、fail-closed 路径同形。llm-gateway 侧
 * ABAC 同 JVM 反射的两态差异化（gateway ALLOW / service DENY）由
 * llm-gateway 模块的 {@code AiTestHarnessSmokeTest} / {@code GatewayAbacModeTest}
 * 承载，不在本类重复。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = AiSliceApp.class)
class AiEngineSmokeTest {

    @Autowired
    AiSecurityEngineClient securityClient;

    @Autowired
    AgentConfigResolver configResolver;

    @Test
    @DisplayName("F06-22 springBootTestSliceBootsInBothModes / ai-engine — security + config bean 均在位")
    void springBootTestSliceBootsInBothModes() {
        assertNotNull(securityClient, "切片启动后 AiSecurityEngineClient 应装配（否则集成挂空档）");
        assertNotNull(configResolver, "切片启动后 AgentConfigResolver 应装配（否则集成挂空档）");
    }

    @Test
    @DisplayName("F06-22 ai-engine 切片 — security REST 不可达时 ABAC 裁决 fail-closed DENY")
    void securityRestUnavailableFailsClosed() {
        // base-url 指向离线未启端口（127.0.0.1:18081，且 timeout=200ms）→ postForEntity 抛异常
        // → evaluate catch → 返回 false（DENY）。禁止「security 不可达 = 无需裁决 = 放行」。
        boolean verdict = securityClient.evaluate("pm-operator", "default", "tool.execute",
                Map.of("tool", "query_db"));
        assertFalse(verdict,
                "security REST 不可达，AiSecurityEngineClient.evaluate 必须 fail-closed DENY（铁律 §2.4）");
    }

    @Test
    @DisplayName("F06-22 ai-engine 切片 — Agent 配置 L1 默认解析成立（本地解析，不触库）")
    void agentConfigResolveReturnsL1Defaults() {
        // agentId=default → 仅 L1 + 请求覆盖，不查 AgentRegistry（AgentRegistryRepository 未装配 = null，安全）。
        AgentLoopConfig cfg = configResolver.resolve("default", null);
        assertNotNull(cfg, "L1 默认配置解析应返回非空 AgentLoopConfig");
        assertTrue(cfg.getModel() != null && !cfg.getModel().isBlank(),
                "L1 默认 model 应从 application 属性解析到位");
        assertEquals("deepseek", cfg.getDefaultProvider(),
                "L1 默认 provider 应为配置注入的 deepseek");
    }
}
