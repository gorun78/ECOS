package com.chinacreator.gzcm.engine.ai.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.chinacreator.gzcm.common.error.AiErrorClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 三条拒绝规则（详细设计-06 F06-13，REQ-AI-04 §4.2）—— 实现为<b>咽喉上的前置判定</b>，
 * 不是系统提示词里的祈使句（差距 X-45：原实现只有提示词，模型可无视）。
 *
 * <p>三条规则的确定性来源各不同，共同点是<b>判定代码而非模型自觉</b>：</p>
 * <ol>
 *   <li>口径缺失/未批准 → 澄清结构 {@code {type:CLARIFY, missing:caliber, nextAction}}，
 *       直接返回，不进 LLM 数值路径（判据来自 {@link CaliberStatusSource}，不可判按未批准处理）；</li>
 *   <li>"算一下/估算金额"意图 → 确定性关键词 + 结构判定命中 → {@code TOOL_BACKEND_REQUIRED} 引导，
 *       指向<b>创建确定性预测运行</b>（分册 09 FC-02），禁 LLM 自行产数；</li>
 *   <li>工具全部失败 → 只渲染 F06-14 的分类结果，禁任何 F06-01 之外的兜底文案（编造数值的机器
 *       正是 mock-success，X-50）。</li>
 * </ol>
 */
@Component
public class RefusalRulesService {

    private static final Logger log = LoggerFactory.getLogger(RefusalRulesService.class);

    /** 冻结字面量：规则 2 的引导码（不进 {@code AiErrorClass} —— 它不是错误，是下一步动作）。 */
    public static final String TOOL_BACKEND_REQUIRED = "TOOL_BACKEND_REQUIRED";

    /** 三种响应类型（与 {@code AgentLoopResult.answerType} 同名字面量）。 */
    public static final String TYPE_NORMAL = "NORMAL";
    public static final String TYPE_CLARIFY = "CLARIFY";
    public static final String TYPE_REFUSED = "REFUSED";

    /** 规则 1 的缺失项标识。 */
    public static final String MISSING_CALIBER = "caliber";

    /** 创建确定性预测运行的引导端点（分册 09 FC-02；跨册接缝义务见类头）。 */
    public static final String FORECAST_RUN_CREATION_POINTER = "POST /api/v1/workspace/forecast-runs";

    /** 经营数值意图关键词（决定规则 1/2 是否需要触发——非数值类问题不受本闸约束）。 */
    private static final Pattern AMOUNT_INTENT = Pattern.compile(
            "(金额|数额|多少钱|营收|收入|成本|利润|毛利|净利|现金流|回款|预算|投资额|产值|GDP|GMV|ARPU)");

    /** 估算/心算意图关键词（规则 2 的意图标记，PRD-06 §4.2"要求 AI 算一下/估算"）。 */
    private static final Pattern ESTIMATE_INTENT = Pattern.compile(
            "(算一下|帮我算|你算|估一下|估算|估计|大约|大概|差不多|拍一个|拍脑袋|猜一个|随口|口算|给个数|给个数字)");

    /** 澄清文案模板（受 X-49 约束：只出模板 + 诊断码，禁带口径内部数据或原始拒绝原因）。 */
    private static final String CALIBER_CLARIFY_MESSAGE =
            "这个问题需要一个已批准的统计口径才能给数。请指定口径（或先在本体域发布并批准口径），"
                    + "我再按该口径取数（诊断码 {traceId}）";

