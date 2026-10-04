package com.chinacreator.gzcm.engine.ai.harness;

import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.engine.ai.service.AgentConfigResolver;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * F06-22（X-87 → C146 前提门禁）: ai-engine <b>薄切片</b>装配。
 *
 * <p>只做 {@link Import} 两个真实生产 bean（无类扫描、无 autoconfig、不触 DB/网）：
 * <ul>
 *   <li>{@link AiSecurityEngineClient} — AI 对 security 的统一 REST 访问层（铁律 §2.4，
 *       fail-closed 默认 DENY）</li>
 *   <li>{@link AgentConfigResolver} — Agent 三层配置解析器（L1 默认走 {@code @Value} 兜底）</li>
 * </ul>
 *
 * <p>离线运行：两个 bean 无 {@code @PostConstruct} 外呼（无 JDBC 池初始化、无外网建连），
 * 断言点只做本地配置解析 + REST 未达 security 时的 fail-closed 语义。</p>
 *
 * <p>两态 meaning：本 slice 是「gateway fat-JAR 态」的 ai-engine 代表（with
 * {@code llm-gateway} 侧的 {@code SecurityEngineBridge} 独立承载两态 ABAC 对照，
 * 参见 llm-gateway 的 {@code AiTestHarnessSmokeTest}/{@code GatewayAbacModeTest}）。
 * ai-engine 侧 REST 客户端在两态下行为一致（均指向同一 security REST 端点，
 * fail-closed 路径同形），故此处单 slice 即满足「每模块 ≥1 真实
 * {@code @SpringBootTest}」（对应 {@code AiIntegrationCoverageTest} 计数断言）。</p>
 */
@SpringBootConfiguration
@Import({ AiSecurityEngineClient.class, AgentConfigResolver.class })
@TestPropertySource(properties = {
        "service.security.base-url=http://127.0.0.1:18081",
        "service.security.timeout-ms=200",
        "llm.default-provider=deepseek",
        "llm.default-model=deepseek-chat"
})
public class AiSliceApp {
}
