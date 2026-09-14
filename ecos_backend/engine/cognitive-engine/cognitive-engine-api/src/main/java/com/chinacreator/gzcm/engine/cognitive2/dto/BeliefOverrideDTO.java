package com.chinacreator.gzcm.engine.cognitive2.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 不确定性判断人工覆写请求 DTO（PMO-59 P2b / ADR-9 外部方案层3.2"专家干预优先于模型更新"）。
 *
 * <p>覆写语义：覆写分布并置 {@code is_manual_override=true}，覆写后模型自动更新让位于专家意见
 * （贝叶斯补算以 manualOverride 为守卫跳过），直至下一次人工覆写入新版本。
 * 分布 prob 和=1 强校验与注册同款（Service 层容差 1e-6）。覆写落库为同变量同域下一 version，
 * 证据溯源指向触发覆写的证据（可空=纯专家判断）。</p>
 */
@Data
public class BeliefOverrideDTO {

    /** 业务域（必填，pricing / supply-chain / demand——跨域重名变量隔离键） */
    private String domain;

    /** 有限离散概率分布（必填且非空，prob 和=1 强声明由 Service 层校验）；复用 BeliefSaveDTO 同构分布点类型 */
    private List<BeliefSaveDTO.OutcomeProb> discreteDistribution = new ArrayList<>();

    /** 人工覆写理由（必填，留痕审计——专家判断的业务依据） */
    private String overrideReason;

    /** 触发覆写的证据 id（可选，引用 {@code ecos_cognitive_evidence.id}；纯专家判断可空） */
    private String lastEvidenceId;
}
