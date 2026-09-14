package com.chinacreator.gzcm.engine.cognitive2.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 不确定性判断新增（注册）请求 DTO（PMO-59 P2a / ADR-9 心智层 P 库）。
 *
 * <p>对齐表 {@code ecos_cognitive_belief}（V129）。<b>概率和=1 约定</b>：
 * {@code discreteDistribution} 各项 {@code prob} 之和必须为 1（容差 1e-6），
 * 由 Service 层强校验（DB 为 JSONB 无 CHECK 约束，见 Phase 1 验收记录残留风险 3）。</p>
 *
 * <p>注册语义：同一 {@code variableName + domain} 若已存在（任意 version），新注册
 * 作为该变量的下一个 version 落库（{@code version = max+1}）；否则为首版 version=1。</p>
 */
@Data
public class BeliefSaveDTO {

    /** 不可观测经营变量名（必填，如 competitor_price_cut_prob） */
    private String variableName;

    /** 租户/域隔离（可选，缺省 default） */
    private String tenantScope;

    /** 业务域（必填，pricing / supply-chain / demand） */
    private String domain;

    /** 有限离散概率分布（必填且非空，prob 和=1 声明由 Service 层强校验） */
    private List<OutcomeProb> discreteDistribution = new ArrayList<>();

    /** 触发本版本的证据 id（可选，引用 {@code ecos_cognitive_evidence.id}；首版可为 null） */
    private String lastEvidenceId;

    /** 分布点：离散取值 + 概率（对齐 JSONB 结构 [{"outcome","prob"}]，内部类供 Jackson 序列化） */
    @Data
    public static class OutcomeProb {
        /** 离散取值（如 none / mild / aggressive），必填 */
        private String outcome;
        /** 概率 0~1，必填；分布内各项和 = 1 */
        private double prob;
    }
}
