package com.chinacreator.gzcm.engine.ai.capability;

import com.chinacreator.gzcm.engine.ai.capability.CapabilityResult.Quality;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.runtime.llm.config.LLMGatewayProperties;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SUP-MODEL-03 — 置信真值纪律（§8.3 / §10.5 quality.kind 规则）冻结测试
 * （《ECOS 认知域与智能域补充详细设计 v1.0》§19.5 测试登记；设计稿原名 CapabilityTaskServiceTest）。
 *
 * <p>校准判定在生产中落在两处（本测试按实际 seam 定位，非设计稿名）：</p>
 * <ol>
 *   <li><b>结果契约门禁</b>：{@link Quality} record 紧凑构造器——calibrated=true 仅允许
 *       kind = {@link Quality#KIND_CALIBRATED}（校准门禁产物）；kind = MODEL_ESTIMATE 或
 *       UNCALIBRATED_CONFIDENCE 等模型自报类型带 calibrated=true 构造即抛 IAE
 *       （未校准置信度禁冒充统计概率，§8.3/§10.5）；</li>
 *   <li><b>执行/台账默认值</b>：{@link ModelCapabilityExecutorImpl} 成功路径恒写
 *       {@code new Quality(null, KIND_MODEL_ESTIMATE, FALSE)}，台账 calibrated 列只可落 0
 *       （{@code Boolean.TRUE.equals(...) ? 1 : 0}），禁留 1。</li>
 * </ol>
 *
 * <p>§10.5 值语义：未校准 conf 值不承载"概率"语义——kind 决定解释口径，score 字段本身
 * 不被透传为 calibrated=false 即"概率"（透传概率 = 冒充可复算统计量）。</p>
 */
@ExtendWith(MockitoExtension.class)
class CapabilityConfidenceCalibrationTest {

    @Mock
    private CapabilityLedgerDao ledgerDao;
    @Mock
    private ObjectProvider<LLMGateway> llmGatewayProvider;
    @Mock
    private ObjectProvider<LLMGatewayProperties> llmPropsProvider;
    @Mock
    private ObjectProvider<ITaskManagementService> taskManagementProvider;
    @Mock
    private ObjectProvider<EventBusService> eventBusProvider;
    @Mock
    private LLMGateway llmGateway;

    private ModelCapabilityExecutorImpl newExecutor() {
        return new ModelCapabilityExecutorImpl(ledgerDao, llmGatewayProvider, llmPropsProvider,
                taskManagementProvider, eventBusProvider);
    }

    private CapabilityRequest llmRequest() {
        CapabilityRequest req = new CapabilityRequest();
        req.setCapabilityId(AiCapabilityId.LLM_TEXT_REASONING);
        req.setInputRef("ref-cal-1");
        req.setModelRef("m-1");
        req.setModelVersion("v1");
        req.setTraceId("trace-cal-1");
        return req;
    }

    // ── §8.3-2 契约门禁: calibrated=true 的准入门槛 ─────────────────

    @Test
    @DisplayName("SUP-MODEL-03: quality.kind = MODEL_ESTIMATE / UNCALIBRATED_CONFIDENCE 时 calibrated 恒不得为 true")
    void modelEstimateKindsRefuseCalibratedTrue() {
        assertThrows(IllegalArgumentException.class,
                () -> new Quality(new BigDecimal("0.90"), Quality.KIND_MODEL_ESTIMATE, Boolean.TRUE),
                "模型自报置信禁声明 calibrated=true (未校准置信度禁冒充概率)");
        assertThrows(IllegalArgumentException.class,
                () -> new Quality(new BigDecimal("0.85"),
                        Quality.KIND_UNCALIBRATED_CONFIDENCE, Boolean.TRUE),
                "显式未校准形态 calibrated=true 必须被构造器拒绝");
        // 同类型下 calibrated=false / null 放行 (未校准是合法态, 只是不得声称已校准)
        Quality est = new Quality(new BigDecimal("0.90"), Quality.KIND_MODEL_ESTIMATE, Boolean.FALSE);
        assertFalse(Boolean.TRUE.equals(est.calibrated()), "MODEL_ESTIMATE 强制 calibrated=false");
        Quality unk = new Quality(null, Quality.KIND_UNCALIBRATED_CONFIDENCE, null);
        assertFalse(Boolean.TRUE.equals(unk.calibrated()), "calibrated 缺省视同未校准");
    }

    @Test
    @DisplayName("SUP-MODEL-03: calibrated=true 唯一合法前提是 kind = CALIBRATED_MODEL_PROBABILITY (校准门禁产物)")
    void calibratedTrueOnlyUnderCalibratedKind() {
        Quality ok = new Quality(new BigDecimal("0.97"), Quality.KIND_CALIBRATED, Boolean.TRUE);
        assertTrue(Boolean.TRUE.equals(ok.calibrated()), "CALIBRATED_MODEL_PROBABILITY 放行 calibrated=true");
        // 防词表漂移: 门禁字面量的任何变体一律拒绝 (大小写/截断/空)
        String[] nonGateKinds = {"CALIBRATED", "calibrated_model_probability", "CALIBRATED_MODEL", ""};
        for (String kind : nonGateKinds) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Quality(new BigDecimal("0.5"), kind == null ? null : kind.toLowerCase(), Boolean.TRUE),
                    "kind 变体不匹配唯一门禁字面量必须拒绝: " + kind);
        }
        // kind 缺失禁缺省放行 (§8.3 置信类型必标注)
        assertThrows(IllegalArgumentException.class,
                () -> new Quality(new BigDecimal("0.5"), null, null),
                "kind 缺失必须抛 IAE (禁缺省放行)");
        assertThrows(IllegalArgumentException.class,
                () -> new Quality(new BigDecimal("0.5"), "  ", null),
                "kind 空白串视同缺失必须抛 IAE");
    }

    // ── 执行 seam: 成功路径默认 MODEL_ESTIMATE + calibrated=false ──

    @Test
    @DisplayName("SUP-MODEL-03: LLM 成功结果质量默认 MODEL_ESTIMATE 且 calibrated 恒 false (非透传概率)")
    void llmSuccessQualityDefaultsToUncalibratedModelEstimate() {
        ModelCapabilityExecutorImpl executor = newExecutor();
        when(llmGatewayProvider.getIfAvailable()).thenReturn(llmGateway);
        when(llmGateway.call(any(ChatRequest.class)))
                .thenReturn(ChatResponse.ok("ok", 1, 1, "m-1"));

        CapabilityResult r = executor.execute(llmRequest());

        assertEquals(CapabilityState.SUCCEEDED, r.getStatus());
        assertNotNull(r.getQuality());
        assertEquals(Quality.KIND_MODEL_ESTIMATE, r.getQuality().kind(),
                "模型自报输出 kind 只能落 MODEL_ESTIMATE, 禁透传为概率语义");
        assertFalse(Boolean.TRUE.equals(r.getQuality().calibrated()), "未过校准门禁 calibrated 恒 false");
    }

    @Test
    @DisplayName("SUP-MODEL-03: 台账 calibrated 列同步执行结果——MODEL_ESTIMATE 恒落 0, 禁留 1")
    void ledgerCalibratedColumnNeverOneForModelEstimate() {
        ModelCapabilityExecutorImpl executor = newExecutor();
        when(llmGatewayProvider.getIfAvailable()).thenReturn(llmGateway);
        when(llmGateway.call(any(ChatRequest.class)))
                .thenReturn(ChatResponse.ok("ok", 1, 1, "m-1"));

        executor.execute(llmRequest());

        ArgumentCaptor<CapabilityTaskDO> cap = ArgumentCaptor.forClass(CapabilityTaskDO.class);
        verify(ledgerDao).insertTask(cap.capture());
        CapabilityTaskDO t = cap.getValue();
        assertEquals(Quality.KIND_MODEL_ESTIMATE, t.getQualityKind(),
                "台账 quality_kind 与响应同值 MODEL_ESTIMATE");
        assertEquals(Integer.valueOf(0), t.getCalibrated(),
                "V269 calibrated 列: MODEL_ESTIMATE 恒 0, 未校准禁置 1");
        assertNull(t.getQualityScore(), "未实测置信分时 score 落 NULL, 禁默认 0 冒充零分");
    }

    @Test
    @DisplayName("SUP-MODEL-03: 未接模型/失败路径不谈置信度——calibrated 列强制 0, 缺 quality 时 kind 兜底 MODEL_ESTIMATE")
    void failurePathLedgerCalibratedForcedToZero() {
        ModelCapabilityExecutorImpl executor = newExecutor();
        // 不装配 llm-gateway => LLM_TEXT_REASONING 走未接入 FAILED 分支 (Quality 缺省 null)
        CapabilityResult r = executor.execute(llmRequest());

        assertEquals(CapabilityState.FAILED, r.getStatus());
        assertNull(r.getQuality(), "失败/未接入路径不得凭空带出 quality");
        ArgumentCaptor<CapabilityTaskDO> cap = ArgumentCaptor.forClass(CapabilityTaskDO.class);
        verify(ledgerDao).insertTask(cap.capture());
        CapabilityTaskDO t = cap.getValue();
        assertEquals(Quality.KIND_MODEL_ESTIMATE, t.getQualityKind(), "缺 quality 时 kind 兜底 MODEL_ESTIMATE");
        assertEquals(Integer.valueOf(0), t.getCalibrated(), "失败路径 calibrated 强制 0, 禁留 1");
    }
}
