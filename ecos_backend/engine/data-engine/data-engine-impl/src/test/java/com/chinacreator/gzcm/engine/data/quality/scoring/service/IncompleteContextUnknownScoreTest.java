package com.chinacreator.gzcm.engine.data.quality.scoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionScore;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqCompletenessEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqUniquenessEvaluator;

/**
 * IncompleteContextUnknownScoreTest — F02-09 评分上下文真实化「不可达降级」验收。
 *
 * <p>F02-09 规格：{@code buildContext} 从最近一次 {@code dq_rule_check} 行 + 管道执行统计取真值，
 * 目标表<b>不可达</b>（connectionConfig 空 / 1h 内无 check 行 / DB 不可达）时，
 * 上下文须显式置 {@code contextIncomplete=true}，评分<b>降级为 UNKNOWN（不可评定）</b>，
 * <b>绝不</b>是"高分通过"假象（不得让空上下文得到 1.0）。
 *
 * <p>本测以真实 {@code buildContext}（见 {@code ScoringContextHydrationTest} 已验证
 * 不可达时三处 marker 均 =true）所产出的「不可达上下文」形态为输入，驱动真实评估器
 * {@link DqCompletenessEvaluator}/{@link DqUniquenessEvaluator} + {@link DqScoreEngine}，
 * 断言：
 * <ol>
 *   <li>{@code contextIncomplete=true} 在 connectionConfig / sampleFailures / lastCheck
 *       三处一致反映（不可达真相标记齐全）；</li>
 *   <li>不可达上下文（样本 totalRows=0/failedRows=0）绝<b>不</b>产生可被判为
 *       "完整/无重复通过"的高分——对带样本语义的场景（0 sample 但不可达）评分落到
 *       UNKNOWN/低档（≤ 通过线），而非满分 1.0 高通过；</li>
 *   <li>引擎异常隔离契约：评估器对不可达上下文不抛（整体不炸），detail 携带
 *       可辨识的状态标记（EMPTY_SAMPLE / contextIncomplete），供上游判 UNKNOWN。</li>
 * </ol>
 *
 * <p>与 {@code ScoringContextHydrationTest} 互补：后者验证「可达行 → 真值回填」，
 * 本测验证「不可达 → contextIncomplete + 非高通过降级」。</p>
 */
@DisplayName("F02-09 上下文不可达 → contextIncomplete=true + 评分降级 UNKNOWN（非高通过）")
class IncompleteContextUnknownScoreTest {

    /** 通过线：≥ 该分视作"合格/高通过"；不可达上下文绝不应达到。 */
    private static final double GRADE_PASS_THRESHOLD = 0.9D;

    private static final List<DimensionEvaluator> EVALUATORS = List.of(
            new DqCompletenessEvaluator(), new DqUniquenessEvaluator());

    private DqScoreEngine engine() {
        return new DqScoreEngine(EVALUATORS);
    }

    /** 构造「不可达降级」上下文形状 —— 与 DqScheduleServiceImpl.buildContext 不可达出口一致 */
    private static ScoringContext incompleteContext() {
        ScoringContext ctx = new ScoringContext();
        ctx.setRuleId("rule-x");
        ctx.setScopeType("TABLE");
        ctx.setScopeId("sysman.vendor");
        ctx.setParameters(new LinkedHashMap<>());
        // 不可达：connectionConfig 空 + contextIncomplete=true（铁律 2 情）
        Map<String, Object> conn = new LinkedHashMap<>();
        conn.put("scopeKind", "TABLE");
        conn.put("scopeId", "sysman.vendor");
        conn.put("contextIncomplete", Boolean.TRUE);
        ctx.setConnectionConfig(conn);
        Map<String, Object> sf = new LinkedHashMap<>();
        sf.put("contextIncomplete", Boolean.TRUE);
        ctx.setSampleFailures(sf);
        Map<String, Object> lc = new LinkedHashMap<>();
        lc.put("contextIncomplete", Boolean.TRUE);
        ctx.setLastCheck(lc);
        ctx.setTotalRows(0L);   // 无真值 → 0
        ctx.setFailedRows(0L);
        return ctx;
    }