    /**
     * 拒绝/澄清结论：{@code answerType} 为 CLARIFY|REFUSED 时，调用方必须直接返回本结构，
     * <b>禁</b>再进模型数值路径。
     */
    public record Refusal(String answerType, String missing, String nextAction,
                          String errorCode, String message) {

        public boolean blocksLlm() {
            return !TYPE_NORMAL.equals(answerType);
        }

        /** 澄清结构（F06-13 要点 1 的 {@code {type:CLARIFY, missing:caliber, nextAction}} 形态）。 */
        public Map<String, Object> toClarification() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", answerType);
            if (missing != null) map.put("missing", missing);
            if (nextAction != null) map.put("nextAction", nextAction);
            if (errorCode != null) map.put("errorCode", errorCode);
            return map;
        }
    }

    /**
     * 咽喉前置判定：入参是用户原文 + 本轮口径编码（可为空）。
     *
     * <p>规则顺序即优先级：口径未批准先于估算意图（前者是"无据"，后者是"据在别处"），
     * 命中即返回，不再评估后续规则。</p>
     *
     * @param userText   用户原文
     * @param caliberCode 本轮携带的口径编码（空/未批准都按"无可用口径"处理）
     * @param statusSource 口径状态源（未装配时按 UNKNOWN 处理，同样 fail-closed）
     * @param traceId    诊断码
     */
    public Optional<Refusal> preAdjudicate(String userText, String caliberCode,
                                           CaliberStatusSource statusSource, String traceId) {
        if (!requiresBusinessNumber(userText)) {
            return Optional.empty();
        }
        String status = statusSource == null ? CaliberStatusSource.UNKNOWN
                : statusSource.statusOf(caliberCode);
        if (!CaliberStatusSource.APPROVED.equals(status)) {
            Refusal clarify = clarifyMissingCaliber(traceId);
            log.info("F06-13 规则 1 命中（口径非 APPROVED 即澄清，不可判按未批准处理）: status={} trace={}",
                    status, traceId);
            return Optional.of(clarify);
        }
        return estimateIntent(userText).map(nextAction -> {
            log.info("F06-13 规则 2 命中（估算金额意图 → 引导创建确定性预测运行）: trace={}", traceId);
            return refuseEstimate(nextAction, traceId);
        });
    }

    /** 规则 1 的澄清结构（可单测：无 APPROVED 口径时必须只提问、不给数）。 */
    public Refusal clarifyMissingCaliber(String traceId) {
        return new Refusal(TYPE_CLARIFY, MISSING_CALIBER, "SELECT_OR_PUBLISH_APPROVED_CALIBER",
                AiErrorClass.CALIBER_CLARIFICATION.errorCode(),
                CALIBER_CLARIFY_MESSAGE.replace("{traceId}", nullToDash(traceId)));
    }

    /** 规则 2 的拒绝结构（引导创建确定性预测运行，绝不由 LLM 产数）。 */
    public Refusal refuseEstimate(String nextAction, String traceId) {
        String message = "经营数值不能靠估算给出，必须由确定性预测运行产出。"
                + "请先创建预测运行（" + FORECAST_RUN_CREATION_POINTER + "）再读取结果"
                + "（诊断码 " + nullToDash(traceId) + "）";
        return new Refusal(TYPE_REFUSED, null,
                nextAction == null ? TOOL_BACKEND_REQUIRED : nextAction,
                AiErrorClass.INVALID_ARGUMENT.errorCode(), message);
    }

    /**
     * 规则 3：本轮工具全部失败时的渲染入口。
     *
     * <p>只接受 F06-14 已分类的 {@link AiErrorClass} 与其渲染文案，<b>不</b>接受原始异常字符串
     * （X-49 禁原文上浮），也不产生任何"F06-01 之外的兜底文案"。</p>
     *
     * @param dominantClass 占多数的失败分类（由调用方按分类计数取众数，保持确定性）
     * @param traceId       诊断码
     */
    public Refusal allToolsFailed(AiErrorClass dominantClass, String traceId) {
        AiErrorClass errorClass = dominantClass == null ? AiErrorClass.TOOL_BACKEND_ERROR : dominantClass;
        return new Refusal(TYPE_REFUSED, null, "RETRY_OR_CHECK_TOOL_BACKEND",
                errorClass.errorCode(), errorClass.renderMessage(traceId));
    }

    /** 是否经营数值类请求（规则 1/2 的触发地板：非数值请求不因口径而澄清）。 */
    public boolean requiresBusinessNumber(String userText) {
        if (userText == null || userText.isBlank()) return false;
        String s = userText.toLowerCase(Locale.ROOT);
        return AMOUNT_INTENT.matcher(s).find() || ESTIMATE_INTENT.matcher(s).find();
    }

    /** 规则 2 的意图判定（确定性关键词 + 结构：估算词与金额词同时命中才算）。 */
    public Optional<String> estimateIntent(String userText) {
        if (userText == null || userText.isBlank()) return Optional.empty();
        String s = userText.toLowerCase(Locale.ROOT);
        boolean estimates = ESTIMATE_INTENT.matcher(s).find();
        boolean aboutAmount = AMOUNT_INTENT.matcher(s).find();
        if (!estimates || !aboutAmount) return Optional.empty();
        return Optional.of(TOOL_BACKEND_REQUIRED);
    }

    /**
     * 从失败分类列表中取众数作为主导分类（并列时按 PRD §5.1 表序取前者，保证确定性）。
     *
     * <p>返回 {@code null} 表示无分类信息（调用方按 {@code TOOL_BACKEND_ERROR} 兜底渲染）。</p>
     */
    public AiErrorClass dominantClass(List<AiErrorClass> classes) {
        if (classes == null || classes.isEmpty()) return null;
        Map<AiErrorClass, Integer> counts = new LinkedHashMap<>();
        for (AiErrorClass c : classes) {
            if (c != null) counts.merge(c, 1, Integer::sum);
        }
        AiErrorClass best = null;
        int bestCount = 0;
        for (AiErrorClass candidate : AiErrorClass.SEVEN_CLASSES) {
            int count = counts.getOrDefault(candidate, 0);
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        if (best != null) return best;
        // 非七类的补充分类（INVALID_ARGUMENT 等）仍可能出现，按插入序取第一个
        return counts.keySet().stream().findFirst().orElse(null);
    }

    private static String nullToDash(String traceId) {
        return traceId == null || traceId.isBlank() ? "-" : traceId;
    }
}
