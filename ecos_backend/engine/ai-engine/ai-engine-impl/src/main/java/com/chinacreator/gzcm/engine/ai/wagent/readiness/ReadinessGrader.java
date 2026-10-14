package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessCheckStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessGrade;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessLayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * DIKC Readiness 确定性定级器（详细设计-10 F10-08 算法树「定级规则」枝的实位；
 * §4.1 组件落位 {@code wagent/readiness} 的实位；差距分析 P0-8「定级器零实现」的收口点）。
 *
 * <p><b>规格原文</b>（F10-08，REQ-WAG-08）：
 * 「定级规则（确定性，非 LLM）：任一 blocker→D；≥2 fail→C；任一 warn→B；全 pass→A」，
 * 配套边界「grade 与 status 由服务端计算，LLM 仅可填 {@code description/impact} 文本列」、
 * 探测面「失败 = UNKNOWN 且计入缺项，禁算 pass」。</p>
 *
 * <p><b>本类是无状态纯函数</b>：不探测（{@code EngineProbeClient}）、不解析阈值
 * （{@code ThresholdPolicy} 经 sysman 配置单源）、不落库（V230/231/232 写通道）、
 * 不调 LLM。输入 = {@link ReadinessCheck} 集合，输出 = {@link ReadinessGradeResult}。
 * <b>尚未接线</b>：GET #12 仍只读现有评估行、POST #13 refill 仍如实 501，
 * 四层探测与评估落库仍在差距清单里——本批只交定级器本体与具名门禁，不谎称 Readiness 闭环。</p>
 *
 * <p><b>订正注（2026-10-14，批 #146 B2④续）</b>：上段的「尚未接线」已部分失效，逐条对照现状 —
 * ①四层探测已由 {@code EngineProbeClient.probeAll()} 承流（预算 3s/层，异常即
 * {@code reachable=false}，禁 fail-open）；②评估落库已由 {@code ReadinessService.evaluate()}
 * 承流（V231 明细 → V232 缺口 → V230 头 → 替代位的固定序）；③POST #13 refill 不再 501，
 * 轮次闸与派发在 {@code ReadinessGapService}（≤3 超限转 {@code awaiting_input}，HTTP 200）；
 * ④GET #12 <b>仍是只读快照</b>（当前评估行 + V231 缺项 + V232 缺口）：现评由 Run 首评与 refill
 * 复评触发，读端点不同步探测——GET 里打四次引擎探测会把只读面变成写路径并吃掉 3s×4 预算。
 * <b>仍然如实的差距</b>（不随本注消失，登记在差距文档 v1.0.7）：四引擎八度量读端点未接线
 * ⇒ 度量恒 {@code absent} ⇒ 检查项恒 UNKNOWN ⇒ 总级恒不高于 C；V230~V232 迁移脚本未在
 * dev PG 实跑；{@code confirm/fetch/approve} 三类动作的引擎侧端口未接线（只进 deferred 清单）。
 * 本类「无状态纯函数」的定位不变：上述四件全在它之外。</p>
 *
 * <h3>规则（优先级自上而下，先命中先定档：D &gt; C &gt; B &gt; A）</h3>
 * <ol>
 *   <li>无检查项 ⇒ <b>C</b>（{@code NO_CHECK->C}）：一个都没有＝四层全无证据，
 *       禁给 A（"全 pass"需有 pass 可数）；</li>
 *   <li>任一 {@code blocker} ⇒ <b>D</b>（{@code BLOCKER_PRESENT->D}）：规格原文；</li>
 *   <li>任一 {@code fail} ⇒ <b>C</b>（{@code FAIL_PRESENT->C}）：<b>裁决 1，对规格收紧</b>——
 *       原文只写「≥2 fail→C」，<i>恰好 1 个 fail 且无 warn</i> 时四条规则全部落空、无档可归。
 *       依"缺陷面不得优于其证据面"取 C（与 ≥2 fail 同档），禁留空档或降为 B；</li>
 *   <li>任一 {@code unknown} ⇒ <b>C</b>（{@code UNKNOWN_PRESENT->C}）：<b>裁决 2，保守档</b>——
 *       规格令 UNKNOWN「计入缺项、禁算 pass」但未给档位。无证据层不得优于"有证据且失败"层，
 *       故与 fail 同档 C；若冒给 B/A 即把探测失败洗成"基本就绪"，正是 F10-08 要禁的假真理；</li>
 *   <li>任一 {@code warn} ⇒ <b>B</b>（{@code WARN_PRESENT->B}）：规格原文；</li>
 *   <li>其余（非空且全 {@code pass}）⇒ <b>A</b>（{@code ALL_PASS->A}）：规格原文。</li>
 * </ol>
 *
 * <p><b>裁决 3（层位缺探测＝该层 UNKNOWN）</b>：F10-08 算法树第一枝是「并行四层探测」，
 * 即 D/I/K/C 恒应各有结论；某层一项都没有＝该层探测未发生/未回，按同一「禁算 pass」纪律
 * 记该层 UNKNOWN（并因规则 4 把总级压到 C），禁按"该层没问题"处理。</p>
 *
 * <p><b>确定性口径</b>：同一输入（无论对象实例、无论遍历顺序）恒得同一结果——
 * 层位聚合与缺项清单都有全序（层位按 DIKC 声明序、缺项按严重度降序→层序→checkCode 升序），
 * 不做任何"取首行""依赖 HashMap 迭代序"的实现。这是
 * {@code ReadinessGraderTest#gradeIsDeterministicFromThresholdsNotLlm} 断言的对象。</p>
 */
public final class ReadinessGrader {

    /**
     * 档位严重度序（降序）：blocker &gt; fail &gt; unknown &gt; warn &gt; pass。
     *
     * <p>UNKNOWN 置于 fail 之后、warn 之前＝层位聚合取"最差档"时，没跑成的层按"有失败证据"
     * 对待而不降级为 warn；与定级规则 3/4 的保守取向一致。</p>
     */
    private static final Comparator<ReadinessCheckStatus> SEVERITY = Comparator
            .comparingInt((ReadinessCheckStatus s) -> switch (s) {
                case BLOCKER -> 4;
                case FAIL -> 3;
                case UNKNOWN -> 2;
                case WARN -> 1;
                case PASS -> 0;
            }).reversed();

    /** 缺项清单全序：严重度降序 → DIKC 层声明序 → checkCode 升序（禁依赖输入顺序）。 */
    private static final Comparator<ReadinessCheck> MISSING_ORDER = Comparator
            .comparing(ReadinessCheck::status, SEVERITY)
            .thenComparing(c -> c.layer().ordinal())
            .thenComparing(ReadinessCheck::checkCode);

    /**
     * 定级（纯函数，无副作用、无外部依赖）。
     *
     * @param checks 四层检查结论（{@code null} 视同空集 ⇒ C；元素不得为 null）
     * @return 总级 + DIKC 四层档位 + 档位计数 + 缺项清单 + 命中规则标识
     */
    public ReadinessGradeResult grade(Collection<ReadinessCheck> checks) {
        List<ReadinessCheck> items = checks == null ? List.of() : new ArrayList<>(checks);
        for (ReadinessCheck check : items) {
            if (check == null) {
                throw new IllegalArgumentException("checks 含 null 元素（无结论即无输入，禁半集定级）");
            }
        }

        Map<ReadinessCheckStatus, Integer> statusCounts = new EnumMap<>(ReadinessCheckStatus.class);
        for (ReadinessCheck check : items) {
            statusCounts.merge(check.status(), 1, Integer::sum);
        }
        Map<ReadinessLayer, ReadinessCheckStatus> layerStatus = layerWorstStatus(items);

        ReadinessGrade grade;
        String matchedRule;
        if (items.isEmpty()) {
            grade = ReadinessGrade.C;
            matchedRule = "NO_CHECK->C";
        } else if (count(statusCounts, ReadinessCheckStatus.BLOCKER) > 0) {
            grade = ReadinessGrade.D;
            matchedRule = "BLOCKER_PRESENT->D";
        } else if (count(statusCounts, ReadinessCheckStatus.FAIL) > 0) {
            grade = ReadinessGrade.C;
            matchedRule = "FAIL_PRESENT->C";
        } else if (count(statusCounts, ReadinessCheckStatus.UNKNOWN) > 0) {
            grade = ReadinessGrade.C;
            matchedRule = "UNKNOWN_PRESENT->C";
        } else if (count(statusCounts, ReadinessCheckStatus.WARN) > 0) {
            grade = ReadinessGrade.B;
            matchedRule = "WARN_PRESENT->B";
        } else {
            grade = ReadinessGrade.A;
            matchedRule = "ALL_PASS->A";
        }

        List<ReadinessCheck> missing = items.stream().filter(ReadinessCheck::missing)
                .sorted(MISSING_ORDER).toList();
        return new ReadinessGradeResult(grade, layerStatus, statusCounts, missing, matchedRule);
    }

    /** DIKC 四层逐层最差档；无检查项的层 ⇒ UNKNOWN（裁决 3）。 */
    private static Map<ReadinessLayer, ReadinessCheckStatus> layerWorstStatus(List<ReadinessCheck> items) {
        Map<ReadinessLayer, ReadinessCheckStatus> worst = new EnumMap<>(ReadinessLayer.class);
        for (ReadinessCheck check : items) {
            // 只在"该层真实跑出的档位"里取最差；先 putIfAbsent 预置 UNKNOWN 会让 UNKNOWN
            // 的反向严重度把该层所有真实档位都吃掉（首跑实测：D 层 pass+warn 被算成 unknown）
            worst.merge(check.layer(), check.status(),
                    (current, candidate) -> SEVERITY.compare(candidate, current) < 0 ? candidate : current);
        }
        for (ReadinessLayer layer : ReadinessLayer.values()) {
            worst.putIfAbsent(layer, ReadinessCheckStatus.UNKNOWN);
        }
        return worst;
    }

    private static int count(Map<ReadinessCheckStatus, Integer> counts, ReadinessCheckStatus status) {
        return counts.getOrDefault(status, 0);
    }
}
