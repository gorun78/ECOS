package com.chinacreator.gzcm.workspace.analysis;

import java.util.List;
import java.util.Map;

/**
 * 研究执行器端口（L3，2026-11-25 首切片）。
 *
 * <p><b>为什么是端口</b>：RD-Agent 的「D（实现并执行）」未来可能落隔离沙盒（代码型实验），
 * 也可能落 ECOS 内的只读契约探测（查询型实验）。端口把「循环编排」与「执行形态」解耦，
 * 首切片只提供 {@link ReadOnlyResearchExecutor}（确定性、只读、零代码执行），
 * 后续沙盒实现只需换 Bean，循环语义与谱系存储不变。</p>
 *
 * <p><b>红线（实现方必须遵守）</b>：</p>
 * <ul>
 *   <li>不得执行 LLM 生成的代码、不得起外部进程；</li>
 *   <li>不得写生产表、不得改场景指标、不得改蓝图状态；</li>
 *   <li>不得编造指标：拿不到真值就返回 {@code degraded=true}，禁默认「通过」。</li>
 * </ul>
 */
public interface ResearchExecutorPort {

    /** 执行器自述（前端展示「本次研究用什么执行器」）。 */
    Map<String, Object> describe();

    /** 场景上下文摘要（绑定、知识资产、可用口径；只读）。 */
    Map<String, Object> describeContext(String scenarioId);

    /** 首轮候选假设（确定性生成，来源必须指向真实上下文事实）。 */
    List<Map<String, Object>> proposeHypotheses(String scenarioId, String question);

    /** 后续轮假设（基于前几轮的评估结果收敛，禁重复上一轮原文）。 */
    List<Map<String, Object>> refineHypotheses(String scenarioId, String question,
                                               List<Map<String, Object>> previousRounds);

    /**
     * 执行一轮实验。
     *
     * @return 至少含 {@code trace}(步骤流水)、{@code experimentSpec}(规格)、
     *         {@code advice}(建议)、{@code metric}(确定性读数)
     */
    Map<String, Object> executeRound(String scenarioId, String question,
                                     List<Map<String, Object>> hypotheses, int loopIndex);

    /**
     * 评估一轮结果。
     *
     * @return {@code accepted}(bool)、{@code degraded}(bool)、{@code noProgress}(bool)、
     *         {@code evidence}(评估依据)
     */
    Map<String, Object> evaluate(String scenarioId, Map<String, Object> execResult,
                                 List<Map<String, Object>> previousRounds);
}
