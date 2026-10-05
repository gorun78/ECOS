package com.chinacreator.gzcm.ai.wagent.readiness;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.LayerStatus;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ReadinessGrade;
import com.chinacreator.gzcm.ai.wagent.readiness.ReadinessGrader.Assessment;
import com.chinacreator.gzcm.ai.wagent.readiness.ReadinessGrader.ItemC;
import com.chinacreator.gzcm.ai.wagent.readiness.ReadinessGrader.ItemD;
import com.chinacreator.gzcm.ai.wagent.readiness.ReadinessGrader.ItemI;
import com.chinacreator.gzcm.ai.wagent.readiness.ReadinessGrader.ItemK;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-08 DIKC Readiness 定级离线单测（确定性，非 LLM）：
 * 全 PASS=A / 任一 WARN=B / ≥2(FAIL|UNKNOWN)=C / 任一 BLOCKED=D / 空探测=C。
 */
class ReadinessGraderTest {

    private static ItemD d(LayerStatus... s) { return new ItemD(List.of(s)); }
    private static ItemI i(LayerStatus... s) { return new ItemI(List.of(s)); }
    private static ItemK k(LayerStatus... s) { return new ItemK(List.of(s)); }
    private static ItemC c(LayerStatus... s) { return new ItemC(List.of(s)); }
    private static Assessment assessment(ItemD d, ItemI i, ItemK k, ItemC c) {
        return new Assessment(d, i, k, c);
    }

    /** F10-08：全 PASS ⇒ A。 */
    @Test
    void allPassGradesA() {
        Assessment a = assessment(d(LayerStatus.PASS), i(LayerStatus.PASS),
                k(LayerStatus.PASS), c(LayerStatus.PASS));
        assertEquals(ReadinessGrade.A, ReadinessGrader.grade(a));
    }

    /** F10-08：任一 WARN（无 FAIL/UNKNOWN/BLOCKED）⇒ B。 */
    @Test
    void anyWarnGradesB() {
        Assessment a = assessment(d(LayerStatus.PASS), i(LayerStatus.WARN),
                k(LayerStatus.PASS), c(LayerStatus.PASS));
        assertEquals(ReadinessGrade.B, ReadinessGrader.grade(a));
    }

    /** F10-08：≥2 个 FAIL（或 UNKNOWN）⇒ C（未 BLOCKED 之上、WARN 之下的下沿）。 */
    @Test
    void twoFiguresFailOrUnknownGradesC() {
        // 两个 FAIL。
        Assessment twoFail = assessment(d(LayerStatus.FAIL, LayerStatus.PASS),
                i(LayerStatus.FAIL), k(LayerStatus.PASS), c(LayerStatus.PASS));
        assertEquals(ReadinessGrade.C, ReadinessGrader.grade(twoFail),
                "2 个 FAIL ≥ 2 ⇒ C");

        // 两个 UNKNOWN（探针错误）同样计 C。
        Assessment twoUnknown = assessment(d(LayerStatus.UNKNOWN, LayerStatus.PASS),
                i(LayerStatus.UNKNOWN), k(LayerStatus.PASS), c(LayerStatus.PASS));
        assertEquals(ReadinessGrade.C, ReadinessGrader.grade(twoUnknown),
                "2 个 UNKNOWN ≥ 2 ⇒ C（禁算 pass）");
    }

    /** F10-08：任一 BLOCKED ⇒ D（最高级失败）。 */
    @Test
    void anyBlockedGradesD() {
        Assessment a = assessment(d(LayerStatus.PASS), i(LayerStatus.BLOCKED),
                k(LayerStatus.PASS), c(LayerStatus.PASS));
        assertEquals(ReadinessGrade.D, ReadinessGrader.grade(a));
    }

    /** F10-08：空探测（四层皆空）⇒ C（禁乐观）。 */
    @Test
    void emptyAssessmentGradesC() {
        Assessment a = assessment(d(), i(), k(), c());
        assertEquals(ReadinessGrade.C, ReadinessGrader.grade(a));
    }
}
