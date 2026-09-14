package com.chinacreator.gzcm.engine.cognitive2.service.mental;

import com.chinacreator.gzcm.common.cognitive.HypothesisVO;
import com.chinacreator.gzcm.common.cognitive.EvidenceRecordVO;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveHypothesisService;
import com.chinacreator.gzcm.engine.cognitive2.service.EvidenceStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 假设失效检测器（PMO-59 P2b / ADR-9 外部方案层3.1 核心能力）。
 *
 * <p><b>职责边界（本检测器只"判"，不"发"）</b>：负责确定性命中判定 + 触发失效状态写，
 * 但不直接发事件/告警——失效后的 {@code ecos.cognitive} 失效事件 + runtime-monitor 告警
 * 统一由 {@link CognitiveHypothesisService#invalidate(String, String, boolean)} 单点发布，
 * 保证自动检测链路与人工兜底链路（{@code {id}/invalidate} 端点）"事件/告警口径一致、不重复"。</p>
 *
 * <p>检测规则（确定性、可审计）——新证据对一条 VALID 假设命中任一即触发失效：
 <ol>
   <li><b>refuting 命中</b>：证据 {@code refuting_evidence_ids} 包含假设的支撑证据 id（证据直接推翻假设的支撑链）；</li>
   <li><b>冲突状态命中</b>：证据 {@code is_conflict=true} 且同 metric（假设 {@code metric_ref} = 证据 blob.metric）
       且证据可信度 ≥ 假设支撑证据最高可信度（高可信冲突证据取代低可信支撑）；</li>
   <li><b>同 metric 数值漂移</b>：冲突证据同 metric 且与假设支撑证据 value 偏差超过阈值
       {@link #VALUE_DRIFT_THRESHOLD}（相对偏差 |new-old|/max(|old|,1)，假设隐含的稳定域被越界）。</li>
 </ol>
 命中 → 经 {@code invalidate(id, reason, true)} 落库 INVALIDATED（invalid_at/invalid_reason 留痕），
 * 由 service 统一发失效事件 + 告警（正文含 faultContext 结构化字段，预留周一故障复盘对接）。</p>
 *
 * <p>触发点（两条）：① 新证据登记即时检测（CognitiveEvidenceService 登记后调用）；
 * ② runtime-task 定时补算轮询（MentalScanTask 周期扫描未处理证据兜底漏报）。</p>
 */
@Component
@RequiredArgsConstructor
public class MentalInvalidationDetector {

    private static final Logger log = LoggerFactory.getLogger(MentalInvalidationDetector.class);

    /** value 漂移阈值：同 metric |new - old| / max(|old|, 1) > 0.15 视为越界（业务可调 Phase 3） */
    public static final double VALUE_DRIFT_THRESHOLD = 0.15d;

    private final CognitiveHypothesisService hypothesisService;
    private final EvidenceStore evidenceStore;

    /**
     * 用给定新证据检验全部有效假设，命中即触发失效（经 service.invalidate 单点发布事件/告警）。
     *
     * <p>铁律合规：失效状态写 + 事件/告警统一收口到 {@code CognitiveHypothesisService.invalidate}，
     * 本类不重复发事件/告警，避免自动链路与人工链路口径漂移。</p>
     *
     * @param evidence 触发检验的新证据（必填）
     * @return 本次被失效的假设 VO 列表（0 命中为空列表）
     */
    public List<HypothesisVO> detectAndInvalidate(EvidenceRecordVO evidence) {
        List<HypothesisVO> invalidated = new ArrayList<>();
        if (evidence == null || evidence.getId() == null) {
            return invalidated;
        }
        Map<String, Object> evidenceBlob = evidenceStore.parseJsonObjectText(evidence.getBlob());
        String metric = evidenceStore.fieldString(evidenceBlob, "metric");
        String value = evidenceStore.fieldString(evidenceBlob, "value");
        List<String> refuting = evidenceStore.parseJsonListText(evidence.getRefutingEvidenceIds());

        for (HypothesisVO hypothesis : hypothesisService.list(null, "VALID")) {
            InvalidationHit hit = evaluate(hypothesis, evidence, metric, value, refuting);
            if (hit != null) {
                try {
                    // 统一经 service.invalidate 单点发布: 事件 + 告警 + 审计 (autoDetected=true)
                    HypothesisVO after = hypothesisService.invalidate(hypothesis.getId(), hit.reason(), true);
                    invalidated.add(after);
                    log.info("假设失效检测命中: {} 规则={} 触发证据={}",
                            after.getHypothesisCode(), hit.rule(), evidence.getId());
                } catch (Exception e) {
                    log.warn("假设失效处理异常 (不阻塞其他假设计检): hypothesis={} err={}",
                            hypothesis.getId(), e.getMessage());
                }
            }
        }
        return invalidated;
    }

    /** 单假设规则判定；返回命中信息（null=未命中）。 */
    InvalidationHit evaluate(HypothesisVO hypothesis, EvidenceRecordVO evidence,
                             String metric, String value, List<String> refuting) {
        // 规则①: refuting 直接包含假设支撑证据 id
        for (String supportId : hypothesis.getEvidenceIds()) {
            if (refuting.contains(supportId)) {
                return new InvalidationHit("REFUTING_HIT",
                    "新证据 " + evidence.getId() + " 直接推翻支撑证据 " + supportId);
            }
        }
        if (evidence.isConflict() && metric != null && metric.equals(hypothesis.getMetricRef())) {
            // 规则②: 冲突证据同 metric 且可信度 ≥ 支撑证据最高可信度
            double supportMaxConfidence = maxSupportConfidence(hypothesis);
            if (evidence.getConfidence() != null && evidence.getConfidence() >= supportMaxConfidence) {
                return new InvalidationHit("CONFLICT_EVIDENCE",
                    "高可信冲突证据 " + evidence.getId() + "（conf=" + evidence.getConfidence()
                        + "）与支撑证据同 metric=" + metric + " 冲突");
            }
            // 规则③: 同 metric 数值漂移越界
            if (value != null) {
                for (String supportId : hypothesis.getEvidenceIds()) {
                    Double drift = driftVsSupport(hypothesis, supportId, value);
                    if (drift != null) {
                        return new InvalidationHit("VALUE_DRIFT",
                            "同 metric=" + metric + " 数值漂移越界 " + drift
                                + "（阈值 " + VALUE_DRIFT_THRESHOLD + "），触发证据=" + evidence.getId());
                    }
                }
            }
        }
        return null;
    }

    /**
     * 与某支撑证据同 metric value 的相对偏差（|new-old|/max(|old|,1)）超阈值才返回，否则 null。
     */
    private Double driftVsSupport(HypothesisVO hypothesis, String supportId, String newValue) {
        Map<String, Object> row = evidenceStore.findById(supportId);
        if (row == null) {
            return null;
        }
        Map<String, Object> blob = evidenceStore.parseJsonObjectText(evidenceStore.fieldString(row, "blob"));
        String oldValue = evidenceStore.fieldString(blob, "value");
        if (oldValue == null) {
            return null;
        }
        double old = parseDouble(oldValue);
        double neu = parseDouble(newValue);
        if (Double.isNaN(old) || Double.isNaN(neu)) {
            return null;
        }
        double drift = Math.abs(neu - old) / Math.max(Math.abs(old), 1d);
        return drift > VALUE_DRIFT_THRESHOLD ? drift : null;
    }

    private double maxSupportConfidence(HypothesisVO hypothesis) {
        double max = 0d;
        for (String supportId : hypothesis.getEvidenceIds()) {
            Map<String, Object> row = evidenceStore.findById(supportId);
            if (row != null && row.get("confidence") instanceof Number n) {
                max = Math.max(max, n.doubleValue());
            }
        }
        return max;
    }

    private static double parseDouble(String v) {
        try {
            return Double.parseDouble(v.trim());
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    /** 命中信息：规则代码 + 留痕原因 */
    public record InvalidationHit(String rule, String reason) {
    }
}