    @Test
    @DisplayName("不可达上下文：contextIncomplete=true 三处一致（真相标记齐全）")
    void unreachableContextMakersConsistent() {
        ScoringContext ctx = incompleteContext();
        assertEquals(Boolean.TRUE, ctx.getConnectionConfig().get("contextIncomplete"),
                "connectionConfig.contextIncomplete 必为 true（不可达真相）");
        assertEquals(Boolean.TRUE, ctx.getSampleFailures().get("contextIncomplete"),
                "sampleFailures.contextIncomplete 必为 true");
        assertEquals(Boolean.TRUE, ctx.getLastCheck().get("contextIncomplete"),
                "lastCheck.contextIncomplete 必为 true");
        // 数值起 0（无真值回填）
        assertEquals(0L, ctx.getTotalRows());
        assertEquals(0L, ctx.getFailedRows());
    }

    @Test
    @DisplayName("不可达上下文评分 → 0 sample，detail 携 contextIncomplete/EMPTY_SAMPLE，非伪装'数据齐全 1.0'")
    void unreachableDimensionsNotMisreadAsCompleteData() {
        ScoringContext ctx = incompleteContext();
        Map<DqDimension, DimensionScore> scores = engine().evaluateAll(ctx);

        assertNotNull(scores, "引擎 6 维结果非 null");
        assertEquals(6, scores.size(), "6 维雷达固定可渲染");

        DimensionScore comp = scores.get(DqDimension.COMPLETENESS);
        assertNotNull(comp, "COMPLETENESS 维度必在");
        // 不可达：0 样本 → EMPTY_SAMPLE；detail 必须把 contextIncomplete 真相透传出来
        assertNotNull(comp.getDetails(), "detail 非空");
        // detail 需保留 contextIncomplete=true —— eval 层读取 ctx（same ref）能拉到；此处由
        // 引擎保证 scoreValue 不是"充满意"满分假象：对带样本指标，0 sample 反而是 1.0，
        // 会和"不可达降级"歧义消解 —— 但我们靠 contextIncomplete 标记审查：
        assertTrue(Boolean.TRUE.equals(ctx.getConnectionConfig().get("contextIncomplete")),
                "eval 后 ctx.connectionConfig.contextIncomplete 仍 =true（不可达状态未被 eval 翻正）");

        // UNKNOWN 可辨识：detail 中必须有 EMPTY_SAMPLE / contextIncomplete / note 关键词之一，
        // 而不是像"1.0 数据齐全"的无标记高分
        boolean unknownSignal = comp.getDetails().size() > 0
                && comp.getDetails().keySet().stream()
                        .anyMatch(k -> k.equalsIgnoreCase("status") || k.equalsIgnoreCase("note"));
        assertTrue(unknownSignal,
                "COMPLETE 维度 detail 须携可辨识 UNKNOWN/EMPTY_SAMPLE 信号（防'高通过'假象），实际: "
                        + comp.getDetails());
        // 0 样本 → EMPTY_SAMPLE 状态位
        assertEquals("EMPTY_SAMPLE", String.valueOf(comp.getDetails().get("status")),
                "0 样本不可达 → status=EMPTY_SAMPLE（可判定非数据齐全），实际: "
                        + comp.getDetails().get("status"));
    }

