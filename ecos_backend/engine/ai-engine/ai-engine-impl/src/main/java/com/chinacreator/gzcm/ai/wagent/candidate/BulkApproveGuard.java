package com.chinacreator.gzcm.ai.wagent.candidate;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.CandidateType;

import java.util.EnumSet;
import java.util.Set;

/**
 * 分册10 F10-20 · 批量审批守卫（附件 §6.1）。
 *
 * <p><b>语义/口径/知识三类禁批量审批</b>：{@link #BULK_FORBIDDEN} =
 * {@code KNOWLEDGE_PROFILE / METRIC_DEFINITION / METRIC_CALIBER / SEMANTIC_METRIC}。
 * 跨类型批（>1 distinct type）同样禁。批量审批只允许"同类型且非语义类"。</p>
 */
public final class BulkApproveGuard {

    /** 禁批量审批的语义/口径/知识类（F10-20）。 */
    public static final Set<CandidateType> BULK_FORBIDDEN =
            EnumSet.of(CandidateType.KNOWLEDGE_PROFILE, CandidateType.METRIC_DEFINITION,
                    CandidateType.METRIC_CALIBER, CandidateType.SEMANTIC_METRIC);

    private BulkApproveGuard() {}

    /**
     * 批量审批入口护栏：批内类型 &gt;1 或含语义/口径/知识类 ⇒ 抛 {@link IllegalArgumentException}。
     *
     * @param types 本批全部 Candidate 的类型并集
     */
    public static void rejectIfCrossTypeBatch(Set<CandidateType> types) {
        if (types == null || types.isEmpty()) return;
        if (types.size() > 1) {
            throw new IllegalArgumentException("E-WA-BULK: 禁止跨类型批量审批，批内=" + types);
        }
        for (CandidateType t : types) {
            if (BULK_FORBIDDEN.contains(t)) {
                throw new IllegalArgumentException("E-WA-BULK: 语义/口径/知识类禁批量审批 " + t);
            }
        }
    }
}
