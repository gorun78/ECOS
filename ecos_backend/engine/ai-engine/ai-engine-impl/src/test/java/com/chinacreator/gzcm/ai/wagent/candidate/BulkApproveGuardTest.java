package com.chinacreator.gzcm.ai.wagent.candidate;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.CandidateType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-20 批量审批守卫离线单测：语义/口径/知识三类禁批量审批，
 * 跨类型批（&gt;1 distinct type）同样禁。
 */
class BulkApproveGuardTest {

    /** F10-20：批内含 2 个 BULK_FORBIDDEN 语义类（SEMANTIC_METRIC + METRIC_DEFINITION）⇒ 抛 IAE。 */
    @Test
    void semanticMetricKnowledgeCandidatesRejectBatchApprove() {
        // 跨类型批（2 个 distinct 禁批语义类）。
        Set<CandidateType> mixed = Set.of(
                CandidateType.SEMANTIC_METRIC, CandidateType.METRIC_DEFINITION);
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> BulkApproveGuard.rejectIfCrossTypeBatch(mixed));
        assertTrue(ex.getMessage().contains("E-WA-BULK"),
                "跨类型禁批必须带 E-WA-BULK 短码，实际：" + ex.getMessage());

        // 同类但单挑一个禁批语义类（批内 1 个 distinct，但在 BULK_FORBIDDEN 内）⇒ 同样禁。
        Exception ex2 = assertThrows(IllegalArgumentException.class,
                () -> BulkApproveGuard.rejectIfCrossTypeBatch(
                        Set.of(CandidateType.SEMANTIC_METRIC)));
        assertTrue(ex2.getMessage().contains("E-WA-BULK"),
                "单一禁批语义类不可批量审批，实际：" + ex2.getMessage());
    }

    /** F10-20：单一非语义类（如 RULE）⇒ 放行（对照基线）。 */
    @Test
    void nonSemanticSingleTypeBatchAllowed() {
        assertDoesNotThrow(
                () -> BulkApproveGuard.rejectIfCrossTypeBatch(Set.of(CandidateType.RULE)),
                "单 RULE 类型批量审批应放行");
        assertDoesNotThrow(
                () -> BulkApproveGuard.rejectIfCrossTypeBatch(Set.of()),
                "空批应放行（无审批对象）");
        assertDoesNotThrow(
                () -> BulkApproveGuard.rejectIfCrossTypeBatch(null),
                "null 批应放行（无审批对象）");
    }
}
