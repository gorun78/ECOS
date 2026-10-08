package com.chinacreator.gzcm.engine.cognitive2.honesty;

import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.ParetoOptimizerService;
import com.chinacreator.gzcm.engine.cognitive2.ScenarioSimulatorService;
import com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveEngineHealthController;
import com.chinacreator.gzcm.engine.cognitive2.service.EngineOpenHealthQueryService;
import com.chinacreator.gzcm.runtime.access.graph.Neo4jClient;
import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.model.ProfileConfig;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 认知健康探测门禁的依赖替身工厂（本模块无 MockMvc / Spring 上下文，Controller 级断言直接构造）。
 */
final class CogStubMocks {

    private CogStubMocks() {}

    /** bean 缺失的 ObjectProvider（getIfAvailable 回 null）。 */
    @SuppressWarnings("unchecked")
    static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    /** bean 可解析的 ObjectProvider。 */
    @SuppressWarnings("unchecked")
    static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        return provider;
    }

    /** 解析即抛异常的 ObjectProvider（探测失败面）。 */
    @SuppressWarnings("unchecked")
    static <T> ObjectProvider<T> throwingProvider(String message) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenThrow(new IllegalStateException(message));
        return provider;
    }

    /**
     * 全分量探测成功的健康控制器。
     *
     * @param neo4jEnabled 档位开关（enterprise/ultimate true；standard false = Neo4j 分量 N/A）
     */
    static CognitiveEngineHealthController healthController(boolean neo4jEnabled) {
        return healthController(probeDbOk(), provider(mock(CausalReasonerService.class)),
                provider(mock(ScenarioSimulatorService.class)), provider(mock(ParetoOptimizerService.class)),
                llmOk(), neo4j(neo4jEnabled, true), neo4jEnabled);
    }

    static EngineOpenHealthQueryService probeDbOk() {
        EngineOpenHealthQueryService service = mock(EngineOpenHealthQueryService.class);
        when(service.probeDb()).thenReturn(1L);
        return service;
    }

    static EngineOpenHealthQueryService probeDbFailing() {
        EngineOpenHealthQueryService service = mock(EngineOpenHealthQueryService.class);
        when(service.probeDb()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("pg down"));
        return service;
    }

    static ObjectProvider<LLMGatewayService> llmOk() {
        LLMGatewayService gateway = mock(LLMGatewayService.class);
        when(gateway.listProfiles("cognitive")).thenReturn(List.of(new ProfileConfig()));
        return provider(gateway);
    }

    static ObjectProvider<LLMGatewayService> llmFailing() {
        LLMGatewayService gateway = mock(LLMGatewayService.class);
        when(gateway.listProfiles("cognitive")).thenThrow(new IllegalStateException("llm-gateway unreachable"));
        return provider(gateway);
    }

    static ObjectProvider<Neo4jClient> neo4j(boolean enabled, boolean connected) {
        if (!enabled) {
            return emptyProvider();
        }
        Neo4jClient client = mock(Neo4jClient.class);
        when(client.testConnection()).thenReturn(connected);
        return provider(client);
    }

    static CognitiveEngineHealthController healthController(EngineOpenHealthQueryService db,
            ObjectProvider<CausalReasonerService> causal,
            ObjectProvider<ScenarioSimulatorService> simulator,
            ObjectProvider<ParetoOptimizerService> pareto,
            ObjectProvider<LLMGatewayService> llm,
            ObjectProvider<Neo4jClient> neo4j,
            boolean neo4jEnabled) {
        return new CognitiveEngineHealthController(db, causal, simulator, pareto, llm, neo4j, neo4jEnabled);
    }
}
