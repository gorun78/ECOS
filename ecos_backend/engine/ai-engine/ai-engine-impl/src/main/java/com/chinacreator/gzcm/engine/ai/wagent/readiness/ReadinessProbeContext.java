package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessLayer;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.EngineProbeClient.LayerProbe;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 一次 Readiness 评估的上下文（详细设计-10 F10-08 算法树的输入面：谁在问、挂哪个目标、
 * 用哪个档位、四层探到了什么）。
 *
 * <p>{@link ReadinessMetricSource} 的取数入参。本对象<b>只带标识与探测结论，不带任何度量值</b>——
 * 度量必须由取数面现取，禁上游"顺手填好"塞进来（那等于把造数的责任从取数端转移到调用端）。</p>
 *
 * <p>{@code probes} 恒为 DIKC 四键（{@link EngineProbeClient#probeAll()} 的产物）；
 * 某层 {@code reachable=false} 时，该层的度量源应当如实返回
 * {@link MetricValue#absent(String)} 而不是猜一个值。</p>
 *
 * @param questionId 问题 ID（V230 {@code question_id}，评估挂问题）
 * @param missionId  任务 ID（问题所属 Mission，用于反查 Goal）
 * @param goalId     目标 ID（{@code onto.caliber_defined} 等等本域度量的取证锚点）
 * @param runId      产生本次评估的 Run（可为 null：手工/预评场景；refill 轮次台账要求非空）
 * @param profileId  阈值档位（{@code default} / {@code forecast}，单源 {@link ThresholdPolicy}）
 * @param intent     问题意图（档位推导的输入，{@link ThresholdPolicy#profileForIntent}）
 * @param tenantId   租户（多租户闸，R-36）
 * @param orgId      组织单元
 * @param probes     四层探测结论
 */
public record ReadinessProbeContext(String questionId, String missionId, String goalId, String runId,
                                    String profileId, String intent, String tenantId, String orgId,
                                    Map<ReadinessLayer, LayerProbe> probes) {

    public ReadinessProbeContext {
        Map<ReadinessLayer, LayerProbe> copy = new EnumMap<>(ReadinessLayer.class);
        if (probes != null) {
            probes.forEach((k, v) -> {
                if (k != null && v != null) {
                    copy.put(k, v);
                }
            });
        }
        probes = Collections.unmodifiableMap(copy);
    }

    /** 该层探测结论（无键 ⇒ null，调用方按"未探"处理）。 */
    public LayerProbe probe(ReadinessLayer layer) {
        return probes.get(layer);
    }

    /** 该层是否给出"引擎在位且健康"的肯定结论（缺键即 false，禁默认放行）。 */
    public boolean layerReachable(ReadinessLayer layer) {
        LayerProbe probe = probes.get(layer);
        return probe != null && probe.reachable();
    }

    /** 同一上下文换 Run（refill 复评时评估新行要挂触发它的那次 refill 所属 Run）。 */
    public ReadinessProbeContext withRunId(String newRunId) {
        return new ReadinessProbeContext(questionId, missionId, goalId, newRunId,
                profileId, intent, tenantId, orgId, probes);
    }

    /** 同一上下文换探测结果（refill 复评需重新探测，不复用旧结论）。 */
    public ReadinessProbeContext withProbes(Map<ReadinessLayer, LayerProbe> newProbes) {
        return new ReadinessProbeContext(questionId, missionId, goalId, runId,
                profileId, intent, tenantId, orgId, newProbes);
    }
}
