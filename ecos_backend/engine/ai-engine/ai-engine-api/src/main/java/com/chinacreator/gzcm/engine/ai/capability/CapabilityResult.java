package com.chinacreator.gzcm.engine.ai.capability;

import java.math.BigDecimal;
import java.util.List;

/**
 * 统一能力结果结构（《补充设计 v1.0》§10.5 sample 冻结字段全集）。
 *
 * <p><b>Tracing 强制字段（§10.2 末段 / §12.2 / SUP-MODEL-02）</b>：
 * {@code traceId} 与 {@code modelRef}/{@code modelVersion} 必须可查——
 * 台账 V269 同列留痕，调用方凭 traceId 回查完整调用记录。</p>
 *
 * <p><b>置信真值纪律（§8.3 / SUP-MODEL-03）</b>：{@link Quality#kind} 未声明或
 * 为模型自报类型时 {@code calibrated} 不得为 true；未校准置信度禁冒充概率，
 * 权威业务裁决须另行字段/内容验证（§10.6 末条）。</p>
 */
public class CapabilityResult {

    /** 质量子结构（§10.5 quality 对象）。 */
    public record Quality(BigDecimal score, String kind, Boolean calibrated) {
        public Quality {
            if (kind == null || kind.isBlank()) {
                throw new IllegalArgumentException("quality.kind 缺失（§8.3 禁缺省放行：置信类型必标注）");
            }
            if (calibrated != null && calibrated) {
                // 只有声明了校准门禁的类型才允许 calibrated=true（§8.3-2）
                if (!KIND_CALIBRATED.equals(kind)) {
                    throw new IllegalArgumentException(
                            "calibrated=true 仅允许 kind=" + KIND_CALIBRATED + "，实际 kind=" + kind
                                    + "（未校准置信度禁冒充概率，SUP-MODEL-03）");
                }
            }
        }

        /** 唯一允许声明 calibrated=true 的 kind 字面量（§8.3-2 校准门禁产物）。 */
        public static final String KIND_CALIBRATED = "CALIBRATED_MODEL_PROBABILITY";

        /** 模型自报置信（LLM 自报"90% 确信"一律落此，§7.2 置信规则）。 */
        public static final String KIND_MODEL_ESTIMATE = "MODEL_ESTIMATE";

        /** 未校准模型置信显式形态（§8.3 第 1 类反面用例）。 */
        public static final String KIND_UNCALIBRATED_CONFIDENCE = "UNCALIBRATED_CONFIDENCE";

        /** 可复算统计模型概率（§8.3-1）。 */
        public static final String KIND_STATISTICAL_PROBABILITY = "STATISTICAL_PROBABILITY";
    }

    private String capabilityId;
    private CapabilityState status;
    private String inputRef;
    private String modelRef;
    private String modelVersion;
    private String outputRef;
    private Quality quality;
    private List<String> warnings;
    private String traceId;
    private long latencyMs;
    /** 计量口径（§10.6：TOKEN/CALL/INPUT_SCALE…；台账 cost_unit 同值）。 */
    private String costUnit;
    /** 计量读数（Token 数/调用次数/输入规模；与 costUnit 同真值）。 */
    private Long costValue;
    /** 降级标志（ledger degraded 列同步；DEGRADED 状态必须置位）。 */
    private boolean degraded;
    /** 路由决策留痕（§10.3：请求能力/候选/选中/未选原因/预算/策略引用摘要）。 */
    private String selectedModelReason;

    public String getCapabilityId() {
        return capabilityId;
    }

    public void setCapabilityId(String capabilityId) {
        this.capabilityId = capabilityId;
    }

    public CapabilityState getStatus() {
        return status;
    }

    public void setStatus(CapabilityState status) {
        this.status = status;
    }

    public String getInputRef() {
        return inputRef;
    }

    public void setInputRef(String inputRef) {
        this.inputRef = inputRef;
    }

    public String getModelRef() {
        return modelRef;
    }

    public void setModelRef(String modelRef) {
        this.modelRef = modelRef;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getOutputRef() {
        return outputRef;
    }

    public void setOutputRef(String outputRef) {
        this.outputRef = outputRef;
    }

    public Quality getQuality() {
        return quality;
    }

    public void setQuality(Quality quality) {
        this.quality = quality;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getCostUnit() {
        return costUnit;
    }

    public void setCostUnit(String costUnit) {
        this.costUnit = costUnit;
    }

    public Long getCostValue() {
        return costValue;
    }

    public void setCostValue(Long costValue) {
        this.costValue = costValue;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    public String getSelectedModelReason() {
        return selectedModelReason;
    }

    public void setSelectedModelReason(String selectedModelReason) {
        this.selectedModelReason = selectedModelReason;
    }
}
