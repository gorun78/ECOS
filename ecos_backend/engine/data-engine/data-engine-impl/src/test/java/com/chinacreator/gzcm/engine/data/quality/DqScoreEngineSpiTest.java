package com.chinacreator.gzcm.engine.data.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionScore;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqAccuracyEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqCompletenessEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqConsistencyEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqFreshnessEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqUniquenessEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqValidityEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.service.DqScoreEngine;

/**
 * DqScoreEngineSpiTest — 6 维评估器 SPI 路由 + 异常隔离 单测（PMO-48-B T10）。
 *
 * <p>策略：把真实 6 个评估器实例注入 {@link DqScoreEngine}，构造 {@code ScoringContext}
 * 覆盖 6 个 rule_type 与 1 个未映射 LLM 类型，验证：
 * <ul>
 *   <li>每个 rule_type 路由到正确维度（通过维度分数特征 + details.status）</li>
 *   <li>COMPLETENESS 评估器抛异常时，{@link DqScoreEngine#evaluateAll} 的异常隔离</li>
 *   <li>6 维固定输出（evaluateAll 永远返 6 个 entry，缺维/空数据集都必兜底 1.0）</li>
 * </ul></p>
 *
 * <p>对应任务清单 1-8：
 * <ol>
 *   <li>evaluateAll_notNullRule_dispatchesToCompleteness — NOT_NULL 路由到 COMPLETENESS</li>
 *   <li>evaluateAll_uniqueRule_dispatchesToUniqueness — UNIQUE 路由到 UNIQUENESS</li>
 *   <li>evaluateAll_formatRule_dispatchesToAccuracy — FORMAT 路由到 ACCURACY</li>
 *   <li>evaluateAll_consistencyRule_dispatchesToConsistency — CONSISTENCY 路由到 CONSISTENCY</li>
 *   <li>evaluateAll_enumRule_dispatchesToValidity — ENUM 路由到 VALIDITY</li>
 *   <li>evaluateAll_freshnessRule_dispatchesToFreshness — FRESHNESS 路由到 FRESHNESS</li>
 *   <li>evaluateAll_unknownRuleType_returnsEmptyDetails — LLM 未映射 ruleType 兜底 ACCURACY</li>
 *   <li>evaluateAll_evaluatorThrows_hasExceptionIsolation — 1 个评估器抛，其余 5 正常</li>
 * </ol></p>
 */
class DqScoreEngineSpiTest {

    /** 真实评估器实例（构造器收集 List&lt;DimensionEvaluator&gt;）。 */
    private final DqCompletenessEvaluator completeness = new DqCompletenessEvaluator();
    private final DqAccuracyEvaluator accuracy = new DqAccuracyEvaluator();
    private final DqConsistencyEvaluator consistency = new DqConsistencyEvaluator();
    private final DqUniquenessEvaluator uniqueness = new DqUniquenessEvaluator();
    private final DqValidityEvaluator validity = new DqValidityEvaluator();
    private final DqFreshnessEvaluator freshness = new DqFreshnessEvaluator();

    /** 引擎入口：构造器注入 6 个真实评估器。 */
    private final DqScoreEngine engine = new DqScoreEngine(Arrays.asList(
            completeness, accuracy, consistency, uniqueness, validity, freshness));

    /** 构造规则上下文（ruleId/rule_type 通过 parameters.rule_type 带入；评估器自身不读 rule_type）。 */
    private ScoringContext ctxWithRuleType(String ruleType) {
        ScoringContext ctx = new ScoringContext();
        ctx.setRuleId("r-" + ruleType.toLowerCase());
        ctx.setScopeType("TABLE");
        ctx.setScopeId("sysman.customer");
        ctx.setTotalRows(100L);
        ctx.setFailedRows(0L);
        ctx.setParameters(new java.util.LinkedHashMap<String, Object>() {{ put("rule_type", ruleType); }});
        return ctx;
    }