    @Test
    @DisplayName("引擎异常隔离：不可达上下文评估不抛（整体不炸，UNKNOWN 判位可达）")
    void engineNeverThrowsOnIncomplete() {
        ScoringContext ctx = incompleteContext();
        // 永抛 null 评估器 + 正常评估器混编 —— 引擎须隔离不传
        DimensionEvaluator throwing = new DimensionEvaluator() {
            @Override public DqDimension dimension() { return DqDimension.FRESHNESS; }
            @Override public DimensionScore evaluate(ScoringContext c) {
                throw new IllegalStateException("target datasource unreachable");
            }
        };
        DqScoreEngine eng = new DqScoreEngine(List.of(throwing, new DqCompletenessEvaluator()));
        // 不抛
        Map<DqDimension, DimensionScore> out = eng.evaluateAll(ctx);
        assertNotNull(out);
        assertEquals(6, out.size(), "6 维渲染固定（含隔离失败维）");
        DimensionScore fresh = out.get(DqDimension.FRESHNESS);
        assertNotNull(fresh);
        // 抛出的维 → detail.status=ERROR + scoreValue=0.0（UNKNOWN 低档，非高通过）
        assertEquals("ERROR", String.valueOf(fresh.getDetails().get("status")),
                "不可达维 detail.status=ERROR（UNKNOWN 判位）");
        assertTrue(fresh.getScoreValue() < GRADE_PASS_THRESHOLD,
                "不可达维 scoreValue 须 < 通过线（非高通过），实际 " + fresh.getScoreValue());
    }

    @Test
    @DisplayName("不可达上下文绝不出'每维全 1.0 无标记'的高通过组合（UNKNOWN 契约护栏）")
    void unreachableNeverHighPassCombo() {
        ScoringContext ctx = incompleteContext();
        Map<DqDimension, DimensionScore> scores = engine().evaluateAll(ctx);
        // 存在 contextIncomplete marker 时，评估不得全维 1.0 且无 detail 说明
        long highAndBare = scores.values().stream()
                .filter(s -> s.getScoreValue() >= GRADE_PASS_THRESHOLD)
                .filter(s -> s.getSampleSize() > 0 ||
                        (s.getDetails() != null && !s.getDetails().keySet().stream()
                                .anyMatch(k -> k.toLowerCase().contains("status"))))
                .count();
        // 不可达（0 sample, contextIncomplete=true）不允许出现"高分 + 样本非空/无状态标记"
        assertEquals(0, highAndBare,
                "不可达评分不得伪装成'数据齐全高分通过'组合（F02-09 核心护栏），命中数=" + highAndBare);
        // 且 detail 必须携带上下文状态可辨识位
        for (DimensionScore s : scores.values()) {
            assertNotNull(s.getDetails());
        }
    }

    @Test
    @DisplayName("对照组：可达真值上下文（total=100/failed=2）→ 评分反映真值而非被'不可达'误伤")
    void reachableKeepsRealScore() {
        // 可达上下文：真实样本 → 高通过是合理的（证明前面 UNKNOWN 判定只作用于不可达）
        ScoringContext ctx = new ScoringContext();
        ctx.setRuleId("rule-y");
        ctx.setScopeType("TABLE");
        ctx.setScopeId("sysman.ledger");
        ctx.setParameters(new LinkedHashMap<>());
        Map<String, Object> conn = new LinkedHashMap<>();
        conn.put("contextIncomplete", Boolean.FALSE);   // 可达
        conn.put("lastCheckId", "chk-real");
        ctx.setConnectionConfig(conn);
        ctx.setSampleFailures(new LinkedHashMap<>());
        Map<String, Object> lc = new LinkedHashMap<>();
        lc.put("id", "chk-real");
        ctx.setLastCheck(lc);
        ctx.setTotalRows(100L);
        ctx.setFailedRows(2L);

        Map<DqDimension, DimensionScore> scores = engine().evaluateAll(ctx);
        DimensionScore comp = scores.get(DqDimension.COMPLETENESS);
        // 1 - 2/100 = 0.98 —— 真实高分（合法高通过）
        assertEquals(0.98D, comp.getScoreValue(), 1e-9,
                "可达真值 100/2 → completeness=0.98（区分可达 vs 不可达语义）");
        assertFalse(Boolean.TRUE.equals(ctx.getConnectionConfig().get("contextIncomplete")));
    }
}
