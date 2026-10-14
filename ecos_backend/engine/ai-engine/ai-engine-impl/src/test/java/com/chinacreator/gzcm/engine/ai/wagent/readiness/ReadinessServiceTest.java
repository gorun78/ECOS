package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessCheckStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessGrade;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessLayer;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentMissionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessItemDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentMissionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessGapDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessItemDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService.BudgetSnapshot;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService.Consumption;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentQuestionService;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.EngineProbeClient.LayerProbe;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.ReadinessCheckCode.Judgement;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.ReadinessGapService.RefillLedger;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.ReadinessGapService.TaskDispatch;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.ThresholdSet.ThresholdValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F10-08/09 编排面门禁（详细设计-10 §4.1 {@code readiness/ReadinessService} + 五条硬规则）。
 *
 * <p>本类钉的是<b>编排面的四个不可让渡性质</b>，不是数值：
 * <ol>
 *   <li><b>证据缺席必须显形</b>——七项未接线度量 ⇒ 恒 UNKNOWN ⇒ 总级不高于 C
 *       （{@link #unwiredMetricsSurfaceAsUnknownAndCapGradeAtC()}）；把它洗成 A 就是假绿；</li>
 *   <li><b>顺序即事务</b>（裁决 D-B2④-15）——明细(V231) → 缺口(V232) → 头(V230) → 替代位，
 *       崩溃只留无主明细，绝不留"有头无明细"的假评估（{@link #itemsAndGapsAreWrittenBeforeHead()}）；</li>
 *   <li><b>只追加、pinned 豁免</b>（硬规则④）——本类从不 update 旧评估内容，pinned 行返 0 时
 *       既不抛错也不去动它的缺口（{@link #pinnedAssessmentSurvivesReassessment()}）；</li>
 *   <li><b>超限是 200 不是错误</b>（硬规则⑤）——越限轮次只挂起问题、不复评、不计轮
 *       （{@link #refillOverLimitSuspendsQuestionWithoutReassessing()}）。</li>
 * </ol>
 *
 * <p>全 mock：不起真引擎、不连真库（V230/231/232 迁移未在 dev 实跑，实跑授权是另一件事，
 * 本类的结论不依赖库表存在）。</p>
 */
class ReadinessServiceTest {

    private static final String TENANT = "t-1";
    private static final String ORG = "o-1";
    private static final String QUESTION_ID = "q-1";
    private static final String OPERATOR = "u-1";

    private EngineProbeClient probeClient;
    private ThresholdPolicy thresholdPolicy;
    private ObjectProvider<ReadinessMetricSource> metricSources;
    private WAgentReadinessDao readinessDao;
    private WAgentReadinessItemDao itemDao;
    private WAgentMissionDao missionDao;
    private ReadinessGapService gapService;
    private WAgentQuestionService questionService;
    private ReadinessService service;

    /** 逐检查项的度量供给（缺席即不 put ⇒ 取数面返回 absent，模拟"端点未接线"）。 */
    private final Map<ReadinessCheckCode, MetricValue> suppliedMetrics = new EnumMap<>(ReadinessCheckCode.class);
    /** 逐检查项的阈值供给（默认给"能让判定通过"的 seed 形态值）。 */
    private final Map<ReadinessCheckCode, ThresholdSet> suppliedThresholds = new EnumMap<>(ReadinessCheckCode.class);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        probeClient = mock(EngineProbeClient.class);
        thresholdPolicy = mock(ThresholdPolicy.class);
        metricSources = mock(ObjectProvider.class);
        readinessDao = mock(WAgentReadinessDao.class);
        itemDao = mock(WAgentReadinessItemDao.class);
        missionDao = mock(WAgentMissionDao.class);
        gapService = mock(ReadinessGapService.class);
        questionService = mock(WAgentQuestionService.class);

        service = new ReadinessService(probeClient, thresholdPolicy, metricSources, readinessDao,
                itemDao, missionDao, gapService, questionService);

        when(probeClient.probeAll()).thenReturn(allLayersUp());
        when(thresholdPolicy.resolve(anyString(), any(ReadinessCheckCode.class)))
                .thenAnswer(invocation -> suppliedThresholds.get(invocation.<ReadinessCheckCode>getArgument(1)));
        // 每次取数都要一条新 Stream：Mockito 默认复用同一个已消费 Stream ⇒ 第二次 .toList() 直接炸
        when(metricSources.stream()).thenAnswer(invocation -> availableSources());
        when(readinessDao.findCurrentByQuestionId(anyString())).thenReturn(Optional.empty());
        when(readinessDao.insert(any())).thenReturn(1);
        when(itemDao.insert(any())).thenReturn(1);
        when(missionDao.findById(anyString())).thenReturn(Optional.empty());
        when(gapService.openGaps(anyString(), any(), any(), anyString(), any(), anyString()))
                .thenReturn(List.of());
        when(gapService.findOpenGaps(anyString())).thenReturn(List.of());

        suppliedMetrics.clear();
        suppliedThresholds.clear();
        seedPassingMetrics();
        seedPassingThresholds();
    }

    // ══════════════════ 性质 1：证据缺席必须显形 ══════════════════

    @Test
    @DisplayName("七项度量未接线 ⇒ 逐项 UNKNOWN、四层 unknown、总级 C（禁把'没探到'洗成就绪）")
    void unwiredMetricsSurfaceAsUnknownAndCapGradeAtC() {
        // 现网真实形态：没有任何取数面在位（八度量中仅 onto.caliber_defined 有本域源，
        // 且它要 Goal 锚点；本用例连 Goal 都反查不到）
        suppliedMetrics.clear();
        // 每次调用都要一条新 Stream（Stream.empty() 是共享单例，第二次终端操作会抛"已消费"）
        when(metricSources.stream()).thenAnswer(invocation -> Stream.<ReadinessMetricSource>of());

        ReadinessService.EvaluationResult result = service.evaluate(context(), OPERATOR);

        assertEquals(ReadinessGrade.C, result.grade().grade(), "无证据不得优于'有证据且失败'层");
        assertEquals("UNKNOWN_PRESENT->C", result.grade().matchedRule());
        assertEquals(ReadinessCheckCode.values().length, result.outcomes().size(), "八项恒全出结论");
        for (CheckOutcome outcome : result.outcomes()) {
            assertEquals(ReadinessCheckStatus.UNKNOWN, outcome.status(),
                    outcome.code().checkCode() + " 在度量缺席时不得出 pass");
            assertTrue(outcome.judgement().reason().startsWith("metric-absent"),
                    "UNKNOWN 必须点名'度量缺席'而非泛泛失败: " + outcome.judgement().reason());
        }
        for (ReadinessLayer layer : ReadinessLayer.values()) {
            assertEquals(ReadinessCheckStatus.UNKNOWN, result.grade().layerStatus().get(layer),
                    layer + " 层无证据即无结论");
        }
    }

    @Test
    @DisplayName("任一 blocker ⇒ D；未知项不冒充 blocker（定级只认服务端结论）")
    void blockerPresentYieldsD() {
        suppliedMetrics.put(ReadinessCheckCode.COG_CM_READY, MetricValue.bool(false));

        ReadinessService.EvaluationResult result = service.evaluate(context(), OPERATOR);

        assertEquals(ReadinessGrade.D, result.grade().grade());
        assertEquals("BLOCKER_PRESENT->D", result.grade().matchedRule());
        assertEquals(ReadinessCheckStatus.BLOCKER.value(), result.assessment().getLayerC());
    }

    @Test
    @DisplayName("八项全 pass ⇒ A，且缺口表零行（missing 过滤只认非 pass）")
    void allPassYieldsAAndKeepsGapServiceFilteredOnMissing() {
        ReadinessService.EvaluationResult result = service.evaluate(context(), OPERATOR);

        assertEquals(ReadinessGrade.A, result.grade().grade());
        assertEquals("ALL_PASS->A", result.grade().matchedRule());
        assertTrue(result.grade().missingItems().isEmpty());

        ArgumentCaptor<List<CheckOutcome>> captured = ArgumentCaptor.forClass(List.class);
        verify(gapService).openGaps(eq(result.assessmentId()), any(), captured.capture(),
                eq("A"), any(), eq(OPERATOR));
        assertEquals(ReadinessCheckCode.values().length, captured.getValue().size(),
                "缺口服务收到全量结论，由它按 missing() 过滤（编排面不得先筛）");
    }

    // ══════════════════ 性质 2：顺序即事务 ══════════════════

    @Test
    @DisplayName("落库顺序=明细→缺口→头→替代位（崩溃只留无主明细，禁留'有头无明细'的假评估）")
    void itemsAndGapsAreWrittenBeforeHead() {
        suppliedMetrics.put(ReadinessCheckCode.DATA_COVERAGE, MetricValue.number(new BigDecimal("0.2")));
        when(readinessDao.findCurrentByQuestionId(anyString()))
                .thenReturn(Optional.of(previousAssessment("ra-old")));
        when(readinessDao.markSuperseded(eq("ra-old"), anyString(), eq(OPERATOR))).thenReturn(1);

        ReadinessService.EvaluationResult result = service.evaluate(context(), OPERATOR);

        InOrder order = inOrder(itemDao, gapService, readinessDao);
        verify(itemDao, never()).update(any());
        order.verify(itemDao, org.mockito.Mockito.times(ReadinessCheckCode.values().length)).insert(any());
        order.verify(gapService).openGaps(eq(result.assessmentId()), any(), any(), anyString(), any(), anyString());
        order.verify(readinessDao).insert(any());
        order.verify(readinessDao).markSuperseded("ra-old", result.assessmentId(), OPERATOR);
        order.verify(gapService).supersedeOpenGaps("ra-old", OPERATOR);
    }

    @Test
    @DisplayName("V231 明细三段同源：measured_json 含度量+状态+原因+供数面，threshold_json 含档位与来源")
    void itemRowsCarryMeasuredAndThresholdSnapshots() {
        service.evaluate(context(), OPERATOR);

        ArgumentCaptor<WAgentReadinessItemDO> captor = ArgumentCaptor.forClass(WAgentReadinessItemDO.class);
        verify(itemDao, org.mockito.Mockito.times(ReadinessCheckCode.values().length)).insert(captor.capture());
        List<WAgentReadinessItemDO> rows = captor.getAllValues();
        assertEquals(ReadinessCheckCode.values().length, rows.size());

        WAgentReadinessItemDO coverage = rows.stream()
                .filter(row -> "data.check_coverage".equals(row.getCheckCode())).findFirst().orElseThrow();
        assertTrue(coverage.getMeasuredJson().contains("\"status\":\"pass\""), coverage.getMeasuredJson());
        assertTrue(coverage.getMeasuredJson().contains("metric_source"), "禁无痕度量: " + coverage.getMeasuredJson());
        assertTrue(coverage.getMeasuredJson().contains("\"value\":\"0.9\""),
                "数值必须以字符串落（铁律禁 double 漂移）: " + coverage.getMeasuredJson());
        assertTrue(coverage.getThresholdJson().contains("\"profile\":\"default\""), coverage.getThresholdJson());
        assertEquals(TENANT, coverage.getTenantId());
        assertEquals("1", coverage.getVersionNo());
        assertNull(coverage.getEvidenceIdsJson(), "V240 证据行本批未写 ⇒ 留 NULL，禁造证据 ID");
    }

    @Test
    @DisplayName("V230 头行：profile_id 留 NULL（列语义是知识画像引用），档位改落 layer_summary_json.profile")
    void headRowDoesNotAbuseProfileColumns() {
        ReadinessService.EvaluationResult result = service.evaluate(context(), OPERATOR);
        WAgentReadinessDO head = result.assessment();

        assertNull(head.getProfileId());
        assertNull(head.getProfileVersion());
        assertNull(head.getValidUntil(), "有效期无规格数值 ⇒ 留 NULL，禁代码自设一个数冒充规格");
        assertNull(head.getSupersededBy());
        assertEquals(0, head.getIsPinned());
        assertEquals(TENANT, head.getTenantId());
        assertEquals(ORG, head.getOrgId());
        assertEquals(QUESTION_ID, head.getQuestionId());
        assertTrue(head.getLayerSummaryJson().contains("\"profile\":\"default\""));
        assertNotNull(head.getComputedTime(), "定级可复盘的前提是留时间戳");
    }

    @Test
    @DisplayName("无租户上下文 ⇒ 直接拒绝且一行的写入都没发生（禁无主评估落库）")
    void missingTenantIsRejectedBeforeAnyWrite() {
        ReadinessProbeContext noTenant = new ReadinessProbeContext(QUESTION_ID, "m-1", "g-1", "run-1",
                null, "FORECAST", null, ORG, Map.of());

        WAgentOrchestrationException failure =
                assertThrows(WAgentOrchestrationException.class, () -> service.evaluate(noTenant, OPERATOR));

        assertTrue(failure.getMessage().contains("tenant_id"), failure.getMessage());
        verify(itemDao, never()).insert(any());
        verify(readinessDao, never()).insert(any());
        verify(gapService, never()).openGaps(anyString(), any(), any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("无 question_id ⇒ 拒绝（V230 question_id NOT NULL，无锚即无评估）")
    void missingQuestionIdIsRejected() {
        ReadinessProbeContext anchorless = new ReadinessProbeContext(null, null, null, null,
                null, null, TENANT, ORG, Map.of());
        assertThrows(WAgentOrchestrationException.class, () -> service.evaluate(anchorless, OPERATOR));
        verify(readinessDao, never()).insert(any());
    }

    // ══════════════════ 性质 3：只追加 + pinned 豁免 ══════════════════

    @Test
    @DisplayName("硬规则④：旧评估 pinned（markSuperseded 返 0）⇒ 新头照插、旧缺口不动、不抛错")
    void pinnedAssessmentSurvivesReassessment() {
        WAgentReadinessDO pinned = previousAssessment("ra-pinned");
        pinned.setIsPinned(1);
        when(readinessDao.findCurrentByQuestionId(anyString())).thenReturn(Optional.of(pinned));
        when(readinessDao.markSuperseded(anyString(), anyString(), anyString())).thenReturn(0);

        ReadinessService.EvaluationResult result = service.evaluate(context(), OPERATOR);

        assertEquals(1, writtenHeads().size(), "新评估必须仍然追加落库（pinned 只豁免被替代，不豁免新行）");
        verify(readinessDao).markSuperseded("ra-pinned", result.assessmentId(), OPERATOR);
        verify(gapService, never()).supersedeOpenGaps(anyString(), anyString());
        verify(readinessDao, never()).update(any());
        assertNull(result.assessment().getSupersededBy());
    }

    @Test
    @DisplayName("替代位只写指针：旧行内容由 markSuperseded 单条 UPDATE 承担，本类禁整行回写旧评估")
    void supersedeWritesPointerOnly() {
        when(readinessDao.findCurrentByQuestionId(anyString()))
                .thenReturn(Optional.of(previousAssessment("ra-old")));
        when(readinessDao.markSuperseded(anyString(), anyString(), anyString())).thenReturn(1);

        ReadinessService.EvaluationResult result = service.evaluate(context(), OPERATOR);

        verify(readinessDao).markSuperseded("ra-old", result.assessmentId(), OPERATOR);
        verify(gapService).supersedeOpenGaps("ra-old", OPERATOR);
        verify(readinessDao, never()).update(any());
    }

    @Test
    @DisplayName("跨租户的旧当前评估不参与替代（多租户闸 R-36）")
    void previousAssessmentFromOtherTenantIsNotSuperseded() {
        WAgentReadinessDO foreign = previousAssessment("ra-foreign");
        foreign.setTenantId("t-other");
        when(readinessDao.findCurrentByQuestionId(anyString())).thenReturn(Optional.of(foreign));

        service.evaluate(context(), OPERATOR);

        verify(readinessDao, never()).markSuperseded(anyString(), anyString(), anyString());
        verify(gapService, never()).supersedeOpenGaps(anyString(), anyString());
    }

    // ══════════════════ 反事实解锁与层位快照（package-private 直钉） ══════════════════

    @Test
    @DisplayName("反事实解锁='该项置 pass 后重算'：有改进才写档，同档写 null（禁 C->C 冒充进展）")
    void counterfactualUnlockWritesNullWhenNoImprovement() {
        List<CheckOutcome> outcomes = List.of(
                outcome(ReadinessCheckCode.COG_CM_READY, ReadinessCheckStatus.BLOCKER),
                outcome(ReadinessCheckCode.DATA_COVERAGE, ReadinessCheckStatus.FAIL),
                outcome(ReadinessCheckCode.ONTO_CONFLICT_COUNT, ReadinessCheckStatus.PASS));
        ReadinessGradeResult current = new ReadinessGrader().grade(outcomes.stream()
                .map(CheckOutcome::toCheck).toList());
        assertEquals(ReadinessGrade.D, current.grade());

        Map<String, String> unlock = service.counterfactualUnlock(outcomes, current);

        // 补 blocker 后仍有 fail ⇒ C（比 D 好一档）
        assertEquals("C", unlock.get(ReadinessCheckCode.COG_CM_READY.checkCode()));
        // 补 fail 后 blocker 仍在 ⇒ 还是 D ⇒ 无解锁效应
        assertNull(unlock.get(ReadinessCheckCode.DATA_COVERAGE.checkCode()));
        assertEquals(2, unlock.size(), "只给缺项出解锁档，pass 项不进表");
    }

    @Test
    @DisplayName("layer_summary_json：DIKC 按声明序、计数以字符串落、探测留痕四键齐全")
    void layerSummaryKeepsDeclarationOrderAndStringCounters() {
        ReadinessProbeContext ctx = new ReadinessProbeContext(QUESTION_ID, null, null, null,
                null, null, TENANT, ORG, allLayersUp());
        ReadinessGradeResult grade = new ReadinessGrader().grade(
                List.of(ReadinessCheck.of(ReadinessLayer.D, "data.check_coverage", ReadinessCheckStatus.WARN)));

        Map<String, Object> summary = service.layerSummary("default", grade, ctx.probes(), 8);

        assertEquals(List.of("profile", "matched_rule", "check_count", "missing_count",
                        "status_counts", "layers"),
                new ArrayList<>(summary.keySet()));
        @SuppressWarnings("unchecked")
        Map<String, Object> layers = (Map<String, Object>) summary.get("layers");
        assertEquals(List.of("D", "I", "K", "C"), new ArrayList<>(layers.keySet()),
                "层位顺序须与 V230 列序一致，禁 Map.copyOf 丢序");
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) summary.get("status_counts");
        assertEquals("1", counts.get("warn"), "计数以字符串落（数值面禁 double 漂移）");
        @SuppressWarnings("unchecked")
        Map<String, Object> dLayer = (Map<String, Object>) layers.get("D");
        assertEquals("warn", dLayer.get("status"));
        assertEquals(Boolean.TRUE, dLayer.get("probe_reachable"));
        // 无检查项的层 ⇒ unknown（裁决 3），且快照里留得下来
        @SuppressWarnings("unchecked")
        Map<String, Object> cLayer = (Map<String, Object>) layers.get("C");
        assertEquals("unknown", cLayer.get("status"));
    }

    @Test
    @DisplayName("UNKNOWN 不映射 DG 码：证据缺席≠确认要走降级（禁给缺证据的层编降级理由）")
    void unknownMissingItemsDoNotMapToDegradeCodes() {
        ReadinessGradeResult unknownOnly = new ReadinessGrader().grade(
                List.of(ReadinessCheck.of(ReadinessLayer.D, "data.check_coverage", ReadinessCheckStatus.UNKNOWN)));
        assertTrue(service.degradations(unknownOnly).isEmpty());

        ReadinessGradeResult warnOnly = new ReadinessGrader().grade(List.of(
                ReadinessCheck.of(ReadinessLayer.K, "know.applicability", ReadinessCheckStatus.WARN)));
        assertEquals(List.of("DG-K1"), service.degradations(warnOnly));
    }

    @Test
    @DisplayName("DG 码只取检查项自带白名单，禁拼新码（白名单自检面）")
    void degradeCodesComeOnlyFromCheckCodes() {
        for (ReadinessCheckCode code : ReadinessCheckCode.values()) {
            if (code.degradeCode() != null) {
                assertTrue(ReadinessService.whitelistableDegradeCodes().contains(code.degradeCode()),
                        code.checkCode() + " 的 DG 码必须在白名单内");
            }
        }
    }

    // ══════════════════ 性质 4：refill（硬规则⑤ + 轮次先计） ══════════════════

    @Test
    @DisplayName("硬规则⑤：越限轮次 ⇒ 挂起问题、不复评、不计轮，200 语义出缺口清单")
    void refillOverLimitSuspendsQuestionWithoutReassessing() {
        WAgentReadinessDO current = previousAssessment("ra-current");
        current.setGrade("C");
        current.setRunId("run-1");
        WAgentQuestionDO question = question();
        RefillLedger exhausted = new RefillLedger(run("run-1"),
                new BudgetSnapshot(60, 40, 30, 30, 3, 3), new Consumption(10, 5, 0, 3));
        when(gapService.loadRefillLedger(current, question)).thenReturn(exhausted);
        when(questionService.markAwaitingInput(QUESTION_ID, TENANT, OPERATOR)).thenReturn(true);
        WAgentReadinessGapDO gap = gap("gap-1", "fetch", "data.check_coverage");
        when(gapService.findOpenGaps("ra-current")).thenReturn(List.of(gap));

        ReadinessService.RefillResult result = service.refill(question, current, List.of(), null, OPERATOR);

        assertTrue(result.awaitingInput());
        assertEquals(3, result.refillRound());
        assertEquals(3, result.maxRefillRounds());
        assertEquals("ra-current", result.assessmentId(), "超限轮次不产生新评估");
        assertTrue(result.taskIds().isEmpty(), "超限不派发");
        assertTrue(result.deferred().isEmpty());
        assertEquals(1, result.openGaps().size());
        verify(questionService).markAwaitingInput(QUESTION_ID, TENANT, OPERATOR);
        verify(gapService, never()).recordRefillRound(any(), any(), anyString());
        verify(gapService, never()).dispatchResolutionTasks(any(), any(), any(), anyString());
        verify(readinessDao, never()).insert(any());
    }

    @Test
    @DisplayName("裁决 D-B2④-13：轮次在复评之前落账（复评失败也计一轮，禁靠'不计轮'无限重试）")
    void refillConsumesRoundBeforeReassessing() {
        WAgentReadinessDO current = previousAssessment("ra-current");
        current.setRunId("run-1");
        WAgentQuestionDO question = question();
        RefillLedger ledger = new RefillLedger(run("run-1"),
                new BudgetSnapshot(60, 40, 30, 30, 3, 3), new Consumption(10, 5, 0, 1));
        when(gapService.loadRefillLedger(current, question)).thenReturn(ledger);
        when(gapService.recordRefillRound(ledger, null, OPERATOR))
                .thenReturn(new Consumption(10, 5, 0, 2));
        WAgentReadinessGapDO gap = gap("gap-1", "task", "cog.cm_ready");
        when(gapService.findOpenGaps("ra-current")).thenReturn(List.of(gap));
        when(gapService.dispatchResolutionTasks(any(), any(), any(), anyString()))
                .thenReturn(new TaskDispatch(List.of("task-7"), List.of()));

        ReadinessService.RefillResult result =
                service.refill(question, current, List.of("gap-1"), "task", OPERATOR);

        assertFalse(result.awaitingInput());
        assertEquals(2, result.refillRound(), "返回的是刚落账后的轮次，不是调用前的");
        assertEquals(List.of("task-7"), result.taskIds());
        assertEquals("C", result.previousGrade());
        assertEquals("A", result.newGrade(), "复评重新定级（八项补齐后全 pass）");

        verify(gapService).dispatchResolutionTasks(eq(List.of(gap)), eq("task"), any(), eq(OPERATOR));

        InOrder order = inOrder(gapService, readinessDao);
        order.verify(gapService).recordRefillRound(ledger, null, OPERATOR);
        order.verify(gapService).dispatchResolutionTasks(any(), any(), any(), anyString());
        order.verify(readinessDao).insert(any());
        order.verify(gapService).attachAssessmentToRun(any(), eq(result.assessmentId()), eq(OPERATOR));
    }

    @Test
    @DisplayName("伪造/跨评估 gapId ⇒ 掩 404 且发生在计轮之前（禁把不可派发的请求记成一轮消耗）")
    void refillRejectsForeignGapIdBeforeConsumingRound() {
        WAgentReadinessDO current = previousAssessment("ra-current");
        current.setRunId("run-1");
        WAgentQuestionDO question = question();
        when(gapService.loadRefillLedger(current, question)).thenReturn(new RefillLedger(run("run-1"),
                new BudgetSnapshot(60, 40, 30, 30, 3, 3), Consumption.zero()));
        when(gapService.findOpenGaps("ra-current")).thenReturn(List.of(gap("gap-real", "task", "cog.cm_ready")));

        assertThrows(WAgentOrchestrationException.class,
                () -> service.refill(question, current, List.of("gap-forged"), null, OPERATOR));

        verify(gapService, never()).recordRefillRound(any(), any(), anyString());
        verify(readinessDao, never()).insert(any());
    }

    @Test
    @DisplayName("空 gapIds ⇒ 对本次全部 open 缺口派发（#13 默认语义）")
    void refillWithoutGapIdsSelectsAllOpenGaps() {
        WAgentReadinessDO current = previousAssessment("ra-current");
        current.setRunId("run-1");
        WAgentQuestionDO question = question();
        when(gapService.loadRefillLedger(current, question)).thenReturn(new RefillLedger(run("run-1"),
                new BudgetSnapshot(60, 40, 30, 30, 3, 3), Consumption.zero()));
        List<WAgentReadinessGapDO> open = List.of(
                gap("gap-1", "task", "cog.cm_ready"), gap("gap-2", "fetch", "data.check_coverage"));
        when(gapService.findOpenGaps("ra-current")).thenReturn(open);
        when(gapService.recordRefillRound(any(), isNull(), anyString())).thenReturn(Consumption.zero());
        when(gapService.dispatchResolutionTasks(any(), any(), any(), anyString()))
                .thenReturn(new TaskDispatch(List.of(), List.of(Map.of("gap_id", "gap-2"))));

        service.refill(question, current, List.of(), null, OPERATOR);

        ArgumentCaptor<List<WAgentReadinessGapDO>> picked = ArgumentCaptor.forClass(List.class);
        verify(gapService).dispatchResolutionTasks(picked.capture(), isNull(), any(), eq(OPERATOR));
        assertEquals(List.of("gap-1", "gap-2"),
                picked.getValue().stream().map(WAgentReadinessGapDO::getId).toList());
    }

    @Test
    @DisplayName("问题→Run→Goal 反查：goalId 从 Mission 取，取不到留 null 由取数面记 absent（禁造锚点）")
    void goalAnchorIsResolvedThroughMission() {
        WAgentQuestionDO question = question();
        question.setMissionId("m-1");
        WAgentMissionDO mission = new WAgentMissionDO();
        mission.setId("m-1");
        mission.setGoalId("goal-9");
        when(missionDao.findById("m-1")).thenReturn(Optional.of(mission));

        ReadinessProbeContext ctx = service.contextFor(question, "run-1");

        assertEquals("goal-9", ctx.goalId());
        assertEquals("m-1", ctx.missionId());
        assertEquals(QUESTION_ID, ctx.questionId());
        assertEquals(TENANT, ctx.tenantId());

        when(missionDao.findById("m-missing")).thenReturn(Optional.empty());
        WAgentQuestionDO orphan = question();
        orphan.setMissionId("m-missing");
        assertNull(service.contextFor(orphan, null).goalId(), "反查不到即 null，禁编造 goalId");
    }

    @Test
    @DisplayName("FORECAST 意图走 forecast 档：回测缺席在 forecast 档是 blocker、default 档只是 warn")
    void intentSelectsThresholdProfile() {
        suppliedMetrics.put(ReadinessCheckCode.COG_BACKTEST_READY, MetricValue.bool(false));
        suppliedThresholds.put(ReadinessCheckCode.COG_BACKTEST_READY,
                setFor(ReadinessCheckCode.COG_BACKTEST_READY, "forecast", "true"));

        ReadinessProbeContext forecastCtx = new ReadinessProbeContext(QUESTION_ID, null, null, null,
                null, "FORECAST", TENANT, ORG, Map.of());
        assertEquals(ReadinessGrade.D, service.evaluate(forecastCtx, OPERATOR).grade().grade(),
                "forecast 档未回测 ⇒ blocker ⇒ D");

        suppliedThresholds.put(ReadinessCheckCode.COG_BACKTEST_READY,
                setFor(ReadinessCheckCode.COG_BACKTEST_READY, "default", "true"));
        ReadinessProbeContext defaultCtx = new ReadinessProbeContext(QUESTION_ID, null, null, null,
                null, "DIAGNOSE", TENANT, ORG, Map.of());
        assertEquals(ReadinessGrade.B, service.evaluate(defaultCtx, OPERATOR).grade().grade(),
                "default 档未回测 ⇒ warn ⇒ B（强度来自命中档位，不写死在代码）");
    }

    // ══════════════════ 夹具 ══════════════════

    private List<WAgentReadinessDO> writtenHeads() {
        ArgumentCaptor<WAgentReadinessDO> captor = ArgumentCaptor.forClass(WAgentReadinessDO.class);
        verify(readinessDao, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
        return captor.getAllValues();
    }

    private Stream<ReadinessMetricSource> availableSources() {
        ReadinessMetricSource source = new ReadinessMetricSource() {
            @Override
            public boolean supports(ReadinessCheckCode code) {
                return suppliedMetrics.containsKey(code);
            }

            @Override
            public MetricValue measure(ReadinessCheckCode code, ReadinessProbeContext context) {
                return suppliedMetrics.get(code);
            }

            @Override
            public String sourceId() {
                return "test-metric-source";
            }
        };
        return Stream.of(source);
    }

    private static ReadinessProbeContext context() {
        return new ReadinessProbeContext(QUESTION_ID, "m-1", "goal-1", "run-1",
                null, "DIAGNOSE", TENANT, ORG, Map.of());
    }

    private static WAgentQuestionDO question() {
        WAgentQuestionDO question = new WAgentQuestionDO();
        question.setId(QUESTION_ID);
        question.setMissionId("m-1");
        question.setIntent("DIAGNOSE");
        question.setTenantId(TENANT);
        question.setOrgId(ORG);
        question.setLatestRunId("run-1");
        return question;
    }

    private static WAgentRunDO run(String id) {
        WAgentRunDO run = new WAgentRunDO();
        run.setId(id);
        run.setQuestionId(QUESTION_ID);
        run.setTenantId(TENANT);
        run.setReadinessId("ra-current");
        return run;
    }

    private static WAgentReadinessDO previousAssessment(String id) {
        WAgentReadinessDO assessment = new WAgentReadinessDO();
        assessment.setId(id);
        assessment.setQuestionId(QUESTION_ID);
        assessment.setTenantId(TENANT);
        assessment.setOrgId(ORG);
        assessment.setGrade("C");
        assessment.setIsPinned(0);
        return assessment;
    }

    private static WAgentReadinessGapDO gap(String id, String actionType, String checkCode) {
        WAgentReadinessGapDO gap = new WAgentReadinessGapDO();
        gap.setId(id);
        gap.setAssessmentId("ra-current");
        gap.setTenantId(TENANT);
        gap.setStatus("open");
        gap.setSuggestedActionText(ReadinessGapService.encodeSuggestedAction(actionType, checkCode));
        return gap;
    }

    /** 八项都给"通过判定"的度量值（各用例按需覆盖单项）。 */
    private void seedPassingMetrics() {
        suppliedMetrics.put(ReadinessCheckCode.DATA_COVERAGE, MetricValue.number(new BigDecimal("0.9")));
        suppliedMetrics.put(ReadinessCheckCode.DATA_STALENESS, MetricValue.number(BigDecimal.ONE));
        suppliedMetrics.put(ReadinessCheckCode.ONTO_CALIBER_DEFINED, MetricValue.bool(true));
        suppliedMetrics.put(ReadinessCheckCode.ONTO_CONFLICT_COUNT, MetricValue.number(BigDecimal.ZERO));
        suppliedMetrics.put(ReadinessCheckCode.KNOW_SAMPLE_SIZE, MetricValue.number(new BigDecimal("40")));
        suppliedMetrics.put(ReadinessCheckCode.KNOW_APPLICABILITY, MetricValue.number(new BigDecimal("0.9")));
        suppliedMetrics.put(ReadinessCheckCode.COG_CM_READY, MetricValue.bool(true));
        suppliedMetrics.put(ReadinessCheckCode.COG_BACKTEST_READY, MetricValue.bool(true));
    }

    /** 八项都给 seed 形态阈值（default 档）。 */
    private void seedPassingThresholds() {
        for (ReadinessCheckCode code : ReadinessCheckCode.values()) {
            suppliedThresholds.put(code, setFor(code, "default", passingRaw(code)));
        }
    }

    private static String passingRaw(ReadinessCheckCode code) {
        return switch (code) {
            case DATA_COVERAGE -> "0.7";
            case DATA_STALENESS -> "7";
            case ONTO_CALIBER_DEFINED -> "true";
            case ONTO_CONFLICT_COUNT -> "0";
            case KNOW_SAMPLE_SIZE -> "30";
            case KNOW_APPLICABILITY -> "0.6";
            case COG_CM_READY -> "true";
            case COG_BACKTEST_READY -> "true";
        };
    }

    /**
     * 造一个片段齐全、来源档位留痕的阈值集合（KNOW_SAMPLE_SIZE 有两片段，趋势下限取 seed 的 min/3）。
     */
    private static ThresholdSet setFor(ReadinessCheckCode code, String profile, String raw) {
        Map<String, ThresholdValue> items = new LinkedHashMap<>();
        for (String fragment : code.thresholdFragments()) {
            String value = "know.sample_size_trend_floor".equals(fragment) ? "10" : raw;
            items.put(fragment, new ThresholdValue(fragment,
                    ThresholdPolicy.keyOf(profile, fragment), value, profile));
        }
        return new ThresholdSet(profile, items);
    }

    private static CheckOutcome outcome(ReadinessCheckCode code, ReadinessCheckStatus status) {
        return new CheckOutcome(code, MetricValue.absent("fixture"),
                setFor(code, "default", "true"), Judgement.of(status, "fixture:" + status.value()),
                List.of(), null);
    }

    private static Map<ReadinessLayer, LayerProbe> allLayersUp() {
        Map<ReadinessLayer, LayerProbe> probes = new EnumMap<>(ReadinessLayer.class);
        for (ReadinessLayer layer : ReadinessLayer.values()) {
            probes.put(layer, new LayerProbe(layer, "/api/v1/engine/" + layer.value() + "/health",
                    true, "UP", null, Map.of()));
        }
        return probes;
    }
}
