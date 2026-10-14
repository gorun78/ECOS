package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessCheckStatus;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.ReadinessCheckCode.Judgement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个检查项的一手结论（详细设计-10 F10-08 算法树「度量 → 阈值 → 档位」三段的合成产物；
 * V231 {@code ecos_wagent_readiness_item} 一行的内存形态）。
 *
 * <p>把三段都留在同一个对象里是为了<b>可复盘</b>：{@code measured_json}（取到什么）、
 * {@code threshold_json}（用了哪档的哪条界、来源档位）、{@code status}（判出的档）
 * 必须同源同刻落库，否则事后无法解释"为什么是 B 不是 A"。</p>
 *
 * <p>{@code missingThresholdFragments} 是判不了的第二种原因（阈值缺席），
 * 与 {@code metricAbsent}（度量缺席）分列——两者的补齐动作完全不同（一个去配阈值，一个去补数据）。</p>
 *
 * @param code      检查项
 * @param metric    度量值（可为 ABSENT）
 * @param thresholds 本档位阈值集合（可为空集合）
 * @param judgement 判档结论（永不为 null；判不了 ⇒ UNKNOWN + 原因）
 * @param missingThresholdFragments 缺席的阈值片段（判档前置校验的产物）
 * @param probeReason 该层探测结论字面（reachable=false 时点名是哪一层没回）
 */
public record CheckOutcome(ReadinessCheckCode code,
                           MetricValue metric,
                           ThresholdSet thresholds,
                           Judgement judgement,
                           List<String> missingThresholdFragments,
                           String probeReason) {

    public CheckOutcome {
        if (code == null) {
            throw new IllegalArgumentException("CheckOutcome.code 必填（无检查项即无结论）");
        }
        if (judgement == null) {
            throw new IllegalArgumentException("CheckOutcome.judgement 必填（判不了也要显式 UNKNOWN）");
        }
        metric = metric == null ? MetricValue.absent("metric-source-not-called") : metric;
        thresholds = thresholds == null ? ThresholdSet.unset(null) : thresholds;
        missingThresholdFragments = missingThresholdFragments == null
                ? List.of() : Collections.unmodifiableList(List.copyOf(missingThresholdFragments));
    }

    /** 定级器输入面（只带层/码/档三件，文本与度量进不去）。 */
    public ReadinessCheck toCheck() {
        return ReadinessCheck.of(code.layer(), code.checkCode(), judgement.status());
    }

    public ReadinessCheckStatus status() {
        return judgement.status();
    }

    /** 是否计入缺项（非 pass 即缺项，含 UNKNOWN）。 */
    public boolean missing() {
        return toCheck().missing();
    }

    /** {@code measured_json} 快照：度量形态 + 值 + 取证线索 + 判档原因。 */
    public Map<String, Object> measuredSnapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", metric.kind().name());
        if (metric.kind() == MetricValue.Kind.NUMBER) {
            m.put("value", metric.number().toPlainString());   // 铁律：数值以字符串落，禁 double 漂移
        } else if (metric.kind() == MetricValue.Kind.BOOLEAN) {
            m.put("value", metric.bool());
        }
        if (!metric.evidence().isEmpty()) {
            m.put("evidence", metric.evidence());
        }
        m.put("status", judgement.status().value());
        m.put("reason", judgement.reason());
        if (!missingThresholdFragments.isEmpty()) {
            m.put("threshold_fragments_missing", missingThresholdFragments);
        }
        if (probeReason != null && !probeReason.isBlank()) {
            m.put("probe_reason", probeReason);
        }
        return Collections.unmodifiableMap(m);
    }

    /** {@code threshold_json} 快照（阈值来源档位留痕，禁只落数值）。 */
    public Map<String, Object> thresholdSnapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profile", thresholds.profileId());
        m.put("items", thresholds.asSnapshot());
        if (!missingThresholdFragments.isEmpty()) {
            m.put("fragments_missing", missingThresholdFragments);
        }
        return Collections.unmodifiableMap(m);
    }
}
