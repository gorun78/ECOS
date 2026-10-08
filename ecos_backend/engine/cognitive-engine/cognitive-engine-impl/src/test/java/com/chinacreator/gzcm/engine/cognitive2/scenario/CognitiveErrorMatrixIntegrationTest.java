package com.chinacreator.gzcm.engine.cognitive2.scenario;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.controller.scenario.CognitiveErrorAdvice;
import com.chinacreator.gzcm.engine.cognitive2.controller.scenario.ScenarioCognitionController;
import com.chinacreator.gzcm.engine.cognitive2.dao.BeliefReader;
import com.chinacreator.gzcm.engine.cognitive2.dao.BeliefScorer;
import com.chinacreator.gzcm.engine.cognitive2.dao.HypothesisReader;
import com.chinacreator.gzcm.engine.cognitive2.dao.MindAssetDao;
import com.chinacreator.gzcm.engine.cognitive2.dao.ScenarioDao;
import com.chinacreator.gzcm.engine.cognitive2.observability.CognitiveObservability;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveObsFixtures;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.DomainAliasResolver;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.ScenarioCognitiveQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * D.5 六行错误矩阵验收（设计-05 F05-07）：6 个方法名与矩阵 6 行一一对应，
 * {@code ApiResponse.code} = HTTP 值、{@code errorCode} = 冻结 ECOS-COG 码、
 * message = {@link CognitiveErrorCode} 冻结字面量（禁 fail-open / 禁 200 空 body）。
 */
class CognitiveErrorMatrixIntegrationTest {

    private static final String S = "sc001";

    private MindAssetDao mindDao;
    private ScenarioDao scenarioDao;
    private CognitiveConfigQueryService cfg;
    private ScenarioCognitiveQueryService service;
    private ScenarioCognitionController controller;
    private CognitiveErrorAdvice advice;

    @BeforeEach
    void setUp() {
        mindDao = mock(MindAssetDao.class);
        scenarioDao = mock(ScenarioDao.class);
        BeliefScorer scorer = mock(BeliefScorer.class);
        HypothesisReader hyp = mock(HypothesisReader.class);
        BeliefReader beliefReader = mock(BeliefReader.class);
        cfg = mock(CognitiveConfigQueryService.class);
        // 引擎默认启用（§E.5 经 sysman 门面，非反射改字段）；矩阵行 5 显式置 false
        when(cfg.engineEnabled()).thenReturn(true);
        when(scorer.readScoreInputs(anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        service = new ScenarioCognitiveQueryService(mindDao, scenarioDao, scorer, hyp,
                beliefReader, new DomainAliasResolver(cfg), cfg);
        controller = new ScenarioCognitionController(service, noRecorderObservability());
        advice = new CognitiveErrorAdvice();
    }

    /** C.7 门面未装配形态（无指标实现/无 EventBus 时降级，禁抛错）。 */
    private static CognitiveObservability noRecorderObservability() {
        return CognitiveObsFixtures.unassembled();
    }

    /** 引擎启停门配置（单源门面 mock，禁反射写字段）。 */
    private void setEnabled(boolean v) {
        when(cfg.engineEnabled()).thenReturn(v);
    }

    private ApiResponse<Map<String, Object>> runGuard(org.junit.jupiter.api.function.Executable call) {
        CognitiveGuardException e = org.junit.jupiter.api.Assertions.assertThrows(
                CognitiveGuardException.class, call);
        return advice.onGuard(e).getBody();
    }

    @Test
    @DisplayName("矩阵行1 场景不存在 → 404 ECOS-COG-404 scenario not found")
    void scenarioNotFound() {
        doThrow(new ScenarioDao.ScenarioNotFoundException(S))
                .when(scenarioDao).assertScenarioExists(S);
        ApiResponse<Map<String, Object>> b = runGuard(() -> controller.detect(S));
        assertEquals(404, b.getCode());
        assertEquals(CognitiveErrorCode.E404_SCENARIO, b.getErrorCode());
        assertEquals(CognitiveErrorCode.MSG_SCENARIO_NOT_FOUND, b.getMessage());
    }

    @Test
    @DisplayName("矩阵行2 场景已软删 → 404 ECOS-COG-404 scenario deleted")
    void scenarioSoftDeleted() {
        doThrow(new ScenarioDao.ScenarioDeletedException(S))
                .when(scenarioDao).assertScenarioExists(S);
        ApiResponse<Map<String, Object>> b = runGuard(() -> controller.detect(S));
        assertEquals(404, b.getCode());
        assertEquals(CognitiveErrorCode.MSG_SCENARIO_DELETED, b.getMessage());
    }

    @Test
    @DisplayName("矩阵行3 mind 不属场景 → 400 ECOS-COG-400 mind not found on this scenario")
    void mindNotOnScenario() {
        when(mindDao.mindExists("m-x")).thenReturn(true);
        when(mindDao.isMindBoundToScenario(S, "m-x")).thenReturn(false);
        ApiResponse<Map<String, Object>> b = runGuard(() -> controller.operationEval(S, "m-x", 10));
        assertEquals(400, b.getCode());
        assertEquals(CognitiveErrorCode.E400_MIND_NOT_ON_SCENARIO, b.getErrorCode());
        assertEquals(CognitiveErrorCode.MSG_MIND_NOT_ON_SCENARIO, b.getMessage());
    }

    @Test
    @DisplayName("矩阵行4 mind 不存在 → 400 ECOS-COG-400M mind not found")
    void mindNotFound() {
        when(mindDao.mindExists("m-none")).thenReturn(false);
        ApiResponse<Map<String, Object>> b = runGuard(() -> controller.operationEval(S, "m-none", 10));
        assertEquals(400, b.getCode());
        assertEquals(CognitiveErrorCode.E400_MIND_NOT_FOUND, b.getErrorCode());
        assertEquals(CognitiveErrorCode.MSG_MIND_NOT_FOUND, b.getMessage());
    }

    @Test
    @DisplayName("矩阵行5 引擎未启用 → 503 ECOS-COG-500 cognitive engine not enabled（原样穿透 A3）")
    void engineDisabled() {
        setEnabled(false);
        ApiResponse<Map<String, Object>> b = runGuard(() -> controller.detect(S));
        assertEquals(503, b.getCode());
        assertEquals(CognitiveErrorCode.E500_ENGINE_DISABLED, b.getErrorCode());
        assertEquals(CognitiveErrorCode.MSG_ENGINE_NOT_ENABLED, b.getMessage());
    }

    @Test
    @DisplayName("矩阵行6 schema 未就绪 → 503 ECOS-COG-501 cognitive schema not ready")
    void schemaNotReady() {
        when(mindDao.mindTableExists()).thenReturn(false);
        ApiResponse<Map<String, Object>> b = runGuard(() -> controller.detect(S));
        assertEquals(503, b.getCode());
        assertEquals(CognitiveErrorCode.E501_SCHEMA_NOT_READY, b.getErrorCode());
        assertEquals(CognitiveErrorCode.MSG_SCHEMA_NOT_READY, b.getMessage());
    }
}
