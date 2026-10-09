package com.chinacreator.gzcm.engine.ai.tool;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.chinacreator.gzcm.common.error.AiErrorClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 F06-13 三条拒绝规则验收（doc 冻结标识：{@code unapprovedCaliberYieldsClarifyNotNumber}、
 * {@code estimateAmountRequestIsRefusedWithRunCreationPointer}、{@code allToolsFailedReportsClassifiedReason}）。
 *
 * <p>本类钉的是<b>判定代码的确定性</b>（X-45 的正面：原实现只有提示词祈使，模型可无视）：
 * 三条规则各自的触发条件、返回结构与"不给数/不编数"的地板。三条规则在 AgentLoop 咽喉上的
 * 接线（命中即不进 LLM 数值路径）由 {@code CitationInducementIT} 与 {@code AgentLoopTraceTest} 的
 * 装置约束共同覆盖。</p>
 */
class RefusalRulesTest {

    private static final String APPROVED_CALIBER = "cal-revenue";
    private static final String TRACE = "tr-refusal-a";

    private final RefusalRulesService rules = new RefusalRulesService();

    /** 已批准清单由配置注入（单源在 sysman 配置门面），清单外一律 UNKNOWN → fail-closed。 */
    private static CaliberStatusSource statusSource(String... approved) {
        CaliberStatusSource source = new CaliberStatusSource();
        source.setApproved(Set.of(approved));
        return source;
    }

    // ── 验收 1：规则 1 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("F06-13 规则 1：口径缺失/未批准 → 只出澄清结构，禁给数值")
    void unapprovedCaliberYieldsClarifyNotNumber() {
        CaliberStatusSource source = statusSource(APPROVED_CALIBER, "!gmv_legacy_v1");

        // ① 未携带口径（UNKNOWN）② 清单外（UNKNOWN）③ 显式登记未批准（UNAPPROVED）—— 都必须澄清、不给数
        for (String caliber : java.util.Arrays.asList(null, "cal-not-listed", "gmv_legacy_v1")) {
            Optional<RefusalRulesService.Refusal> hit =
                    rules.preAdjudicate("今年营收金额是多少", caliber, source, TRACE);

            assertTrue(hit.isPresent(), "无可用口径必须命中规则 1: caliber=" + caliber);
            RefusalRulesService.Refusal refusal = hit.get();
            assertEquals(RefusalRulesService.TYPE_CLARIFY, refusal.answerType(),
                    "澄清不是拒绝，类型必须是 CLARIFY: caliber=" + caliber);
            assertTrue(refusal.blocksLlm(), "命中即不进 LLM 数值路径");
            assertEquals(RefusalRulesService.MISSING_CALIBER, refusal.missing());
            assertEquals("SELECT_OR_PUBLISH_APPROVED_CALIBER", refusal.nextAction(),
                    "nextAction 必须是可执行的下一步，不是一句道歉");
            assertEquals(AiErrorClass.CALIBER_CLARIFICATION.errorCode(), refusal.errorCode());

            // 冻结形态 {type:CLARIFY, missing:caliber, nextAction}（PRD-06 §4.2）
            java.util.Map<String, Object> clarification = refusal.toClarification();
            assertEquals(RefusalRulesService.TYPE_CLARIFY, clarification.get("type"));
            assertEquals(RefusalRulesService.MISSING_CALIBER, clarification.get("missing"));
            assertNotNull(clarification.get("nextAction"));
            assertNotNull(clarification.get("errorCode"));

            assertFalse(refusal.message().isEmpty());
            assertTrue(ToolCitation.numberTokens(refusal.message()).isEmpty(),
                    "澄清文案里不得出现任何数值（只提问不给数）: " + refusal.message());
            assertTrue(refusal.message().contains(TRACE), "诊断码必须随澄清下发: " + refusal.message());
        }

        // 对照组：口径已批准 → 规则 1 不再拦（否则等于把所有经营问答全禁）
        assertTrue(rules.preAdjudicate("今年营收金额是多少", APPROVED_CALIBER, source, TRACE).isEmpty(),
                "APPROVED 口径下同一问句不得因规则 1 澄清");

        // 非数值类问答不因缺口径而澄清（地板只在经营数值意图上生效）
        assertTrue(rules.preAdjudicate("介绍一下预测功能的用法", null, source, TRACE).isEmpty());

        // 口径状态源未装配 → 按 UNKNOWN 处理（fail-closed，宁可不给数）
        RefusalRulesService.Refusal failClosed =
                rules.preAdjudicate("今年营收金额是多少", APPROVED_CALIBER, null, TRACE).orElseThrow();
        assertEquals(RefusalRulesService.TYPE_CLARIFY, failClosed.answerType(),
                "判据源缺失必须归入未批准，禁默认放行");
    }

