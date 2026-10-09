package com.chinacreator.gzcm.engine.ai.tool;

import java.util.List;
import java.util.Set;

import com.chinacreator.gzcm.common.error.AiErrorClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 F06-12 引用块后校验验收（doc 冻结标识：{@code numberWithoutCitationBlockIsIntercepted}、
 * {@code numberAbsentFromToolResultsIsInterceptedAsHallucination}、
 * {@code regenerateCappedAtTwoThenFallbackMessage}）。
 *
 * <p>三个方法各自钉住一条独立判据，故断言要能区分：</p>
 * <ul>
 *   <li>数值有出处、只缺引用块 → 只报 {@code MISSING_CITATION_BLOCK}，{@code offenders} 必须为空
 *       （否则两条码混成一条，"缺引用"与"编造数值"在审计面上无法区分）；</li>
 *   <li>引用块齐全但报了工具结果里没有的数 → 报 {@code HALLUCINATED_NUMBER}
 *       （要点 1 的集合比对，比"有没有引用块"更强）；</li>
 *   <li>重生成上限 2 次 + 超限固定话术（要点 2，复用 {@link AiErrorClass#CITATION_MISSING}，
 *       不新增错误类；话术受 X-49 约束不得带违规数字）。</li>
 * </ul>
 *
 * <p>循环内的实际重生成次数与整段替换由 {@code CitationInducementIT} 在 AgentLoop 上验。</p>
 */
class CitationPostCheckTest {

    /** 引用元数据齐备的工具返回体（F06-11 要点 4 的形态，数值白名单唯一来源）。 */
    private static final String TOOL_BODY =
            "{\"forecastRunId\":\"run-7\",\"caliberId\":\"cal-revenue\",\"version\":\"3\","
                    + "\"asOfTime\":\"2026-10-01T00:00:00Z\","
                    + "\"evidenceLinks\":[\"http://minio.local/evidence/run-7.pdf\"],"
                    + "\"rows\":[{\"year\":2026,\"amount\":120000.00}]}";

    private static final List<String> TOOL_BODIES = List.of(TOOL_BODY);

    /** 冻结格式的引用块（逐字符由常量拼出，禁在测试里另抄一份分隔符理解）。 */
    private static final String BLOCK = ToolCitation.PREFIX + "run-7"
            + ToolCitation.SEP_CALIBER + "cal-revenue" + ToolCitation.SEP_VERSION + "3"
            + ToolCitation.SEP_ASOF + "2026-10-01T00:00:00Z"
            + ToolCitation.SEP_EVIDENCE + "http://minio.local/evidence/run-7.pdf"
            + ToolCitation.SUFFIX;

    private final CitationPostCheckService checker = new CitationPostCheckService();

    // ── 验收 1 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F06-12 要点 1：含数值却无引用块 → 拦截（MISSING_CITATION_BLOCK）")
    void numberWithoutCitationBlockIsIntercepted() {
        String answer = "2026 年预测收入为 120000 元。";   // 数值有出处，但没带引用块

        CitationPostCheckService.PostCheckResult pc = checker.check(answer, TOOL_BODIES);

        assertFalse(pc.passed(), "含经营数值且无引用块必须拦截");
        assertTrue(pc.hasViolation(CitationPostCheckService.MISSING_CITATION_BLOCK),
                "违规码必须可区分：实际 " + pc.violations());
        assertEquals(Set.of(CitationPostCheckService.MISSING_CITATION_BLOCK), pc.violations(),
                "数值都有出处时不应叠加幻觉码（两条形同一条即无法定位缺陷）");
        assertTrue(pc.offenders().isEmpty(), "违规数字集合必须为空: " + pc.offenders());

        // 对照组：同一句话补上引用块即放行 —— 证明拦截的是"缺引用"而不是"含数值"
        assertTrue(checker.check(answer + BLOCK, TOOL_BODIES).passed(),
                "引用块齐全且数值有出处必须放行（禁制造假阳性）");
    }

    // ── 验收 2 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F06-12 要点 1：引用块齐全但报了工具结果中不存在的数字 → 判幻觉数值")
    void numberAbsentFromToolResultsIsInterceptedAsHallucination() {
        String answer = "2026 年预测收入为 135000 元。" + BLOCK;   // 135000 是模型自己算出来的

        CitationPostCheckService.PostCheckResult pc = checker.check(answer, TOOL_BODIES);

        assertFalse(pc.passed(), "有引用块不等于有依据，幻觉数值同样拦截");
        assertTrue(pc.hasViolation(CitationPostCheckService.HALLUCINATED_NUMBER),
                "必须命中幻觉码: " + pc.violations());
        assertEquals(List.of("135000"), pc.offenders(), "违规数字即工具结果里不存在的那个 token");
        assertFalse(pc.hasViolation(CitationPostCheckService.MISSING_CITATION_BLOCK),
                "引用块在位时不该报缺引用（否则审计面误判）: " + pc.violations());

        // 引用块自身的数字（run-7 / @3 / 截至时间）是引用载体，不得计入违规
        assertEquals(List.of(),
                ToolCitation.numbersAbsentFrom(BLOCK, ToolCitation.numberTokensUnion(TOOL_BODIES)),
                "引用块内数字不参与集合比对（numbersAbsentFrom 先剥块）");
    }

    // ── 验收 3 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F06-12 要点 2：重生成上限恰 2 次，超限返回固定话术且不带违规数字")
    void regenerateCappedAtTwoThenFallbackMessage() {
        assertEquals(2, CitationPostCheckService.MAX_REGENERATIONS,
                "上限冻结为 2 次（PRD §4.2-2），与 PARSE_FAILURE 配额同源不叠加");
        assertTrue(checker.canRegenerate(0), "第 1 次重生成应有配额");
        assertTrue(checker.canRegenerate(1), "第 2 次重生成应有配额");
        assertFalse(checker.canRegenerate(CitationPostCheckService.MAX_REGENERATIONS),
                "用完配额必须停（禁无限重生成静默烧 token）");

        String traceId = "tr-citation-cap";
        String fallback = checker.fallbackMessage(traceId);

        assertEquals(AiErrorClass.CITATION_MISSING.renderMessage(traceId), fallback,
                "固定话术必须复用 CITATION_MISSING 模板，不新增错误类");
        assertTrue(fallback.contains("无法提供有依据的数值"), "话术逐字对齐 PRD: " + fallback);
        assertTrue(fallback.contains(traceId), "诊断码必须随话术下发（F06-14 要点 4）");
        assertEquals(AiErrorClass.CITATION_MISSING.errorCode(), checker.fallbackErrorCode());
        assertTrue(ToolCitation.numberTokens(fallback).isEmpty(),
                "X-49：话术里不得出现任何违规数字原文: " + fallback);
    }
}
