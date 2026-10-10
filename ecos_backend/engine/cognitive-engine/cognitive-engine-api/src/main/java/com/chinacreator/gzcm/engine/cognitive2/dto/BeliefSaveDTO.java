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

    // ── 《认知域与智能域补充详细设计 v1.0》§8.3 增量字段（只增不改；V268 语义配套）──

    /** 点概率（可选；V262 probability 列位，0~1）。<b>受 §16.5 红线 4 守卫</b>：
     *  probabilityKind 属 UNCALIBRATED 族时禁直入（Service 写前强校验，400 拒绝）。 */
    private Double probability;

    /** 点概率语义分类（可选；probability 非空时必填）。词表单源 = cognitive-engine-api
     *  {@code BeliefProbabilityKind}：STATISTICAL_PROBABILITY / CALIBRATED_MODEL_PROBABILITY /
     *  UNCALIBRATED_CONFIDENCE / UNCALIBRATED_MODEL_ESTIMATE（UNCALIBRATED 族写 probability 一律拒绝）。 */
    private String probabilityKind;

    /** 分布点：离散取值 + 概率（对齐 JSONB 结构 [{"outcome","prob"}]，内部类供 Jackson 序列化） */
    @Data
    public static class OutcomeProb {
        /** 离散取值（如 none / mild / aggressive），必填 */
        private String outcome;
        /** 概率 0~1，必填；分布内各项和 = 1 */
        private double prob;
    }
}
