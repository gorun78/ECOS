package com.chinacreator.gzcm.engine.cognitive2.service.asset;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.asset.BeliefProbabilityKind;
import com.chinacreator.gzcm.engine.cognitive2.dto.BeliefSaveDTO;
import com.chinacreator.gzcm.engine.cognitive2.service.BeliefStore;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveBeliefService;
import com.chinacreator.gzcm.engine.cognitive2.service.EvidenceStore;
import com.chinacreator.gzcm.engine.cognitive2.service.mental.MentalEventPublisher;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * SUP-COG-02 belief 置信三分写前网关聚焦测试
 * （《认知域与智能域补充详细设计 v1.0》§8.3 / §16.5 红线 4 / §16 SUP-COG-02）。
 *
 * <p>自 {@code CognitiveAssetLifecycleTest} 抽出的 belief-probability 写守卫单测
 * （Mockito 装配口径与之一致：真实 {@link CognitiveBeliefService} + mock
 * {@link BeliefStore}/{@link EvidenceStore}/{@link MentalEventPublisher}，EventBusService
 * 字段级可选依赖不装配 ⇒ 审计降级 WARN 不阻塞）。</p>
 *
 * <p>裁决规则（{@link BeliefLifecycleGateway#guard}，全部 belief 写路径唯一收口）：
 * <ul>
 *   <li>probability 非空 + kind ∈ UNCALIBRATED 族（UNCALIBRATED_CONFIDENCE /
 *       UNCALIBRATED_MODEL_ESTIMATE）⇒ 400 拒绝 —— LLM/模型置信估计禁直入
 *       {@code probability} 列，禁入金额计算/自动审批；</li>
 *   <li>probability 非空 + kind 缺失/未登记 ⇒ 400（V268 语义配套必填；
 *       UNKNOWN_LEGACY 是读侧回落，禁写入）；</li>
 *   <li>probability 非空 + STATISTICAL_PROBABILITY / CALIBRATED_MODEL_PROBABILITY ⇒ 放行；</li>
 *   <li>probability = NULL ⇒ 放行（V262 既有语义：NULL = 尚未标定，禁补 0）。</li>
 * </ul>
 * 所有拒绝用例同时验证 <b>0 落库</b>（store.insert 在守卫之后才可达。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SUP-COG-02 belief 置信三分写前网关 (BeliefLifecycleGateway / CognitiveBeliefService)")
class BeliefProbabilityKindGuardTest {

    @Mock
    private BeliefStore store;
    @Mock
    private EvidenceStore evidenceStore;
    @Mock
    private MentalEventPublisher eventPublisher;

    private CognitiveBeliefService beliefService;

    @BeforeEach
    void setUp() {
        beliefService = new CognitiveBeliefService(store, evidenceStore, new ObjectMapper(),
                eventPublisher);
    }

    // ── 1. UNCALIBRATED 族拒写（§16.5 红线 4）──
    // 注意（生产行为原样断言）：UNCALIBRATED 族命中时 register() 经
    // BeliefLifecycleGateway.guard 抛 CognitiveGuardException(400P)，
    // 而非 missing-kind 路径的 BusinessException(400 COG-400) —— 两条拒绝路径异常型不同，
    // 本测试按生产真值分路径断言（既有 CognitiveAssetLifecycleTest 对此处的 BusinessException
    // 断言与其生产路径不符，见任务报告）。

    @Test
    @DisplayName("SUP-COG-02: probability 有值 + kind=UNCALIBRATED_CONFIDENCE ⇒ CognitiveGuardException 400 ECOS-COG-400P 且 0 落库")
    void uncalibratedConfidenceRejected() {
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> beliefService.register(probAndKind(0.72d, "UNCALIBRATED_CONFIDENCE")));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
        assertEquals(400, e.httpStatus);
        verifyNoBeliefRowWritten();
    }

    @Test
    @DisplayName("SUP-COG-02: probability 有值 + kind=UNCALIBRATED_MODEL_ESTIMATE ⇒ CognitiveGuardException 400 ECOS-COG-400P 且 0 落库")
    void uncalibratedModelEstimateRejected() {
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> beliefService.register(probAndKind(0.72d, "UNCALIBRATED_MODEL_ESTIMATE")));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
        assertEquals(400, e.httpStatus);
        verifyNoBeliefRowWritten();
    }

    // ── 2. 语义配套必填：kind 缺失/未登记 ⇒ 400 ──

    @Test
    @DisplayName("SUP-COG-02: probability 有值 + kind=null ⇒ 400（prob_kind 语义配套必填，0 落库）")
    void missingKindRejected() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> beliefService.register(probAndKind(0.72d, null)));
        assertEquals(400, e.getErrorCode());
        assertTrue(String.valueOf(e.getMessage()).contains("probabilityKind"),
                "拒绝原因必须点名 probabilityKind: " + e.getMessage());
        verifyNoBeliefRowWritten();
    }

    @Test
    @DisplayName("SUP-COG-02: probability 有值 + kind=未登记词表(BOGUS) ⇒ 400 fail-loud（0 落库）")
    void unregisteredKindRejected() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> beliefService.register(probAndKind(0.72d, "BOGUS_KIND")));
        assertEquals(400, e.getErrorCode());
        verifyNoBeliefRowWritten();
    }

    // ── 3. 白名单放行 + NULL 语义 ──

    @Test
    @DisplayName("SUP-COG-02: probability 有值 + kind=STATISTICAL_PROBABILITY ⇒ 放行（守卫不拦截，流程推进到 insert）")
    void statisticalProbabilityAllowed() {
        // store.maxVersion 未 stub ⇒ mock 默认 0 ⇒ 首版 version=1；
        // store.findLatest 未 stub ⇒ 返回 null，register 尾部 getLatest 安全返回 null。
        // 放行判定 = 守卫未拦截 ⇒ store.insert 恰被调用一次。
        assertDoesNotThrow(() -> beliefService.register(probAndKind(0.55d, "STATISTICAL_PROBABILITY")));
        // 可选列（tenantScope / lastEvidenceId / overrideReason）生产真值 NULL ⇒ anyString() 不匹配，
        // 用 any()；关键列精确断言。
        verify(store, times(1)).insert(any(), eq("v"), any(), eq("dm"), any(), eq(1),
                any(), anyBoolean(), any(), eq("ACTIVE"));
    }

    @Test
    @DisplayName("SUP-COG-02: probability=NULL ⇒ 放行（V262 未标定既有语义，禁补 0 校验）")
    void nullProbabilityPassesThrough() {
        assertDoesNotThrow(() -> beliefService.register(probAndKind(null, null)));
        verify(store, times(1)).insert(any(), eq("v"), any(), eq("dm"), any(), eq(1),
                any(), anyBoolean(), any(), eq("ACTIVE"));
    }

    // ── 4. 网关直测（单一裁决入口语义冻结）──

    @Test
    @DisplayName("网关直测: UNKNOWN_LEGACY 禁写入（读侧回落词，§8.3 末段禁伪造补齐）⇒ 400 ECOS-COG-400P")
    void unknownLegacyNotWritable() {
        BeliefLifecycleGateway gateway = new BeliefLifecycleGateway();
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> gateway.guard(0.4d, BeliefProbabilityKind.UNKNOWN_LEGACY));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
        assertEquals(400, e.httpStatus);
    }

    @Test
    @DisplayName("网关直测: 白名单族 + NULL 组合全放行（guard 为纯放行路径，零 IO）")
    void gatewayAllowsWhitelistedAndNullCombinations() {
        BeliefLifecycleGateway gateway = new BeliefLifecycleGateway();
        assertDoesNotThrow(() -> gateway.guard(null, null));
        // kind 仅在 probability 非空时才受约束：NULL 值 + 任意 kind 均放行
        assertDoesNotThrow(() -> gateway.guard(null, BeliefProbabilityKind.UNCALIBRATED_CONFIDENCE));
        // 词表语义单源白名单（可参与计算/审批）：统计概率 + 已校准模型概率
        assertTrue(BeliefProbabilityKind.STATISTICAL_PROBABILITY.isCalibrated());
        assertTrue(BeliefProbabilityKind.CALIBRATED_MODEL_PROBABILITY.isCalibrated());
        assertTrue(BeliefProbabilityKind.UNCALIBRATED_CONFIDENCE.isUncalibrated());
        assertTrue(BeliefProbabilityKind.UNCALIBRATED_MODEL_ESTIMATE.isUncalibrated());
        assertFalse(BeliefProbabilityKind.UNKNOWN_LEGACY.isCalibrated(),
                "UNKNOWN_LEGACY 不得进白名单（只读回落词，禁参与计算）");
    }

    // ── fixtures ──

    private void verifyNoBeliefRowWritten() {
        // 全 any 匹配（含 null 实参位）：若用 anyString()，生产真值 null 会让 never() 因
        // "实参不匹配"而假绿，起不到"0 落库"的约束作用。
        verify(store, never()).insert(any(), any(), any(), any(), any(), anyInt(),
                any(), anyBoolean(), any(), any());
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
