package com.chinacreator.gzcm.engine.cognitive2.fc;

import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.Candidate;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.Cell;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.CellInput;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.CellOverride;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.RunOutput;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.SixElement;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 分册09 §七 W221/C203 · 情景复制只改目标格（非目标格 diff=0，F09-10 附件演练 4）。
 *
 * <p>验收标识：{@code mvn -Dtest=ScenarioCopyIsolationTest#nonTargetCellsRemainIdentical}。</p>
 *
 * <p>红线：scenario-copy 只允许对 overrides 白名单命中的目标格改数；非目标格必须<b>逐字段不变</b>，
 * 不能顺带"重算"邻格（否则演算 3 的三级平衡被污染）。生产侧由 cognitive 计算 + 后端 compare 断言守线。</p>
 */
class ScenarioCopyIsolationTest {

    private static final SixElement SIX = SixElement.of(
            "run-base", "R6.1", "v1", Instant.parse("2026-10-01T00:00:00Z"),
            "snap-1", "f1");

    @Test
    @DisplayName("§七 W221/C203 · 只有目标格被覆盖，其余格 diff=0")
    void nonTargetCellsRemainIdentical() {
        List<CellInput> inputs = List.of(
                leaf("M1", "2026-10", "P1", "D1", "S1", "1000.00"),
                leaf("M1", "2026-10", "P2", "D1", "S1", "2000.00"),
                leaf("M1", "2026-10", "P3", "D2", "S1", "3000.00"));
        RunOutput base = FcRunCalc.calculate(SIX, inputs, "sh", "oh", "f1");

        // 只覆盖 P2 那格（cellKey: metric|granularity|period|projectId|departmentId|stage）
        String targetKey = "M1|STAGE|2026-10|P2|D1|S1";
        Map<String, CellOverride> overrides = new LinkedHashMap<>();
        overrides.put(targetKey, new CellOverride(new BigDecimal("2500.00"),
                Map.of("override", true, "assumption", "delay_q4")));

        List<Cell> scenario = FcRunCalc.applyOverrides(base.cells(), overrides);

        assertEquals(base.cells().size(), scenario.size(), "副本格数应与基准一致（不新增/不删格）");
        for (int i = 0; i < base.cells().size(); i++) {
            Cell b = base.cells().get(i);
            Cell s = scenario.get(i);
            if (FcRunCalc.cellKey(b).equals(targetKey)) {
                // 目标格：金额变、source 变 MANUAL_OVERRIDE
                assertNotEquals(0, b.amountP50().compareTo(s.amountP50()), "目标格金额应变");
                assertSame(SourceType.MANUAL_OVERRIDE, s.source(), "目标格来源应改 MANUAL_OVERRIDE");
            } else {
                // 非目标格：三值 + 来源 完全不变
                assertEquals(0, b.amountP10().compareTo(s.amountP10()), "非目标格 p10 应不变 @ " + FcRunCalc.cellKey(b));
                assertEquals(0, b.amountP50().compareTo(s.amountP50()), "非目标格 p50 应不变 @ " + FcRunCalc.cellKey(b));
                assertEquals(0, b.amountP90().compareTo(s.amountP90()), "非目标格 p90 应不变 @ " + FcRunCalc.cellKey(b));
                assertSame(b.source(), s.source(), "非目标格来源应不变 @ " + FcRunCalc.cellKey(b));
            }
        }
        // 目标格确实被覆盖
        Cell overridden = scenario.stream()
                .filter(c -> FcRunCalc.cellKey(c).equals(targetKey))
                .findFirst().orElseThrow();
        assertEquals(0, overridden.amountP50().compareTo(new BigDecimal("2500.00")));
    }

    @Test
    @DisplayName("空 overrides ⇒ 全格原样返回（copy 无变化 = diff 0）")
    void emptyOverridesKeepsAllCells() {
        List<CellInput> inputs = List.of(
                leaf("M1", "2026-10", "P1", "D1", "S1", "1000.00"),
                leaf("M1", "2026-10", "P2", "D1", "S1", "2000.00"));
        RunOutput base = FcRunCalc.calculate(SIX, inputs, "sh", "oh", "f1");
        List<Cell> scenario = FcRunCalc.applyOverrides(base.cells(), Map.of());
        for (int i = 0; i < base.cells().size(); i++) {
            assertEquals(0, base.cells().get(i).amountP50().compareTo(scenario.get(i).amountP50()));
            assertSame(base.cells().get(i).source(), scenario.get(i).source());
        }
    }

    private static CellInput leaf(String m, String p, String proj, String dept, String stage, String amt) {
        return new CellInput(m, p, "STAGE", proj, dept, stage,
                Map.of("act", new Candidate(SourceType.ACTUAL, new BigDecimal(amt), Map.of("src", "fixture"))));
    }
}
