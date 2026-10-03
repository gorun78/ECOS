package com.chinacreator.gzcm.engine.data.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionScore;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;
import com.chinacreator.gzcm.engine.data.quality.scoring.service.DqScoreEngine;

/**
 * F02-09 (P1, D-20/W61) —— 评分上下文不完整 → 全维降级为 {@code UNKNOWN}。
 *
 * <p>规格：当 {@code buildContext} 目标表/最近 check 行不可达时，
 * 调用方置 {@link ScoringContext#isContextIncomplete()} = true；
 * {@link DqScoreEngine#evaluateAll} 必须把所有 6 维降级为
 * {@link DimensionScore#unknown}（哨兵分 {@code UNKNOWN_SENTINEL} = -1.0），
 * <b>不得</b>当作 {@code totalRows=0 → 1.0 高分通过} 或 {@code 0.0 未通过}。
 *
 * <p>纯单测：不走 Spring、不连库。空评估器列表即可触发路径
 * （降级分支在 evaluator 循环之前 return，与评估器是否注册无关）。
 */
@DisplayName("F02-09 上下文不完整 → 全维 UNKNOWN")
class IncompleteContextUnknownScoreTest {

    private DqScoreEngine engine() {
        // 空评估器列表即可（contextIncomplete 分支在 evaluator 循环前直接返回）
        return new DqScoreEngine(List.of());
    }

    @Test
    @DisplayName("contextIncomplete=true → 6 维全部 UNKNOWN 哨兵（-1.0），status=UNKNOWN")
    void incompleteContextYieldsAllUnknown() {
        ScoringContext ctx = new ScoringContext();
        ctx.setRuleId("R-1");
        ctx.setScopeType("TABLE");
        ctx.setScopeId("pub.ods_orders");
        ctx.setContextIncomplete(true); // 目标表/最近 check 不可达
        // 关键反向断言：即便声称 totalRows=0 也不允许 1.0 通过
        ctx.setTotalRows(0);

        Map<DqDimension, DimensionScore> result = engine().evaluateAll(ctx);

        assertEquals(DqDimension.all().size(), result.size(),
                "必须返回全部 6 维（雷达图固定 6 格）");
        for (DqDimension d : DqDimension.all()) {
            DimensionScore s = result.get(d);
            org.junit.jupiter.api.Assertions.assertNotNull(s, "维度 " + d + " 缺失");
            assertEquals(DimensionScore.UNKNOWN_SENTINEL, s.getScoreValue(), 1e-9,
                    "维度 " + d + " 应 UNKNOWN 哨兵(-1.0)，实际 " + s.getScoreValue()
                            + "（若为 1.0=高分通过 / 0.0=未通过，均为 F02-09 违规）");
            Object status = s.getDetails() == null ? null : s.getDetails().get("status");
            assertEquals("UNKNOWN", status,
                    "维度 " + d + " details.status 应为 UNKNOWN，实际 " + status);
        }
    }

    @Test
    @DisplayName("Sentinel -1.0 落在合法区间 [0,1] 外，可区分\"未通过\"与\"不可评估\"")
    void unknownSentinelIsOutsideLegalRange() {
        DimensionScore u = DimensionScore.unknown(DqDimension.COMPLETENESS);
        double v = u.getScoreValue();
        assertTrue(v < 0.0 || v > 1.0,
                "UNKNOWN 哨兵必须落在 [0,1] 合法区间外以便消费端区分；实际 " + v);
    }
}
