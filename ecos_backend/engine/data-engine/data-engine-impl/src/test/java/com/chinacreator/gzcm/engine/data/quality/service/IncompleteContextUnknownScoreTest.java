package com.chinacreator.gzcm.engine.data.quality.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-09 DQ 评分上下文：不完整上下文**不得静默回退为综合分**（W58 归属缺口）。
 *
 * <p>设计依据（详细设计-02 F02-09 验收 + M0 表格）：{@code DqScheduleServiceImpl}
 * 在目标表不可达 / 无 1h 内 {@code dq_rule_check} 行 / 上下文缺失时应显式
 * 返回 {@code INCONCLUSIVE-PARTIAL | INCONCLUSIVE-AUX | INCONCLUSIVE-SCORE-CONTEXT}
 * 之一（部分字符串），把 {@code outDTO.score = -1} / {@code level = "INCONCLUSIVE-PARTIAL"}，
 * 让出参可被上层识别为"不可胜负评分"，而不是"高分通过"假象。</p>
 *
 * <p><b>当前实现状态（2026-09-30 实查）= 缺口未闭合</b>：</p>
 * <ol>
 *   <li>{@code DqScheduleServiceImpl} 只有 {@code contextIncomplete=true}
 *       布尔位（{@code connectionConfig/sampleFailures/lastCheck}）而非 INCONCLUSIVE 常量；
 *       全仓 grep {@code INCONCLUSIVE} 零命中；</li>
 *   <li>{@code dsource/} 目录不存在（现为 {@code repository/DataResourceRepository}
 *       单接口，无 {@code lowStorage}/{@code degraded} 打标）；</li>
 *   <li>{@code DqCheckExecuteResult} 类型尚不存在；{@code DqScoreEngine}
 *       在 evaluator 缺失时 fallback {@code scoreValue=1.0}
 *       （{@code DqScoreEngine.java:86-97 EVALUATOR_MISSING 分支}）—
 *       这正是"静默回退为综合分"风险点，需 W58 补 INCONCLUSIVE 短路。</li>
 * </ol>
 *
 * <p><b>本文件的现状收口</b>：</p>
 * <ul>
 *   <li>Soft-checkpoint {@code sourceSentinelAbsent_today}：证明既有
 *       {@code contextIncomplete} 三位 marker 已存在、且 INCONCLUSIVE 常量**尚未**
 *       进入源码；需在 W58 引入 sentinel 时，本 test 应 fail 提示同步更新
 *       baseline（避免静默把缺口当作"已修"）。</li>
 *   <li>{@code @Disabled} 的完整行为验收占位（{"w58Behavior"}, behavior set）：
 *       引入 INCONCLUSIVE 后启用；断言 outDTO.score=-1 且 level=INCONCLUSIVE-PARTIAL。</li>
 * </ul>
 *
 * <p>surefire 工作目录 = data-engine-impl/，DqScheduleServiceImpl 位于
 * {@code src/main/java/com/chinacreator/gzcm/engine/data/quality/service/}。</p>
 *
 * @author ECOS Data Engine Team / W58-gap
 */
@DisplayName("F02-09 不完整上下文不静默回退综合分（W58 gap 记录 + baseline 守护）")
class IncompleteContextUnknownScoreTest {

    private static final Path IMPL_PATH = Paths.get(
            "src/main/java/com/chinacreator/gzcm/engine/data/quality/service/DqScheduleServiceImpl.java");

    /** 设计要求的 INCONCLUSIVE 部分字符串（三选一中任一命中即视为引入）。 */
    private static final String[] INCONCLUSIVE_SENTINELS = {
            "INCONCLUSIVE-PARTIAL", "INCONCLUSIVE-AUX", "INCONCLUSIVE-SCORE-CONTEXT"
    };

    private static String readImpl() {
        try {
            return Files.readString(IMPL_PATH);
        } catch (IOException e) {
            fail("DqScheduleServiceImpl.java 缺失: " + IMPL_PATH.toAbsolutePath());
            return "";
        }
    }

    @Test
    @DisplayName("Soft checkpoint: 已实现的 contextIncomplete 位保持存在；INCONCLUSIVE 常量当前尚未引入（W58 前 baseline）")
    void sourceSentinelAbsent_today() {
        String src = readImpl();

        // 已由既有 ScoringContextHydrationTest 稳定的 `contextIncomplete` marker 必须保留；
        // 否则 buildContext 会退回"空 Map = 高分通过"。
        assertTrue(src.contains("contextIncomplete"),
                "既有 F02-09 零静默假象依靠 contextIncomplete 位保持激活（见 ScoringContextHydrationTest）");
        // 确保三位 marker 都没静默消失（connCfg / sampleFailures / lastCheck）
        int hits = 0;
        hits += src.contains("connCfg.put(\"contextIncomplete\"") ? 1 : 0;
        hits += src.contains("sampleFailures.put(\"contextIncomplete\"") ? 1 : 0;
        hits += src.contains("lastCheck.put(\"contextIncomplete\"") ? 1 : 0;
        assertTrue(hits == 3,
                "三位 contextIncomplete marker 必须同时保留（此时 Incomplete 单侧置位 = 假修）; 实际 " + hits);

        // W58 完成前，INCONCLUSIVE sentinel 不得被"半引入"（部分引入 + behavior 缺失 = 更糟）
        for (String sentinel : INCONCLUSIVE_SENTINELS) {
            assertFalse(src.contains(sentinel),
                    "INCONCLUSIVE sentinel '" + sentinel + "' 引入时必须同步启用下方 @Disabled 行为测试");
        }
    }

    @Disabled("W58 gap：DqCheckExecuteResult + INCONCLUSIVE sentinel 尚未引入；等 W58 落地完成 scoring-context "
            + "hydration + 短_score=-1 出参时启用；见设计 docs/30-设计/详细设计-02-数据域 F02-09 + M0 表格.W58")
    @Test
    @DisplayName("(W58 行为) 不完整上下文 → outDTO.score=-1 且 level=INCONCLUSIVE-PARTIAL，DqScoreEngine fallback 被短路")
    void incompleteContext_doesNotSilentlyFallBack() {
        // W58 引入后此处应：
        // 1. 反射注入 fake DqRuleMapper / DqScheduleMapper / DqScoreService (返回空的
        //    Map<DqDimension, DimensionScore>, 模拟 evaluator 缺失)
        // 2. 调 DqScheduleServiceImpl runRuleBatch 或 evaluateOneRule 的公开 facade
        // 3. 断言 outDTO.score == -1 && "INCONCLUSIVE-PARTIAL".equals(outDTO.getLevel())
        // 4. 反向断言：DqScoreEngine.fontfall=1.0 (EVALUATOR_MISSING) 时，被 Incomplete 拦截层
        //    拦截，不出 1.0 假分
    }
}
