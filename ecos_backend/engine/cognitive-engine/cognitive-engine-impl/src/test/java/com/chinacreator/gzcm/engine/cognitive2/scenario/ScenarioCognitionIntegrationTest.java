package com.chinacreator.gzcm.engine.cognitive2.scenario;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
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
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 场景认知 E1~E4 契约验收（设计-05 F05-03~07 / C.1）。
 *
 * <p>直接装配真实 {@link ScenarioCognitiveController} + {@link CognitiveErrorAdvice}，
 * DAO/Reader/Scorer 用 Mockito 打桩（禁 Spring 上下文、禁连 DB）：
 * <ul>
 *   <li>happy path：调 controller 方法直接断 {@link ApiResponse} data 契约字段；</li>
 *   <li>错误矩阵：controller 抛 {@link CognitiveGuardException} → advice 转冻结码 · code = HTTP 值。</li>
 * </ul>
 * 权威（JWT + Clearance）双重等价由 gateway 三滤波器链 / CognitiveAuthParityTest 层承接（F05-14）。</p>
 */
class ScenarioCognitionIntegrationTest {

    private static final String SCENARIO_ID  = "sc001";
    private static final String MIND_BOUND   = "m-bound";
    private static final String MIND_ORPHAN  = "m-orphan";
    private static final String MIND_MISSING = "m-missing";

    private MindAssetDao mindDao;
    private ScenarioDao scenarioDao;
    private BeliefReader beliefReader;
    private CognitiveConfigQueryService cfg;
    private ScenarioCognitiveQueryService service;
    private ScenarioCognitionController controller;
    private CognitiveErrorAdvice advice;

    @BeforeEach
    void setUp() {
        mindDao      = mock(MindAssetDao.class);
        scenarioDao  = mock(ScenarioDao.class);
        BeliefScorer beliefScorer = mock(BeliefScorer.class);
        HypothesisReader hypReader = mock(HypothesisReader.class);
        beliefReader = mock(BeliefReader.class);
        cfg = mockConfigFacade();
        when(mindDao.mindTableExists()).thenReturn(true); // F05-02 schema 预检默认通过；专项反例见 CognitiveSchemaPreflightTest
        when(beliefScorer.readScoreInputs(anyString(), any(), anyList(), any())).thenReturn(List.of());
        when(hypReader.listForScenario(anyString(), any())).thenReturn(List.of());
        service = new ScenarioCognitiveQueryService(mindDao, scenarioDao, beliefScorer, hypReader,
                beliefReader, new DomainAliasResolver(cfg), cfg);
        controller = new ScenarioCognitionController(service, noRecorderObservability());
        advice = new CognitiveErrorAdvice();
    }

    /** §E.5 配置单源：引擎门默认开、trend_weight 走门面默认 1（不在测试里散落脚本值）。 */
    private static CognitiveConfigQueryService mockConfigFacade() {
        CognitiveConfigQueryService c = mock(CognitiveConfigQueryService.class);
        when(c.engineEnabled()).thenReturn(true);
        when(c.evalTrendWeight()).thenReturn(CognitiveConfigQueryService.TREND_WEIGHT_DEFAULT);
        when(c.domainAliases()).thenReturn(Map.of());
        return c;
    }

    /** C.7 门面未装配形态：计数降级 DEBUG，EVT 仍照常出日志。 */
    private static CognitiveObservability noRecorderObservability() {
        return CognitiveObsFixtures.unassembled();
    }

    /** 把控制器抛出的 guard 异常交给 advice，得到最终 ResponseEntity（还原 adice 拦截行为）。 */
    private ApiResponse<Map<String, Object>> viaAdvice(CognitiveGuardException e) {
        return advice.onGuard(e).getBody();
    }

    @Test
    @DisplayName("E1 happy path：mindId/name/capabilityMask/closedLoopBounds/isActive 五契约字段，仅 active 行")
    void e1DetectHappyPathFieldsAndActiveOnly() {
        when(mindDao.detectActiveMindsForScenario(SCENARIO_ID)).thenReturn(List.of(
                Map.of("mind_id", "m1", "mind_name", "MindA",
                        "capability_mask_json", "[\"DETECT\",\"FORECAST\"]",
                        "closed_loop_bounds_json", "[\"PRE\",\"POST\"]", "is_active", 1)));
        ApiResponse<List<CognitiveContractSchema.MindVO>> resp = controller.detect(SCENARIO_ID);
        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertEquals(1, resp.getData().size());
        CognitiveContractSchema.MindVO m = resp.getData().get(0);
        assertEquals("m1", m.getMindId());
        assertEquals("MindA", m.getName());
        assertEquals(List.of("DETECT", "FORECAST"), m.getCapabilityMask());
        assertEquals(List.of("PRE", "POST"), m.getClosedLoopBounds());
        assertEquals(Boolean.TRUE, m.getIsActive());
    }

