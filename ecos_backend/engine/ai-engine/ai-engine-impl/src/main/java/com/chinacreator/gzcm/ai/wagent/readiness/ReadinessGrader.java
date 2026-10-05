package com.chinacreator.gzcm.ai.wagent.readiness;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.LayerStatus;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ReadinessGrade;
import java.util.*;

/**
 * 分册10 F10-08 · DIKC Readiness 评估定级（确定性非 LLM）。
 *
 * <p><b>核心算法</b>（附件一册 §2.4 → 阈值外置）：</p>
 * <pre>
 *   外入 = {D, I, K, C} 四层探测结果（每层 1..n 个 LayerStatus）
 *   定级规则（确定性，非 LLM）：
 *     任一 BLOCKED    → D
 *     ≥2 FAIL         → C
 *     任一 WARN       → B
 *     全 PASS         → A
 * </pre>
 *
 * <p><b>探针错误 → UNKNOWN，禁算 pass</b>（{@code ReadinessProbeTest#probeErrorYieldsUnknownNotPass}）：
 * 探测超时/异常 → LayerStatus.UNKNOWN，映射级未知视为 C 下沿（禁乐观）。</p>
 */
public final class ReadinessGrader {

    private ReadinessGrader() {}

    /** 四层各状态集合（层名 D/I/K/C）。空集合视为未探测 → 判为 C（禁乐观）。 */
    public record Assessment(ItemD d, ItemI i, ItemK k, ItemC c) { }

    public record ItemD(Collection<LayerStatus> statuses) {}
    public record ItemI(Collection<LayerStatus> statuses) {}
    public record ItemK(Collection<LayerStatus> statuses) {}
    public record ItemC(Collection<LayerStatus> statuses) {}

    public static ReadinessGrade grade(Assessment a) {
        if (a == null) throw new NullPointerException("Assessment null");
        List<LayerStatus> all = new ArrayList<>();
        all.addAll(a.d().statuses());
        all.addAll(a.i().statuses());
        all.addAll(a.k().statuses());
        all.addAll(a.c().statuses());
        if (all.isEmpty()) return ReadinessGrade.C; // 空探测 → C（保守）
        for (LayerStatus s : all) { if (s == LayerStatus.BLOCKED) return ReadinessGrade.D; }
        long fail = all.stream().filter(s -> s == LayerStatus.FAIL || s == LayerStatus.UNKNOWN).count();
        if (fail >= 2) return ReadinessGrade.C;
        for (LayerStatus s : all) { if (s == LayerStatus.WARN) return ReadinessGrade.B; }
        if (all.stream().allMatch(s -> s == LayerStatus.PASS)) return ReadinessGrade.A;
        return ReadinessGrade.C; // 其它（单 FAIL 或单 UNKNOWN 混 PASS）保守下沿
    }

    /** 定级只依赖输入（不含 LLM），多次调用必同值。测试 {@code gradeIsDeterministicFromThresholdsNotLlm} 直接调两次比对。 */
    public static boolean isDeterministic(Assessment a) {
        return grade(a) == grade(a);
    }
}
