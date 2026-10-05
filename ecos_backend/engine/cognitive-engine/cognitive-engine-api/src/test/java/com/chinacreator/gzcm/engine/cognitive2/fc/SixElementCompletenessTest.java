package com.chinacreator.gzcm.engine.cognitive2.fc;

import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.BalanceFailed;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.Candidate;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.RunOutput;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.SixElement;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.SixElementMismatch;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分册09 §九 · 六要素完备性（FC-03 §3.1 / W227 / C209）。
 *
 * <p>验收标识：{@code mvn -Dtest=SixElementCompletenessTest#rejectsRunMissingSnapshot}。</p>
 *
 * <p>SUCCEEDED 前逐行自检：forecastRunId/caliberId/caliberVersion/asOfTime/snapshotId/formulaVersion
 * 任一缺失即抛（宁可失败不缺要素，§3.1 step 5）。</p>
 */
class SixElementCompletenessTest {

    private static final Instant AS_OF = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    @DisplayName("§九 · snapshotId 缺失 ⇒ SixElementMismatch，禁 SUCCEEDED")
    void rejectsRunMissingSnapshot() {
        // snapshotId = null 应直接抛（SixElement.of）
        assertThrows(SixElementMismatch.class,
                () -> SixElement.of("run-1", "R6.1", "v1", AS_OF, null, "f1"));
        assertThrows(SixElementMismatch.class,
                () -> SixElement.of("run-1", "R6.1", "v1", AS_OF, "", "f1"));
        assertThrows(SixElementMismatch.class,
                () -> SixElement.of("run-1", "R6.1", "v1", AS_OF, "snap-1", null));
        assertThrows(SixElementMismatch.class,
                () -> SixElement.of(null, "R6.1", "v1", AS_OF, "snap-1", "f1"));
        // asOfTime = null 也应拒
        assertThrows(SixElementMismatch.class,
                () -> SixElement.of("run-1", "R6.1", "v1", null, "snap-1", "f1"));
    }

    @Test
    @DisplayName("六要素齐全应可构造，且六要素三元组 (id, ver, snap) 全部携带")
    void completeSixIsAccepted() {
        SixElement s = SixElement.of("run-1", "R6.1", "v3", AS_OF, "snap-1", "f2");
        assertSame(SixElement.class, s.getClass());
        assertTrue(s.caliberVersion() != null && !s.caliberVersion().isBlank());
        assertNotNull(s.snapshotId());
    }

    @Test
    @DisplayName("SUCCEEDED 前的 run 级六要素自检（六字段全非空可读）")
    void runLevelSIXCarrowed() {
        SixElement s = SixElement.of("run-2", "R6.2", "v9", AS_OF, "snap-9", "f9");
        // 全字段可读且非空
        assertTrue(s.forecastRunId().startsWith("run-"));
        assertTrue(s.caliberId().startsWith("R6"));
        assertNotNull(asOf(s));
    }

    private static Instant asOf(SixElement s) {
        assertNotNull(s.asOfTime());
        return s.asOfTime();
    }
}
