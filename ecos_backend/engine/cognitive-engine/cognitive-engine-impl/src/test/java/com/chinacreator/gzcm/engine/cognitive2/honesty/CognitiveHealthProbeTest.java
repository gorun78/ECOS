package com.chinacreator.gzcm.engine.cognitive2.honesty;

import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.ParetoOptimizerService;
import com.chinacreator.gzcm.engine.cognitive2.ScenarioSimulatorService;
import com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveEngineHealthController;
import com.chinacreator.gzcm.engine.cognitive2.service.EngineOpenHealthQueryService;
import com.chinacreator.gzcm.runtime.access.graph.Neo4jClient;
import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.model.ProfileConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F05-16 要点 1 验收（X-42）：{@code GET /api/v1/cognitive/health} 的每个分量都绑一次真实探测调用。
 *
 * <p>断言两面：</p>
 * <ol>
 *   <li><b>调用面</b> — db 经 {@code EngineOpenHealthQueryService#probeDb()}（Controller 不直连库，
 *       铁律 §3.6）、三个能力分量经容器 {@code ObjectProvider#getIfAvailable()} 解析、
 *       llm 经 {@code llm-gateway}（{@code listProfiles}，铁律 §2.5-2）、
 *       neo4j 经 runtime-access {@code Neo4jClient#testConnection()}（铁律 §2.5-1）；
 *       各探测调用恰好一次，输出 {@code checked=true}。</li>
 *   <li><b>判定面</b> — 探测返回失败值或抛异常 ⇒ 该分量 {@code DOWN}（绝不 {@code UP}）；
 *       DB 失败汇总 {@code DOWN}，DB 可用而任一分量失败汇总 {@code DEGRADED}。
 *       standard 档（{@code ecos.cognitive.neo4j-enabled=false}）Neo4j 分量 {@code DISABLED}
 *       + {@code applicable=false} 且客户端一次都不调用，不参与汇总（依据数据库访问规范 ST02，
 *       禁 standard 实启 Neo4j）。</li>
 * </ol>
 *
 * <p>本模块无 MockMvc（禁引入），Controller 级断言 = 直接构造 + Mockito 替身。</p>
 */
class CognitiveHealthProbeTest {

    private static final List<String> COMPONENTS =
            List.of("db", "causalReasoner", "scenarioSimulator", "paretoOptimizer", "llm", "neo4j");

    private final EngineOpenHealthQueryService db = mock(EngineOpenHealthQueryService.class);
    private final CausalReasonerService causal = mock(CausalReasonerService.class);
    private final ScenarioSimulatorService simulator = mock(ScenarioSimulatorService.class);
    private final ParetoOptimizerService pareto = mock(ParetoOptimizerService.class);
    private final LLMGatewayService llm = mock(LLMGatewayService.class);
    private final Neo4jClient neo4j = mock(Neo4jClient.class);

    private final ObjectProvider<CausalReasonerService> causalProvider = CogStubMocks.provider(causal);
    private final ObjectProvider<ScenarioSimulatorService> simulatorProvider = CogStubMocks.provider(simulator);
    private final ObjectProvider<ParetoOptimizerService> paretoProvider = CogStubMocks.provider(pareto);
    private final ObjectProvider<LLMGatewayService> llmProvider = CogStubMocks.provider(llm);
    private final ObjectProvider<Neo4jClient> neo4jProvider = CogStubMocks.provider(neo4j);

    /** db 探活成功 + 五路依赖全部可达的健康控制器（探测桩在调用点各 stub 一次）。 */
    private CognitiveEngineHealthController healthyController(boolean neo4jEnabled) {
        when(db.probeDb()).thenReturn(1L);
        when(llm.listProfiles("cognitive")).thenReturn(List.of(new ProfileConfig()));
        when(neo4j.testConnection()).thenReturn(true);
        return new CognitiveEngineHealthController(db, causalProvider, simulatorProvider, paretoProvider,
                llmProvider, neo4jProvider, neo4jEnabled);
    }

    @Test
    @DisplayName("每个健康分量都绑一次真实探测调用，探测通过才 UP")
    void eachComponentIsActuallyChecked() {
        Map<String, Object> payload = healthyController(true).health();

        Map<String, Object> components = components(payload);
        assertEquals(COMPONENTS, List.copyOf(components.keySet()),
                "健康分量集合必须恰为 db/causalReasoner/scenarioSimulator/paretoOptimizer/llm/neo4j");
        for (String key : COMPONENTS) {
            Map<String, Object> item = component(components, key);
            assertEquals(Boolean.TRUE, item.get("checked"), key + " 未标记 checked=true");
            assertEquals("UP", item.get("status"), key + " 探测通过却未回 UP");
        }
        assertEquals("UP", payload.get("status"), "全分量探测通过时汇总必须 UP");

        // 调用面：六分量各自的探测调用确实发生过（状态字面量来自探测返回值，而非常量）
        verify(db).probeDb();
        verify(causalProvider).getIfAvailable();
        verify(simulatorProvider).getIfAvailable();
        verify(paretoProvider).getIfAvailable();
        verify(llmProvider).getIfAvailable();
        verify(llm).listProfiles("cognitive");
        verify(neo4jProvider).getIfAvailable();
        verify(neo4j).testConnection();
    }

    @Test
    @DisplayName("探测抛异常 ⇒ 该分量 DOWN + detail 记异常类型，汇总 DOWN（不被掩盖）")
    void failedProbeNeverReportsUp() {
        EngineOpenHealthQueryService dbDown = mock(EngineOpenHealthQueryService.class);
        when(dbDown.probeDb()).thenThrow(new DataAccessResourceFailureException("pg down"));
        when(llm.listProfiles("cognitive")).thenReturn(List.of(new ProfileConfig()));
        when(neo4j.testConnection()).thenReturn(true);

        Map<String, Object> payload = new CognitiveEngineHealthController(dbDown, causalProvider,
                simulatorProvider, paretoProvider, llmProvider, neo4jProvider, true).health();

        Map<String, Object> dbComponent = component(components(payload), "db");
        assertEquals("DOWN", dbComponent.get("status"), "DB 探测异常必须 DOWN，禁 UP");
        assertNotNull(dbComponent.get("detail"), "探测异常必须写 detail（异常类型）");
        assertEquals(DataAccessResourceFailureException.class.getSimpleName(), dbComponent.get("detail"));
        assertEquals("DOWN", payload.get("status"), "DB 不可用汇总必须 DOWN，禁 DEGRADED 掩盖");
    }

    @Test
    @DisplayName("能力 Bean 缺失 / LLM 探测异常 / Neo4j 连通失败 ⇒ 对应分量 DOWN + 汇总 DEGRADED")
    void missingCapabilityOrUnreachableDependencyDegrades() {
        when(db.probeDb()).thenReturn(1L);
        when(llm.listProfiles("cognitive")).thenThrow(new IllegalStateException("llm-gateway unreachable"));
        when(neo4j.testConnection()).thenReturn(false);
        CognitiveEngineHealthController c = new CognitiveEngineHealthController(db,
                causalProvider,
                CogStubMocks.emptyProvider(),                       // scenarioSimulator bean 缺失
                CogStubMocks.throwingProvider("pareto bean broken"), // 解析抛异常
                llmProvider, neo4jProvider, true);

        Map<String, Object> payload = c.health();
        Map<String, Object> components = components(payload);
        assertEquals("UP", component(components, "causalReasoner").get("status"));
        assertEquals("DOWN", component(components, "scenarioSimulator").get("status"),
                "Bean 缺失必须 DOWN，不是 UP");
        assertEquals("DOWN", component(components, "paretoOptimizer").get("status"),
                "探测抛异常必须 DOWN（fail-closed）");
        assertEquals("DOWN", component(components, "llm").get("status"));
        assertEquals("DOWN", component(components, "neo4j").get("status"));
        assertEquals("DEGRADED", payload.get("status"), "DB 可用而分量失败 = DEGRADED");

        // 兼容既有对外键：reasoners 三键与 components 同值（不返回旧字面量 UP）
        @SuppressWarnings("unchecked")
        Map<String, String> reasoners = (Map<String, String>) payload.get("reasoners");
        assertEquals("UP", reasoners.get("causalReasoner"), "reasoners 必须由探测派生");
        assertEquals("DOWN", reasoners.get("scenarioSimulator"));
        assertEquals("DOWN", reasoners.get("paretoOptimizer"));
    }

    @Test
    @DisplayName("standard 档 Neo4j 分量 = DISABLED + applicable=false，不参与汇总（非假绿）")
    void neo4jReportedNotApplicableInStandardEdition() {
        Map<String, Object> payload = healthyController(false).health();
        Map<String, Object> neo = component(components(payload), "neo4j");
        assertEquals("DISABLED", neo.get("status"), "N/A 必须显式 DISABLED，不能写成 UP 假绿");
        assertEquals(Boolean.FALSE, neo.get("applicable"));
        assertNotNull(neo.get("detail"), "N/A 依据必须写进 detail（ST02：standard 档不需要 Neo4j）");
        verify(neo4jProvider, never()).getIfAvailable();
        verify(neo4j, never()).testConnection();

        // 其余分量全通过时，Neo4j N/A 不得把汇总拉低成 DEGRADED
        assertEquals("UP", payload.get("status"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> components(Map<String, Object> payload) {
        Object v = payload.get("components");
        assertNotNull(v, "health 响应缺 components 明细");
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> component(Map<String, Object> components, String key) {
        Object v = components.get(key);
        assertNotNull(v, "缺健康分量：" + key);
        return (Map<String, Object>) v;
    }
}
