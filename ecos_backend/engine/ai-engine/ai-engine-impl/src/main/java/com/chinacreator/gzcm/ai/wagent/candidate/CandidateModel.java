package com.chinacreator.gzcm.ai.wagent.candidate;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 分册10 F10-20/F10-23 · W Agent Candidate 12 字段定稿模型（附件 §6.1 / §6.2 V238）。
 *
 * <p>五个 {@code v_*} 校验列（V238）为**结构化枚举形态**（pass/fail/warn 语义），
 * 禁裸存 LLM 自由文本；本 record 的紧凑构造器强制五列 {@code non-null}
 * （结构化缺失即拒收，与 agent "Agent 必留痕" 同源）。金额/置信率一律 {@link BigDecimal}
 * （铁律：无 double/float 进入签名）。</p>
 */
public record CandidateModel(
        String candidateId,
        WAgentEnums.CandidateType type,
        String targetEngine,
        String targetObjectRefJson,
        String payloadJson,
        String payloadHash,
        String generatedByRun,
        String generatedByStep,
        BigDecimal confidence,
        String basisJson,
        // 结构 V238 五校验列（pass/fail/warn）。
        String vStructure,
        String vReference,
        String vConflict,
        String vImpact,
        String vRegression,
        WAgentEnums.CandidateStatus status,
        String batchId,
        String engineDraftRef,
        String publishedRef,
        String publishedGitRef,
        Instant expireTime) {

    /** V238 五校验列 non-null 强校（缺列 = 无结构化校验留痕，拒收）。 */
    public CandidateModel {
        requireNonBlank(vStructure,  "vStructure");
        requireNonBlank(vReference,  "vReference");
        requireNonBlank(vConflict,   "vConflict");
        requireNonBlank(vImpact,     "vImpact");
        requireNonBlank(vRegression, "vRegression");
    }

    /**
     * 类型语义工厂：入口防御，确保类型非空（Candidate 类型单源 {@link WAgentEnums.CandidateType}）。
     * 返回一个最小 DRAFT 骨架，供调用方链式补全 payload。
     */
    public static CandidateModel requireType(String candidateId, WAgentEnums.CandidateType t) {
        if (t == null) {
            throw new IllegalArgumentException("E-WA-CAND: candidateType 不可为空");
        }
        return new CandidateModel(
                candidateId, t, null, null, null, null,
                null, null, null, null,
                "warn", "warn", "warn", "warn", "warn",
                WAgentEnums.CandidateStatus.DRAFT, null, null, null, null, null);
    }

    private static void requireNonBlank(String v, String name) {
        if (v == null) {
            throw new IllegalArgumentException("E-WA-CAND: 结构校验列 " + name + " 不可为空（V238）");
        }
    }
}