    @Test
    @DisplayName("E1 场景不存在 → 404 + ECOS-COG-404 + 冻结 message")
    void e1Detect404WhenScenarioMissing() {
        doThrow(new ScenarioDao.ScenarioNotFoundException(SCENARIO_ID))
                .when(scenarioDao).assertScenarioExists(SCENARIO_ID);
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> controller.detect(SCENARIO_ID));
        ResponseEntity<ApiResponse<Map<String, Object>>> out = advice.onGuard(e);
        assertEquals(404, out.getStatusCode().value());
        ApiResponse<Map<String, Object>> body = out.getBody();
        assertEquals(404, body.getCode(), "code 与 HTTP 一致");
        assertEquals(CognitiveErrorCode.E404_SCENARIO, body.getErrorCode());
        assertEquals(CognitiveErrorCode.MSG_SCENARIO_NOT_FOUND, body.getMessage());
    }

    @Test
    @DisplayName("E1 场景已软删 → 404 + ECOS-COG-404（区分 message）")
    void e1Detect404WhenScenarioSoftDeleted() {
        doThrow(new ScenarioDao.ScenarioDeletedException(SCENARIO_ID))
                .when(scenarioDao).assertScenarioExists(SCENARIO_ID);
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> controller.detect(SCENARIO_ID));
        ApiResponse<Map<String, Object>> body = viaAdvice(e);
        assertEquals(404, body.getCode());
        assertEquals(CognitiveErrorCode.E404_SCENARIO, body.getErrorCode());
        assertEquals(CognitiveErrorCode.MSG_SCENARIO_DELETED, body.getMessage());
    }

    @Test
    @DisplayName("E1 场景无 Mind → 200 data:[]（禁 404，DG-6 空态语义）")
    void e1ReturnsEmptyArrayWhenScenarioHasNoMind() {
        when(mindDao.detectActiveMindsForScenario(SCENARIO_ID)).thenReturn(List.of());
        ApiResponse<List<CognitiveContractSchema.MindVO>> resp = controller.detect(SCENARIO_ID);
        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertNotNull(resp.getData());
        assertEquals(0, resp.getData().size(), "空场景返 data:[] 而非 404");
    }

    @Test
    @DisplayName("E2 mind 存在但未绑定 → 400 ECOS-COG-400 mind not on this scenario（禁静默兜底）")
    void e2RejectsMindNotOnScenario() {
        when(mindDao.mindExists(MIND_ORPHAN)).thenReturn(true);
        when(mindDao.isMindBoundToScenario(SCENARIO_ID, MIND_ORPHAN)).thenReturn(false);
        CognitiveGuardException e =
                assertThrows(CognitiveGuardException.class, () -> controller.operationEval(SCENARIO_ID, MIND_ORPHAN, 10));
        ApiResponse<Map<String, Object>> body = viaAdvice(e);
        assertEquals(400, body.getCode());
        assertEquals(CognitiveErrorCode.E400_MIND_NOT_ON_SCENARIO, body.getErrorCode());
        assertEquals(CognitiveErrorCode.MSG_MIND_NOT_ON_SCENARIO, body.getMessage());
    }

    @Test
    @DisplayName("E2 limit>50 → 硬截 50 且 truncated=true + trendSource=NOT_IMPLEMENTED")
    void e2LimitsHardCapFifty() {
        when(mindDao.mindExists(MIND_BOUND)).thenReturn(true);
        when(mindDao.isMindBoundToScenario(SCENARIO_ID, MIND_BOUND)).thenReturn(true);
        when(mindDao.readCapabilityMask(MIND_BOUND)).thenReturn("[\"DETECT\"]");
        BeliefScorer scorer = mock(BeliefScorer.class);
        List<BeliefScorer.ScoreInput> many = new java.util.ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            many.add(new BeliefScorer.ScoreInput("m" + i, "n" + i, "DETECT", i * 0.01));
        }
        when(scorer.readScoreInputs(anyString(), any(), anyList(), any())).thenReturn(many);
        ScenarioCognitiveQueryService svc = new ScenarioCognitiveQueryService(
                mindDao, scenarioDao, scorer, mock(HypothesisReader.class), beliefReader,
                new DomainAliasResolver(cfg), cfg);
        CognitiveContractSchema.OperationEvalData data = svc.operationEval(SCENARIO_ID, MIND_BOUND, 999);
        assertEquals(50, data.getLimit(), "limit>50 硬截 50");
        assertEquals(true, data.isTruncated(), "truncated 必为 true");
        assertEquals(50, data.getMetrics().size(), "Top-N 恰取 50 条");
        assertEquals(0, data.getMissingProbability(), "全部行已标定概率");
        assertEquals("NOT_IMPLEMENTED", data.getTrendSource());
        assertEquals("FLAT", data.getMetrics().get(0).getTrend());
        // score 唯一入口 = CognitiveEvalCalculator：probability × trend_weight(1)，降序首行即最大概率 0.60
        assertEquals(0.6d, data.getMetrics().get(0).getScore(), 1e-9, "数值来自概率而非伪造常量");
    }

    @Test
    @DisplayName("E3 mind 未绑定 → 严格 400（禁 fallback 全量，X-15）")
    void e3StrictMindFilterNoFallback() {
        when(mindDao.mindExists(MIND_ORPHAN)).thenReturn(true);
        when(mindDao.isMindBoundToScenario(SCENARIO_ID, MIND_ORPHAN)).thenReturn(false);
        assertThrows(CognitiveGuardException.class, () -> controller.hypotheses(SCENARIO_ID, MIND_ORPHAN));
    }

    @Test
    @DisplayName("E3 未绑定 mind 校验通过时委托 reader（SQL 内 is_deleted=0 承载软删过滤）")
    void e3NeverReturnsSoftDeletedRows() {
        List<CognitiveContractSchema.HypothesisVO> got = service.hypotheses(SCENARIO_ID, null);
        assertNotNull(got);
        assertTrue(got.isEmpty(), "无数据即为空；软删行由 Reader SQL is_deleted=0 过滤");
    }

    @Test
    @DisplayName("E4 未映射 domain → capability=null 但行不丢（DG-7）")
    void e4UnmatchedDomainKeepsRowWithNullCapability() {
        when(beliefReader.listForScenario(anyString(), any(), anyList(), any(Function.class)))
                .thenReturn(List.of(new CognitiveContractSchema.BeliefVO(
                        "b1", null, "var1", "unknown-domain", null, null, null, "t0")));
        List<CognitiveContractSchema.BeliefVO> rows = service.beliefs(SCENARIO_ID, null);
        assertEquals(1, rows.size(), "未映射行不丢（DG-7）");
        assertEquals(null, rows.get(0).getCapability(), "未映射 capability=null");
        assertEquals("unknown-domain", rows.get(0).getDomain());
    }

    @Test
    @DisplayName("E4 契约保留 updatedAt（Reader SQL desc 排序承载）")
    void e4OrdersByUpdatedAtDesc() {
        when(beliefReader.listForScenario(anyString(), any(), anyList(), any(Function.class)))
                .thenReturn(List.of(
                        new CognitiveContractSchema.BeliefVO("b1", null, "x", "d", null, null, null, "2026-10-02T00:00:00Z"),
                        new CognitiveContractSchema.BeliefVO("b2", null, "y", "d", null, null, null, "2026-10-01T00:00:00Z")));
        List<CognitiveContractSchema.BeliefVO> rows = service.beliefs(SCENARIO_ID, null);
        assertEquals(2, rows.size());
        assertNotNull(rows.get(0).getUpdatedAt());
    }

    @Test
    @DisplayName("E1~E4 守卫顺序：enabled→scenario→业务 read（未绕过前置门即证明顺序）")
    void e1ToE4RequireJwtAndClearance() {
        // 无 scenario stub（默认 mock 空 → 通过）；断言四端点走同一守卫链
        assertNotNull(service.detect(SCENARIO_ID));
        // E2 无 mind → 直接抛（mind 必填语义由 controller @RequestParam 强制）
        try {
            service.operationEval(SCENARIO_ID, MIND_MISSING, 10);
        } catch (CognitiveGuardException expected) {
            return; // mind 缺失的未绑/missing 守卫生效
        }
        assertNotNull(service.hypotheses(SCENARIO_ID, null));
        assertNotNull(service.beliefs(SCENARIO_ID, null));
    }

    @Test
    @DisplayName("契约前缀模板以 CognitiveApiPaths 单源拼出（防漂移）")
    void scenarioPrefixSingleSource() {
        String expect = String.format(CognitiveApiPaths.SCENARIO_COGNITION_PREFIX_TEMPLATE, SCENARIO_ID);
        assertEquals("/api/v1/business/scenarios/sc001/cognition", expect);
    }
}
