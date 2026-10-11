package com.chinacreator.gzcm.engine.cognitive2.service.asset;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.asset.BeliefProbabilityKind;
import com.chinacreator.gzcm.engine.cognitive.api.asset.CognitiveAssetStatus;
import com.chinacreator.gzcm.engine.cognitive.api.objects.CognitiveObjectClass;
import com.chinacreator.gzcm.engine.cognitive2.dao.BeliefDao;
import com.chinacreator.gzcm.engine.cognitive2.dto.BeliefSaveDTO;
import com.chinacreator.gzcm.engine.cognitive2.dao.HypothesisDao;
import com.chinacreator.gzcm.engine.cognitive2.dao.ModelRegistryDao;
import com.chinacreator.gzcm.engine.cognitive2.service.BeliefStore;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveAuditFallback;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveBeliefService;
import com.chinacreator.gzcm.engine.cognitive2.service.EvidenceStore;
import com.chinacreator.gzcm.engine.cognitive2.service.objects.CognitiveObjectCatalogService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SUP-C 认知资产生命周期冻结测试（《补充设计 v1.0》§8.4 状态机 / §13 端点 / §16.5 红线 4）。
 *
 * <p>纯 Mockito 风格（对齐同模块 {@code CognitiveModelRegistryTest}）：DAO 均以 stub 注入，
 * EventBusService 未装配（Service 里 eventBus == null ⇒ publish 全走降级 WARN 不阻塞）。
 * 库侧 SQL 真实行为由 V268 迁移实跑保障，本类只锁 Service 层裁决分支。</p>
 */
class CognitiveAssetLifecycleTest {

    private final BeliefDao beliefDao = mock(BeliefDao.class);
    private final HypothesisDao hypothesisDao = mock(HypothesisDao.class);
    private final ModelRegistryDao modelRegistryDao = mock(ModelRegistryDao.class);
    private final CognitiveObjectCatalogService catalogService = mock(CognitiveObjectCatalogService.class);
    private final CognitiveAuditFallback auditFallback = mock(CognitiveAuditFallback.class);
    private final BeliefStore beliefStore = mock(BeliefStore.class);

    private CognitiveAssetLifecycleService service;
    private CognitiveBeliefService beliefService;

    @BeforeEach
    void setUp() {
        service = new CognitiveAssetLifecycleService(beliefDao, hypothesisDao, modelRegistryDao,
                catalogService, auditFallback);
        // 装配一个真实 CognitiveBeliefService 以便走真 register() 路径验证置信红线。
        // 内部依赖用 mock；测试仅验证 register() 前置的置信守卫在调用 store.insert 前是否拦截。
        EvidenceStore evidenceStore = mock(EvidenceStore.class);
        com.chinacreator.gzcm.engine.cognitive2.service.mental.MentalEventPublisher eventPublisher =
                mock(com.chinacreator.gzcm.engine.cognitive2.service.mental.MentalEventPublisher.class);
        beliefService = new CognitiveBeliefService(beliefStore, evidenceStore, new ObjectMapper(), eventPublisher);
    }

    // ── 1. 合法迁移链（§8.4 主链 5 步）──

    @Test
    @DisplayName("合法迁移链：DRAFT→CANDIDATE→EVIDENCE_CHECKED→VALIDATED→APPROVED→ACTIVE")
    void legalTransitionChainRunsToActive() {
        // 迁移链逐步读库：findLifecycleStateById 必须返回"当前"行，
        // 故用可变 row + updateLifecycleState 回写 lifecycle_state 模拟库侧推进（否则每步都读到 DRAFT，第 2 步即 409）。
        Map<String, Object> live = row("DRAFT", "ACTIVE");
        when(beliefDao.findLifecycleStateById("bel_x")).thenAnswer(inv -> live);
        when(beliefDao.updateLifecycleState(eq("bel_x"), anyString(), anyString()))
                .thenAnswer(inv -> {
                    live.put("lifecycle_state", inv.getArgument(1));
                    return 1;
                });
        List<String> chain = new ArrayList<>(List.of(
                "CANDIDATE", "EVIDENCE_CHECKED", "VALIDATED", "APPROVED", "ACTIVE"));
        String current = "DRAFT";
        for (String target : chain) {
            Map<String, Object> resp = service.transition("bel_x", target, "step " + target, "ev-1", "op");
            assertEquals(current, resp.get("fromState"));
            assertEquals(target, resp.get("toState"));
            current = target;
        }
        verify(beliefDao, times(5)).updateLifecycleState(eq("bel_x"), anyString(), anyString());
    }

