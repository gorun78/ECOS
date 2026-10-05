package com.chinacreator.gzcm.engine.cognitive2.fc;

import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.BalanceFailed;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.Candidate;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.CellInput;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.RunOutput;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.SixElement;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分册09 §九 · 三级平衡断言 + 幂等重跑（W223/C205 与 W235/C217，F09-07）。
 *
 * <p>验收标识：</p>
 * <ul>
 *   <li>{@code mvn -Dtest=ForecastRunReconcileTest#threeLevelBalanceHoldsExactly}</li>
 *   <li>{@code mvn -Dtest=ForecastRunReconcileTest#rerunProducesZeroDiff}</li>
 * </ul>
 */
class ForecastRunReconcileTest {

    private static final SixElement SIX = SixElement.of(
            "run-a", "R6.1", "v1", Instant.parse("2026-12-31T23:59:59Z"),
            "snap-1", "f1");

    @Test
    @DisplayName("§九 · 三级平衡精确相等：STAGE 叶子求和 = DEPARTMENT 聚合 = TOTAL 聚合")
    void threeLevelBalanceHoldsExactly() {
        String period = "2026-12";
        // 建两 STAGE 叶子（同部门 D1）+ 1 DEPARTMENT 聚合 + 1 TOTAL 聚合
        List<CellInput> cells = List.of(
                stageLeaf("M1", period, "P1", "D1", "S1", "1000.00"),
                stageLeaf("M1", period, "P2", "D1", "S1", "500.50"),
                stageLeaf("M1", period, "P3", "D2", "S1", "250.25"),
                deptLeaf("M1", period, "D1", "1500.50"),   // 1000.00+500.50
                deptLeaf("M1", period, "D2", "250.25"),
                totalLeaf("M1", period, "1750.75")          // 1500.50+250.25 or 1000+500.50+250.25
        );
        RunOutput out = FcRunCalc.calculate(SIX, cells, "sh", "oh", "f1");
        // DEPARTMENT 聚合应精确等于其下属 STAGE 求和（C217 精确相等，非约等于）
        assertEquals(0, out.deptSums().get("D1").compareTo(new BigDecimal("1500.50")),
                "D1 = 1000.00+500.50, 实际 " + out.deptSums().get("D1"));
        assertEquals(0, out.deptSums().get("D2").compareTo(new BigDecimal("250.25")),
                "D2 = 250.25, 实际 " + out.deptSums().get("D2"));
        // STAGE 叶子求和（跨部门全部叶）= 1000+500.50+250.25 = 1750.75
        assertEquals(0, out.stageSums().get("S1").compareTo(new BigDecimal("1750.75")),
                "STAGE S1 = 1750.75, 实际 " + out.stageSums().get("S1"));
        // 若把 DEPARTMENT 数字改偏差 1 分，应触发 BalanceFailed
        List<CellInput> broken = List.of(
                stageLeaf("M1", period, "P1", "D1", "S1", "1000.00"),
                stageLeaf("M1", period, "P2", "D1", "S1", "500.50"),
                deptLeaf("M1", period, "D1", "1500.00"),    // 少了 0.50
                totalLeaf("M1", period, "1500.00")
        );
        assertThrows(BalanceFailed.class,
                () -> FcRunCalc.calculate(SIX, broken, "sh", "oh", "f1"),
                "DEPARTMENT 与 STAGE 之和差 0.50 应 throw BalanceFailed");
    }

    @Test
    @DisplayName("§九 · 同输入重跑 diff=0（幂等 R-61/R-59 契约）")
    void rerunProducesZeroDiff() {
        String period = "2026-10";
        List<CellInput> cells = List.of(
                stageLeaf("M1", period, "P1", "D1", "S1", "1000.00"),
                stageLeaf("M1", period, "P2", "D1", "S1", "499.99"),
                deptLeaf("M1", period, "D1", "1499.99"),
                totalLeaf("M1", period, "1499.99"));
        RunOutput a = FcRunCalc.calculate(SIX, cells, "sh", "oh", "f1");
        RunOutput b = FcRunCalc.calculate(SIX, cells, "sh", "oh", "f1");
        assertEquals(a.total(), b.total());
        assertEquals(a.stageSums(), b.stageSums());
        assertEquals(a.deptSums(), b.deptSums());
        assertEquals(a.runKey(), b.runKey());
        assertEquals(a.cells().size(), b.cells().size());
        for (int i = 0; i < a.cells().size(); i++) {
            assertEquals(0, a.cells().get(i).amountP50().compareTo(b.cells().get(i).amountP50()));
            assertEquals(0, a.cells().get(i).amountP10().compareTo(b.cells().get(i).amountP10()));
            assertEquals(0, a.cells().get(i).amountP90().compareTo(b.cells().get(i).amountP90()));
        }
        // 与 FcRunState latestByRunKey 语义对接：runKey 稳定命中即可复用（§3.2）
        assertNotNull(a.runKey());
        assertTrue(a.cells().size() == 4);
    }

    // ────────────────────────────────────────── helpers ──────────────────────────────────────────

    private static CellInput stageLeaf(String m, String p, String proj, String dept, String stage, String amt) {
        return new CellInput(m, p, "STAGE", proj, dept, stage,
                Map.of("act", candidate(amt)));
    }
    private static CellInput deptLeaf(String m, String p, String dept, String amt) {
        // DEPARTMENT 粒度：projectId/stage = null，dept 承载
        return new CellInput(m, p, "DEPARTMENT", null, dept, null,
                Map.of("comp", candidate(amt)));
    }
    private static CellInput totalLeaf(String m, String p, String amt) {
        return new CellInput(m, p, "TOTAL", null, null, null,
                Map.of("comp", candidate(amt)));
    }
    private static Candidate candidate(String amt) {
        return new Candidate(SourceType.ACTUAL, new BigDecimal(amt), Map.of("src", "fixture"));
    }
}
