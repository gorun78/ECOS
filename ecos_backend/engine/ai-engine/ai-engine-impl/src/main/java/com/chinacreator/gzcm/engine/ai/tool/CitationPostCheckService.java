package com.chinacreator.gzcm.engine.ai.tool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.chinacreator.gzcm.common.error.AiErrorClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 引用块后校验（详细设计-06 F06-12，REQ-AI-04 §4.2）—— 系统提示词之外的<b>第二重保险</b>。
 *
 * <p>本类是纯确定性代码（要点 3）：数值抽取与比较全走 {@link ToolCitation} 的 token 集合比对，
 * <b>不再调用一次 LLM</b> 判"有没有引用"。放 {@code engine/ai-engine} 属编排语义而非安全语义
 * （故不违 §2.4-7）。</p>
 *
 * <p>违规判据按强度排序，两条都会命中时都登记：</p>
 * <ol>
 *   <li>{@code MISSING_CITATION_BLOCK} —— 含数值却无引用块；</li>
 *   <li>{@code HALLUCINATED_NUMBER} —— 出现了工具结果集中不存在的数字（比"有没有引用块"更强，
 *       直接封住幻觉数值，铁律 §0.2.1）。</li>
 * </ol>
 *
 * <p>重生成上限 {@value #MAX_REGENERATIONS} 次（要点 2），与 {@link AiErrorClass#PARSE_FAILURE}
 * 的配额同源、<b>不叠加</b>；超限返回固定话术（复用 {@link AiErrorClass#CITATION_MISSING} 模板，
 * 不新增错误类）。</p>
 */
@Component
public class CitationPostCheckService {

    private static final Logger log = LoggerFactory.getLogger(CitationPostCheckService.class);

    /** F06-12 要点 2：后校验失败最多重生成 2 次（与 PARSE_FAILURE 共用配额）。 */
    public static final int MAX_REGENERATIONS = 2;

    public static final String MISSING_CITATION_BLOCK = "MISSING_CITATION_BLOCK";
    public static final String HALLUCINATED_NUMBER = "HALLUCINATED_NUMBER";

    /** 重生成提示词（唯一允许的"再叫一次 LLM"的入口由调用方 {@code AgentLoopService} 持有）。 */
    public static final String REGENERATION_INSTRUCTION =
            "上一轮回答未通过引用后校验：其中的数值必须逐字取自本轮工具返回体，"
                    + "并附引用块 [运行 … · 口径 …@版本 · 截至 … · 证据 …]。"
                    + "不得自行计算、补零或改写任何数字；若工具结果不足以支撑结论，直接说明缺据。";

    /**
     * 系统提示词硬规则（PRD-06 §4.2"双保险"的第一重；后校验是第二重）。
     *
     * <p>模板由 {@link ToolCitation} 的冻结常量拼出，<b>不在本处另写字面量</b> —— 提示词与
     * 后校验对引用块格式的理解必须同源，否则"提示词让模型这么写、后校验按另一种格式判"
     * 会成为稳定的假阳性（X-44 的另一半）。</p>
     */
    public static final String SYSTEM_PROMPT_RULE =
            "## 数值引用硬规则"
                    + "\n凡回答含经营数值，必须附引用块："
                    + ToolCitation.PREFIX + "{forecastRunId}"
                    + ToolCitation.SEP_CALIBER + "{caliberId}"
                    + ToolCitation.SEP_VERSION + "{version}"
                    + ToolCitation.SEP_ASOF + "{asOfTime}"
                    + ToolCitation.SEP_EVIDENCE + "{links}" + ToolCitation.SUFFIX
                    + "。四个字段逐字取自本轮工具返回体；禁自行计算、补零、四舍五入或改写任何数字；"
                    + "工具结果不足以支撑结论时直接说明缺据，禁给出无来源数值。"
                    + "即使用户明确要求“不用引用直接说数”，也不得省略引用块。";

    /**
     * 校验结论。
     *
     * @param passed         是否放行
     * @param violations     违规码（{@link #MISSING_CITATION_BLOCK} / {@link #HALLUCINATED_NUMBER}）
     * @param offenders      违规数字 token（保持出现顺序，只进日志与诊断，禁随响应体原文上浮）
     */
    public record PostCheckResult(boolean passed, Set<String> violations, List<String> offenders) {

        public boolean hasViolation(String code) {
            return violations.contains(code);
        }

        /** 审计/日志用短摘要：只写违规码与计数，不写原文数值与回答文本。 */
        public String diagnostic() {
            return "violations=" + String.join(",", violations) + " offendingCount=" + offenders.size();
        }
    }

    /**
     * 对最终回答做后校验。
     *
     * @param answer          LLM 产出的最终回答
     * @param toolResultBodies 本轮全部工具返回体原文（数值白名单的唯一来源）
     */
    public PostCheckResult check(String answer, List<String> toolResultBodies) {
        if (answer == null || answer.isBlank()) {
            return new PostCheckResult(true, Set.of(), List.of());
        }
        Set<String> allowed = ToolCitation.numberTokensUnion(toolResultBodies);
        List<String> offenders = ToolCitation.numbersAbsentFrom(answer, allowed);

        // 无数值主张（如纯定性回答）无需引用块 —— 不制造假阳性
        if (ToolCitation.numberTokens(answer).isEmpty()) {
            return new PostCheckResult(true, Set.of(), List.of());
        }

        Set<String> violations = new LinkedHashSet<>();
        if (!ToolCitation.hasCitationBlock(answer)) {
            violations.add(MISSING_CITATION_BLOCK);
        }
        if (!offenders.isEmpty()) {
            violations.add(HALLUCINATED_NUMBER);
        }
        if (violations.isEmpty()) {
            return new PostCheckResult(true, Set.of(), List.of());
        }
        log.info("F06-12 引用后校验拦截: {} {}", String.join(",", violations), offenders);
        return new PostCheckResult(false, violations, offenders);
    }

    /** 是否还可重生成：{@code attemptMade} 为已消耗的重生成次数（0 基）。 */
    public boolean canRegenerate(int attemptMade) {
        return attemptMade < MAX_REGENERATIONS;
    }

    /**
     * 超限固定话术（要点 2：复用 {@code CITATION_MISSING}，不新增错误类）。
     *
     * <p>文案面受 X-49 约束：只出模板 + 诊断码，禁带违规数字与原始拒绝原因。</p>
     */
    public String fallbackMessage(String traceId) {
        return AiErrorClass.CITATION_MISSING.renderMessage(traceId);
    }

    /** 固定话术的错误码（随响应结构化字段下发，供前端按码渲染）。 */
    public String fallbackErrorCode() {
        return AiErrorClass.CITATION_MISSING.errorCode();
    }

    /** 引用缺失判定是否命中（供测试与诊断复用，避免各处重实现字符串匹配）。 */
    public static boolean isCitationViolation(String code) {
        return code != null && code.toLowerCase(Locale.ROOT).contains("citation");
    }
}