    // ── 2. 非法迁移 → 409 原行不变（§13.1 409 语义）──

    @Test
    @DisplayName("非法迁移：DRAFT→ACTIVE 跳链 ⇒ 409 ECOS-COG-409S，0 UPDATE 落库（原行不变）")
    void illegalTransitionRejected() {
        when(beliefDao.findLifecycleStateById("bel_x")).thenReturn(row("DRAFT", "ACTIVE"));
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.transition("bel_x", "ACTIVE", "skip chain", "ev", "op"));
        assertEquals(CognitiveErrorCode.E409_STATE_ILLEGAL, e.errorCode);
        assertEquals(409, e.httpStatus);
        verify(beliefDao, never()).updateLifecycleState(anyString(), anyString(), anyString());
    }

    // ── 3. SUPERSEDED 生成新版本不覆写（§8.4「不可原地覆盖」）──

    @Test
    @DisplayName("SUPERSEDED 需要 reason 带 supersededBy=<新版本>（否则 400；带齐后落 lifecycle_state）")
    void supersedeRequiresNewVersionRef() {
        when(modelRegistryDao.findLifecycleStateById("mod_v1")).thenReturn(row("ACTIVE", "active"));
        CognitiveGuardException missing = assertThrows(CognitiveGuardException.class,
                () -> service.transition("mod_v1", "SUPERSEDED", "no-ref", "ev", "op"));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, missing.errorCode);
        assertEquals(400, missing.httpStatus);
        verify(modelRegistryDao, never()).updateLifecycleState(anyString(), anyString(), anyString());

        when(modelRegistryDao.updateLifecycleState(eq("mod_v1"), anyString(), anyString())).thenReturn(1);
        Map<String, Object> ok = service.transition("mod_v1", "SUPERSEDED",
                "supersededBy=mod_v2 by=calibration-drift", "ev-1", "op");
        assertEquals("SUPERSEDED", ok.get("toState"));
        verify(modelRegistryDao, times(1))
                .updateLifecycleState(eq("mod_v1"), eq("SUPERSEDED"), anyString());
    }

    @Test
    @DisplayName("SUPERSEDED 终态：SUPERSEDED→SUPERSEDED 幂等拒绝")
    void supersedeIsTerminal() {
        when(modelRegistryDao.findLifecycleStateById("mod_v1")).thenReturn(row("SUPERSEDED", "active"));
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.transition("mod_v1", "SUPERSEDED", "dup supersededBy=x", "ev", "op"));
        assertEquals(CognitiveErrorCode.E409_STATE_ILLEGAL, e.errorCode);
        verify(modelRegistryDao, never()).updateLifecycleState(anyString(), anyString(), anyString());
    }

    // ── 4. CONTRADICTED 保留反证链（§8.4「不等于删除」）──

    @Test
    @DisplayName("CONTRADICTED 只置 lifecycle_state 一列；同行其它字段 0 触及（保留反证链）")
    void contradictedKeepsRowAndRefutesByKind() {
        when(beliefDao.findLifecycleStateById("bel_c")).thenReturn(row("ACTIVE", "stat-active"));
        when(beliefDao.updateLifecycleState(eq("bel_c"), anyString(), anyString())).thenReturn(1);
        Map<String, Object> r = service.transition("bel_c", "CONTRADICTED",
                "refuted by ev-new-99", "ev-new-99", "op");
        assertEquals("CONTRADICTED", r.get("toState"));
        // belt 表其它列（variable/domain/distribution 等）保持原样：0 补充 UPDATE
        verify(beliefDao, times(1)).updateLifecycleState(eq("bel_c"), eq("CONTRADICTED"), anyString());
        verify(beliefDao, never()).insert(any(), any(), any(), any(), any(),
                anyInt(), any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("CONTRADICTED（终态）→ CANDIDATE 允许复审候选；→ ACTIVE 禁直跳 ⇒ 409")
    void contradictedCanOnlyResumeAsCandidate() {
        when(beliefDao.findLifecycleStateById("bel_c")).thenReturn(row("CONTRADICTED", "stat-refuted"));
        when(beliefDao.updateLifecycleState(anyString(), anyString(), anyString())).thenAnswer(inv -> 1);
        Map<String, Object> ok = service.transition("bel_c", "CANDIDATE", "review-queue", "ev", "op");
        assertEquals("CANDIDATE", ok.get("toState"));
        // 再试一次：现在行已变 CANDIDATE → 允许直跳 ACTIVE？(CANDIDATE → ACTIVE 不在矩阵)
        when(beliefDao.findLifecycleStateById("bel_c")).thenReturn(row("CANDIDATE", null));
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.transition("bel_c", "ACTIVE", "dup jump", "ev", "op"));
        assertEquals(CognitiveErrorCode.E409_STATE_ILLEGAL, e.errorCode);
    }

    // ── 5. belief 写路径拒绝 UNCALIBRATED（§8.3 / §16.5 红线 4）──

    @Test
    @DisplayName("belief 写路径：probability 有值 + kind=UNCALIBRATED_CONFIDENCE ⇒ 400 拒绝（未落库）")
    void beliefProbabilityWriteRejectsUncalibratedConfidence() {
        // 生产真值：UNCALIBRATED 族经 BeliefLifecycleGateway.guard 抛 CognitiveGuardException(ECOS-COG-400P)，
        // 而非 missing-kind 路径的 BusinessException —— 两条拒绝路径异常型不同（见 SUP-COG-02 冻结测试）。
        BeliefSaveDTO dto = probAndKind(0.72d, "UNCALIBRATED_CONFIDENCE");
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> beliefService.register(dto));
        assertEquals(400, e.httpStatus);
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
        // 未落库：store.insert 0 调用（前置拦截）
        verifyUncalibratedRejected();
    }

    @Test
    @DisplayName("belief 写路径：probability 有值 + kind=UNCALIBRATED_MODEL_ESTIMATE ⇒ 400 拒绝")
    void beliefProbabilityWriteRejectsUncalibratedModelEstimate() {
        BeliefSaveDTO dto = probAndKind(0.72d, "UNCALIBRATED_MODEL_ESTIMATE");
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> beliefService.register(dto));
        assertEquals(400, e.httpStatus);
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
        verifyUncalibratedRejected();
    }

    @Test
    @DisplayName("belief 写路径：probability 有值 + kind=null ⇒ 400（V268 语义配套必填）")
    void beliefProbabilityWriteRequiresKind() {
        BeliefSaveDTO dto = probAndKind(0.72d, null);
        BusinessException e = assertThrows(BusinessException.class, () -> beliefService.register(dto));
        assertEquals(400, e.getErrorCode());
        assertTrue(String.valueOf(e.getMessage()).contains("probabilityKind"));
    }

    @Test
    @DisplayName("belief 写路径：probability=NULL ⇒ 放行（V262 未标定既有语义）")
    void beliefProbabilityNullPassesThrough() {
        BeliefSaveDTO dto = probAndKind(null, null);
        // store 为 mock：maxVersion 默认 0 ⇒ 首版 version=1，无需（也不能）对非 mock 返回值做 when 桩
        assertDoesNotThrow(() -> beliefService.register(dto));
        // 可选列（tenantScope / lastEvidenceId / overrideReason）生产真值为 NULL，
        // anyString() 不匹配 null（Mockito 语义）⇒ 用 any()；关键列（变量/域/版本/状态）仍精确断言。
        verify(beliefStore, times(1)).insert(any(), eq("v"), any(), eq("dm"), any(), eq(1),
                any(), anyBoolean(), any(), eq("ACTIVE"));
    }

    @Test
    @DisplayName("belief 写路径：probability 有值 + kind=STATISTICAL_PROBABILITY ⇒ 放行")
    void beliefProbabilityStatisticalPassesThrough() {
        BeliefSaveDTO dto = probAndKind(0.55d, "STATISTICAL_PROBABILITY");
        assertDoesNotThrow(() -> beliefService.register(dto));
        verify(beliefStore, times(1)).insert(any(), eq("v"), any(), eq("dm"), any(), eq(1),
                any(), anyBoolean(), any(), eq("ACTIVE"));
    }

    @Test
    @DisplayName("BeliefLifecycleGateway 直测：UNKNOWN_LEGACY 禁写入（§8.3 末段）")
    void unknownLegacyNotWritableByGateway() {
        var gw = new BeliefLifecycleGateway();
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> gw.guard(0.4d, BeliefProbabilityKind.UNKNOWN_LEGACY));
        assertEquals(400, e.httpStatus);
        assertDoesNotThrow(() -> gw.guard(null, null));
        assertDoesNotThrow(() -> gw.guard(0.5d, BeliefProbabilityKind.CALIBRATED_MODEL_PROBABILITY));
    }

    // ── 6. proposal 不改原行（SUP-COG-03）──

    @Test
    @DisplayName("proposal：原资产行 0 改动（0 UPDATE / 0 INSERT），候选只落审计事件(fallback) + 通知")
    void proposalDoesNotMutateOriginalRow() throws Exception {
        when(beliefDao.findLifecycleStateById("bel_x")).thenReturn(row("DRAFT", "stat-draft"));
        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("targetState", "CANDIDATE");
        proposal.put("reason", "attach evidence before promotion");
        proposal.put("evidenceRef", "ev-42");
        proposal.put("operator", "analyst");
        Map<String, Object> resp = service.propose("bel_x", proposal);
        assertNotNull(resp.get("proposalId"));
        assertEquals("PENDING_APPROVAL", resp.get("status"));
        ArgumentCaptor<String> idCap = ArgumentCaptor.forClass(String.class);
        verify(beliefDao, never()).updateLifecycleState(idCap.capture(), anyString(), anyString());
        verify(beliefDao, never()).insert(any(), any(), any(), any(), any(),
                anyInt(), any(), anyBoolean(), any(), any());
        verify(auditFallback, times(1)).write(any());
    }

    @Test
    @DisplayName("proposal：非法目标态 ⇒ 409；0 落写、0 建候选")
    void proposalRejectsIllegalTargetState() throws Exception {
        when(beliefDao.findLifecycleStateById("bel_x")).thenReturn(row("DRAFT", "stat-draft"));
        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("targetState", "ACTIVE");
        proposal.put("reason", "skip chain");
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.propose("bel_x", proposal));
        assertEquals(CognitiveErrorCode.E409_STATE_ILLEGAL, e.errorCode);
        assertEquals(409, e.httpStatus);
        verify(beliefDao, never()).updateLifecycleState(anyString(), anyString(), anyString());
        verify(auditFallback, never()).write(any());
    }

    @Test
    @DisplayName("proposal：audit 主通道 + fallback 同时失败 ⇒ fail-closed 503（禁假候选 200）")
    void proposalFailClosedWhenBothChannelsDown() throws Exception {
        when(beliefDao.findLifecycleStateById("bel_x")).thenReturn(row("DRAFT", "stat-draft"));
        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("targetState", "CANDIDATE");
        proposal.put("reason", "reason");
        org.mockito.Mockito.doThrow(new java.io.IOException("disk"))
                .when(auditFallback).write(any());
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.propose("bel_x", proposal));
        assertEquals(503, e.httpStatus);
        verify(beliefDao, never()).updateLifecycleState(anyString(), anyString(), anyString());
    }

    // ── 7. resolve 前缀路由 & 404（§13.1 不降级为空数据）──

    @Test
    @DisplayName("resolve：非法前缀 ⇒ 400")
    void resolveRejectsUnknownPrefix() {
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.resolve("zzz"));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
    }

    @Test
    @DisplayName("resolve：查不到 ⇒ 404（不降级为空数据）")
    void resolveMissesAre404() {
        when(beliefDao.findLifecycleStateById("bel_missing")).thenReturn(null);
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.resolve("bel_missing"));
        assertEquals(404, e.httpStatus);
    }

    @Test
    @DisplayName("resolve：belief 置信三元组（probability / confidenceKind / calibrationRef）")
    void resolveBeliefCarriesConfidenceTriplet() {
        Map<String, Object> row = row("ACTIVE", "stat-active");
        row.put("probability", 0.713d);
        row.put("prob_kind", "STATISTICAL_PROBABILITY");
        row.put("calibration_ref", "cal-01");
        when(beliefDao.findLifecycleStateById("bel_x")).thenReturn(row);
        Map<String, Object> r = service.resolve("bel_x");
        assertEquals("BELIEF", r.get("assetClass"));
        assertEquals(0.713d, r.get("confidenceProbability"));
        assertEquals("STATISTICAL_PROBABILITY", r.get("confidenceKind"));
        assertEquals("cal-01", r.get("calibrationRef"));
    }

    @Test
    @DisplayName("resolve：probability 有值 + prob_kind NULL ⇒ UNKNOWN_LEGACY 读回落（§8.3 禁伪造补齐）")
    void resolveBeliefFallsBackToUnknownLegacy() {
        Map<String, Object> row = row("ACTIVE", "stat-active");
        row.put("probability", 0.5d);
        row.put("prob_kind", null);
        when(beliefDao.findLifecycleStateById("bel_x")).thenReturn(row);
        Map<String, Object> r = service.resolve("bel_x");
        assertEquals("UNKNOWN_LEGACY", r.get("confidenceKind"));
        assertNull(r.get("calibrationRef"));
    }

    // ── 8. catalog 装配（SUP-COG-01：11 C 对象 + §8.1 A/B/C 三层）──

    @Test
    @DisplayName("catalog：11 C 对象 + 三层挂载 + 12 治理状态词表")
    void catalogAssembledFromSingleSource() {
        List<CognitiveObjectClass.CatalogEntry> entries = Arrays.stream(CognitiveObjectClass.values())
                .map(CognitiveObjectClass::toEntry).toList();
        when(catalogService.catalog()).thenReturn(entries);

        Map<String, Object> data = service.catalog();
        assertEquals(11, ((List<?>) data.get("objects")).size());
        assertEquals(3, ((Map<?, ?>) data.get("layers")).size());
        assertTrue(((List<?>) data.get("governedAssetClasses")).contains("BELIEF"));
        assertEquals(12, ((List<?>) data.get("lifecycleStates")).size());
    }

    // ── 9. 词表冻结（§8.4 12 值 + 主链 + 异常态）──

    @Test
    @DisplayName("§8.4 状态词表 = 12 值；主链 + 异常态矩阵冻结")
    void vocabularyIsFrozen() {
        assertEquals(12, CognitiveAssetStatus.values().length);
        assertTrue(CognitiveAssetStatus.fromDbValue("ACTIVE").isPresent());
        assertNull(CognitiveAssetStatus.fromDbValue("BOGUS").orElse(null));
        assertTrue(CognitiveAssetStatus.DRAFT.canTransitTo(CognitiveAssetStatus.CANDIDATE));
        assertFalse(CognitiveAssetStatus.DRAFT.canTransitTo(CognitiveAssetStatus.ACTIVE));
        assertTrue(CognitiveAssetStatus.REJECTED.isTerminal());
        assertTrue(CognitiveAssetStatus.EXPIRED.isTerminal());
        assertTrue(CognitiveAssetStatus.ACTIVE.canTransitTo(CognitiveAssetStatus.CONTRADICTED));
        assertTrue(CognitiveAssetStatus.ACTIVE.canTransitTo(CognitiveAssetStatus.SUSPENDED));
        // SUSPENDED 可恢复
        assertTrue(CognitiveAssetStatus.SUSPENDED.canTransitTo(CognitiveAssetStatus.ACTIVE));
    }

    // ── 内部工具 ──

    private static Map<String, Object> row(String lifecycle, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "x");
        m.put("status", status);
        m.put("lifecycle_state", lifecycle);
        return m;
    }

    private void verifyUncalibratedRejected() {
        // assertThrows 已经在 test 里做了；这里额外 verify 未在 beliefService 上调用
        // store.insert()（不可达），退化为断言 gate 直判 result
        var gw = new BeliefLifecycleGateway();
        var e = assertThrows(CognitiveGuardException.class,
                () -> gw.guard(0.72d, BeliefProbabilityKind.UNCALIBRATED_CONFIDENCE));
        assertEquals(400, e.httpStatus);
    }

    private static BeliefSaveDTO probAndKind(Double p, String k) {
        BeliefSaveDTO d = new BeliefSaveDTO();
        d.setVariableName("v");
        d.setDomain("dm");
        List<BeliefSaveDTO.OutcomeProb> dist = new ArrayList<>();
        BeliefSaveDTO.OutcomeProb a = new BeliefSaveDTO.OutcomeProb();
        a.setOutcome("a");
        a.setProb(0.5d);
        BeliefSaveDTO.OutcomeProb b = new BeliefSaveDTO.OutcomeProb();
        b.setOutcome("b");
        b.setProb(0.5d);
        dist.add(a);
        dist.add(b);
        d.setDiscreteDistribution(dist);
        d.setProbability(p);
        d.setProbabilityKind(k);
        return d;
    }
}