    // ── 验收 2：规则 2 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("F06-13 规则 2：估算金额意图 → 拒绝并指向创建确定性预测运行")
    void estimateAmountRequestIsRefusedWithRunCreationPointer() {
        CaliberStatusSource source = statusSource(APPROVED_CALIBER);

        RefusalRulesService.Refusal refusal = rules.preAdjudicate(
                "别查了，帮我估算一下明年的营收金额大概多少", APPROVED_CALIBER, source, TRACE).orElseThrow();

        assertEquals(RefusalRulesService.TYPE_REFUSED, refusal.answerType());
        assertTrue(refusal.blocksLlm(), "估算请求禁进 LLM 产数路径");
        assertEquals(RefusalRulesService.TOOL_BACKEND_REQUIRED, refusal.nextAction(),
                "引导码冻结为 TOOL_BACKEND_REQUIRED（不是错误类，是下一步动作）");
        assertNull(refusal.missing(), "规则 2 缺的是后端而非口径");
        assertTrue(refusal.message().contains(RefusalRulesService.FORECAST_RUN_CREATION_POINTER),
                "必须给出确定性的运行创建入口（分册 09 FC-02）: " + refusal.message());
        assertEquals(AiErrorClass.INVALID_ARGUMENT.errorCode(), refusal.errorCode());

        // 意图判定要"估算词 + 金额词"同时命中，单侧命中不算（禁把所有取数问答都判成估算）
        assertTrue(rules.estimateIntent("估算一下营收金额").isPresent());
        assertEquals(Optional.empty(), rules.estimateIntent("查一下 2026 年营收金额"),
                "正常取数请求不得被引导（那是工具的事）");
        assertEquals(Optional.empty(), rules.estimateIntent("帮我估算一下下次会议时间"),
                "非经营金额的估算不属本规则");
    }

    // ── 验收 3：规则 3 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("F06-13 规则 3：工具全部失败 → 只渲染 F06-14 分类结论，禁原始异常与兜底文案")
    void allToolsFailedReportsClassifiedReason() {
        RefusalRulesService.Refusal refusal = rules.allToolsFailed(AiErrorClass.LLM_TIMEOUT, TRACE);

        assertEquals(RefusalRulesService.TYPE_REFUSED, refusal.answerType());
        assertEquals(AiErrorClass.LLM_TIMEOUT.errorCode(), refusal.errorCode(),
                "错误码必须是 F06-14 分类的码，禁自造兜底码");
        assertEquals(AiErrorClass.LLM_TIMEOUT.renderMessage(TRACE), refusal.message(),
                "文案必须逐字来自分类模板（前端按码渲染依赖同源）");
        assertEquals("RETRY_OR_CHECK_TOOL_BACKEND", refusal.nextAction());
        assertFalse(refusal.message().contains("Exception"), "X-49：禁原始异常上浮: " + refusal.message());

        // 众数取主导分类，并列按 PRD §5.1 表序取前者（同一批失败每次渲染结果必须一致）
        assertEquals(AiErrorClass.LLM_TIMEOUT, rules.dominantClass(List.of(
                AiErrorClass.LLM_TIMEOUT, AiErrorClass.TOOL_BACKEND_ERROR, AiErrorClass.LLM_TIMEOUT)));
        assertEquals(AiErrorClass.LLM_TIMEOUT, rules.dominantClass(List.of(
                AiErrorClass.TOOL_BACKEND_ERROR, AiErrorClass.LLM_TIMEOUT)),
                "并列时按七类表序取首个，禁依赖 Map 迭代顺序");
        assertNull(rules.dominantClass(List.of()));
        assertEquals(AiErrorClass.TOOL_BACKEND_ERROR.errorCode(),
                rules.allToolsFailed(null, TRACE).errorCode(),
                "无分类信息时按工具后端类渲染（仍属 F06-14，不新增文案）");

        // 部分成功不属"全部失败"：地板判定在调用方（AgentLoop），本类只钉渲染面
        assertNotNull(rules.allToolsFailed(AiErrorClass.GUARDRAIL_FAIL_CLOSED, TRACE).message());
    }
}