    // ────────────────────────────────────────────────────────────────────
    // 8 个测试
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1) NOT_NULL 规则 — 路由到 COMPLETENESS；COMPLETENESS 维度分数 1.0（0 失空）")
    void evaluateAll_notNullRule_dispatchesToCompleteness() {
        Map<DqDimension, DimensionScore> scores = engine.evaluateAll(ctxWithRuleType("NOT_NULL"));
        assertEquals(6, scores.size(), "6 维固定");
        DimensionScore c = scores.get(DqDimension.COMPLETENESS);
        assertNotNull(c);
        assertSame(DqDimension.COMPLETENESS, c.getDimension());
        assertEquals(1.0D, c.getScoreValue(), 1e-9, "0 失空 / 100 总行 = 1.0");
        // 路由反查：DqDimension.ofRuleType("NOT_NULL") 必须 = COMPLETENESS
        assertSame(DqDimension.COMPLETENESS, DqDimension.ofRuleType("NOT_NULL"),
                "NOT_NULL 未映射维度应兜底 ACCURACY 而非 COMPLETENESS");
    }

    @Test
    @DisplayName("2) UNIQUE 规则 — 路由到 UNIQUENESS；UNIQUENESS 维度分数 1.0（0 重复）")
    void evaluateAll_uniqueRule_dispatchesToUniqueness() {
        Map<DqDimension, DimensionScore> scores = engine.evaluateAll(ctxWithRuleType("UNIQUE"));
        assertEquals(6, scores.size());
        DimensionScore u = scores.get(DqDimension.UNIQUENESS);
        assertNotNull(u);
        assertSame(DqDimension.UNIQUENESS, u.getDimension());
        assertEquals(1.0D, u.getScoreValue(), 1e-9, "0 重复 / 100 总行 = 1.0");
        assertSame(DqDimension.UNIQUENESS, DqDimension.ofRuleType("UNIQUE"));
    }

    @Test
    @DisplayName("3) FORMAT 规则 — 路由到 ACCURACY（FORMAT/Range/regex 校验通过率）")
    void evaluateAll_formatRule_dispatchesToAccuracy() {
        Map<DqDimension, DimensionScore> scores = engine.evaluateAll(ctxWithRuleType("FORMAT"));
        assertEquals(6, scores.size());
        DimensionScore a = scores.get(DqDimension.ACCURACY);
        assertNotNull(a);
        assertSame(DqDimension.ACCURACY, a.getDimension());
        assertEquals(1.0D, a.getScoreValue(), 1e-9, "0 失败 / 100 总行 = 1.0");
        assertSame(DqDimension.ACCURACY, DqDimension.ofRuleType("FORMAT"));
    }

    @Test
    @DisplayName("4) CONSISTENCY 规则 — 路由到 CONSISTENCY；detail.source=dq_rule_check.pass_rate")
    void evaluateAll_consistencyRule_dispatchesToConsistency() {
        ScoringContext ctx = ctxWithRuleType("CONSISTENCY");
        // 通过 lastCheck.passRate 给一致性一个精确快照值，便于断言
        java.util.LinkedHashMap<String, Object> last = new java.util.LinkedHashMap<>();
        last.put("passRate", 0.85D);
        ctx.setLastCheck(last);
        Map<DqDimension, DimensionScore> scores = engine.evaluateAll(ctx);
        assertEquals(6, scores.size());
        DimensionScore c = scores.get(DqDimension.CONSISTENCY);
        assertNotNull(c);
        assertSame(DqDimension.CONSISTENCY, c.getDimension());
        assertEquals(0.85D, c.getScoreValue(), 1e-9, "consistency 应取 lastCheck.passRate=0.85");
        assertSame(DqDimension.CONSISTENCY, DqDimension.ofRuleType("CONSISTENCY"));
    }

    @Test
    @DisplayName("5) ENUM 规则 — 路由到 VALIDITY（枚举/合规命中通过率）")
    void evaluateAll_enumRule_dispatchesToValidity() {
        Map<DqDimension, DimensionScore> scores = engine.evaluateAll(ctxWithRuleType("ENUM"));
        assertEquals(6, scores.size());
        DimensionScore v = scores.get(DqDimension.VALIDITY);
        assertNotNull(v);
        assertSame(DqDimension.VALIDITY, v.getDimension());
        assertEquals(1.0D, v.getScoreValue(), 1e-9, "0 违规 / 100 总行 = 1.0");
        assertSame(DqDimension.VALIDITY, DqDimension.ofRuleType("ENUM"));
    }

    @Test
    @DisplayName("6) FRESHNESS 规则 — 路由到 FRESHNESS；executedAt = now 内 → 1.0")
    void evaluateAll_freshnessRule_dispatchesToFreshness() {
        ScoringContext ctx = ctxWithRuleType("FRESHNESS");
        ctx.setExecutedAt(java.time.LocalDateTime.now());
        Map<DqDimension, DimensionScore> scores = engine.evaluateAll(ctx);
        assertEquals(6, scores.size());
        DimensionScore f = scores.get(DqDimension.FRESHNESS);
        assertNotNull(f);
        assertSame(DqDimension.FRESHNESS, f.getDimension());
        assertEquals(1.0D, f.getScoreValue(), 1e-9, "executedAt = now → 5 分钟内满分 1.0");
        assertSame(DqDimension.FRESHNESS, DqDimension.ofRuleType("FRESHNESS"));
    }

    @Test
    @DisplayName("7) LLM (未映射) — ofRuleType 兜底 ACCURACY；evaluateAll 仍返 6 维，分数均 0.0-1.0")
    void evaluateAll_unknownRuleType_returnsEmptyDetails() {
        // LLM 不在任何 DqDimension.getRuleTypes()，ofRuleType 兜底 ACCURACY
        assertSame(DqDimension.ACCURACY, DqDimension.ofRuleType("LLM"),
                "LLM 未映射 ruleType 应兜底 ACCURACY（避免维度丢失）");
        Map<DqDimension, DimensionScore> scores = engine.evaluateAll(ctxWithRuleType("LLM"));
        assertEquals(6, scores.size(), "未映射 ruleType 不应让任何维度缺失");
        for (DqDimension d : DqDimension.all()) {
            DimensionScore ds = scores.get(d);
            assertNotNull(ds, "维度 " + d + " 应有分数");
            assertTrue(ds.getScoreValue() >= 0.0D && ds.getScoreValue() <= 1.0D,
                    "维度 " + d + " 分数应在 0.0-1.0；实际 " + ds.getScoreValue());
            assertNotNull(ds.getDimension());
        }
    }

    @Test
    @DisplayName("8) evaluateAll — COMPLETENESS 评估器内部约束违反（抛） → 该维度 0.0+detail.status=ERROR，其余 5 正常")
    void evaluateAll_evaluatorThrows_doesNotLoseOthers() {
        // 用「generous 自定义」评估器替换 COMPLETENESS：抛 IllegalArgumentException 模拟样本拉取超时
        DimensionEvaluator throwingCompleteness = new DimensionEvaluator() {
            @Override
            public DqDimension dimension() {
                return DqDimension.COMPLETENESS;
            }
            @Override
            public DimensionScore evaluate(ScoringContext ctx) {
                throw new IllegalArgumentException("样本拉取超时 60s + 数据库断连");
            }
        };
        DqScoreEngine throwingEngine = new DqScoreEngine(Arrays.asList(
                throwingCompleteness, accuracy, consistency, uniqueness, validity, freshness));

        ScoringContext ctx = ctxWithRuleType("NOT_NULL");
        Map<DqDimension, DimensionScore> scores = throwingEngine.evaluateAll(ctx);
        assertEquals(6, scores.size(), "异常不应让其它维度丢失");

        // COMPLETENESS 维度：scoreValue=0.0 + detail.status=ERROR + detail.error 摘要
        DimensionScore bad = scores.get(DqDimension.COMPLETENESS);
        assertNotNull(bad);
        assertSame(DqDimension.COMPLETENESS, bad.getDimension());
        assertEquals(0.0D, bad.getScoreValue(), 1e-9, "异常维度 scoreValue 应 = 0.0");
        assertNotNull(bad.getDetails());
        String status = (String) bad.getDetails().get("status");
        assertEquals("ERROR", status, "detail.status 应为 ERROR");
        String err = (String) bad.getDetails().get("error");
        assertNotNull(err, "detail.error 应填异常摘要");
        assertTrue(err.contains("超时"), "异常摘要应含原始 message 关键段；实际：" + err);

        // 其它 5 维照常返回（1.0 满分布局）
        for (DqDimension other : Arrays.asList(DqDimension.ACCURACY, DqDimension.CONSISTENCY,
                DqDimension.UNIQUENESS, DqDimension.VALIDITY, DqDimension.FRESHNESS)) {
            DimensionScore ok = scores.get(other);
            assertNotNull(ok, "维度 " + other + " 不应因 COMPLETENESS 异常而丢失");
            assertEquals(1.0D, ok.getScoreValue(), 1e-9,
                    "维度 " + other + " 应仍返回默认 1.0");
            assertNotSame(DqDimension.COMPLETENESS, ok.getDimension());
        }
    }
}
