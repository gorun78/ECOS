package com.chinacreator.gzcm.ai.wagent.readiness;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums;
import java.util.*;

/**
 * 分册10 F10-09 · 缺口·解锁值·补齐循环（refillRound ≤ 3）。
 *
 * <p>{@code suggested_action(type=fetch|task|confirm|approve, target, params)} +
 * {@code unlock_value(grade_from → grade_to)}；{@code type=task} 经
 * {@link GapTaskPort#submit}（生产链路 = runtime-task {@code ITaskManagementService}，
 * 本域不建任务表）。超 3 轮补齐未达标 ⇒ 输出缺口清单并转 {@code awaiting_input} 挂起。</p>
 */
public final class ReadinessGapService {

    private ReadinessGapService() {}

    /** runtime-task 唯一建任务口（生产 = ITaskManagementService；离线测试注入 stub）。 */
    public interface GapTaskPort {
        String submit(String type, String target, Map<String, Object> params);
    }

    public record SuggestedAction(String type, String target, Map<String, Object> params) {
        public static SuggestedAction fetch(String target)  { return new SuggestedAction("fetch", target, Map.of()); }
        public static SuggestedAction task(String target, Map<String, String> params) { return new SuggestedAction("task", target, Map.copyOf(params)); }
        public static SuggestedAction confirm(String target) { return new SuggestedAction("confirm", target, Map.of()); }
        public static SuggestedAction approve(String target) { return new SuggestedAction("approve", target, Map.of()); }
    }

    public record Gap(String layer, String severity, String description, String impact,
                      SuggestedAction suggestedAction, WAgentEnums.ReadinessGrade unlockFrom,
                      WAgentEnums.ReadinessGrade unlockTo) {}

    public record RefillOutcome(List<Gap> gaps, int refillRound, boolean capped, Collection<String> submittedTaskIds) {}

    /** 阈值 3：上限在此处硬编码，单源。 */
    public static final int MAX_REFILL = 3;

    /** 由评估中的 WARN/FAIL/BLOCKED/UNKNOWN 项产出缺口 + 解锁值。无 gap ⇒ 空清单。 */
    public static List<Gap> extractGaps(Map<LayerKey, WAgentEnums.LayerStatus> probeResults) {
        List<Gap> out = new ArrayList<>();
        if (probeResults == null) return out;
        for (Map.Entry<LayerKey, WAgentEnums.LayerStatus> e : probeResults.entrySet()) {
            WAgentEnums.LayerStatus s = e.getValue();
            if (s == WAgentEnums.LayerStatus.PASS) continue;
            String severity = switch (s) {
                case BLOCKED -> "blocker";
                case FAIL, UNKNOWN -> "warn";
                case WARN -> "minor";
                default -> "info";
            };
            WAgentEnums.ReadinessGrade from = ReadinessGrader.grade(singletonAssessment(e.getKey(), s));
            WAgentEnums.ReadinessGrade to = WAgentEnums.ReadinessGrade.A; // 补齐到全 pass = A（理想解锁值）
            SuggestedAction a = switch (s) {
                case BLOCKED, FAIL -> SuggestedAction.task(e.getKey().code, Map.of("layer", String.valueOf(e.getKey().layer()), "reason", severity));
                case UNKNOWN -> SuggestedAction.fetch(e.getKey().code);
                case WARN -> SuggestedAction.confirm(e.getKey().code);
                default -> SuggestedAction.confirm(e.getKey().code);
            };
            out.add(new Gap(String.valueOf(e.getKey().layer()), severity, "gap:" + e.getKey().code, null, a, from, to));
        }
        return out;
    }

    /** 补齐循环：refillRound 累计 ≥3 时 cap → 返回挂起清单，不再有 gap 被再次派发。 */
    public static RefillOutcome refill(List<Gap> gaps, int currentRound, GapTaskPort taskPort) {
        if (currentRound >= MAX_REFILL) {
            return new RefillOutcome(gaps, currentRound, true, List.of());
        }
        List<String> submitted = new ArrayList<>();
        for (Gap g : gaps) {
            if ("task".equals(g.suggestedAction().type()) && taskPort != null) {
                submitted.add(taskPort.submit(g.suggestedAction().type(), g.suggestedAction().target(),
                        g.suggestedAction().params()));
            }
        }
        return new RefillOutcome(gaps, currentRound + 1, false, submitted);
    }

    private static ReadinessGrader.Assessment singletonAssessment(LayerKey layer, WAgentEnums.LayerStatus s) {
        switch (layer.layer()) {
            case 'D': return new ReadinessGrader.Assessment(new ReadinessGrader.ItemD(List.of(s)),
                    new ReadinessGrader.ItemI(List.of(WAgentEnums.LayerStatus.PASS)),
                    new ReadinessGrader.ItemK(List.of(WAgentEnums.LayerStatus.PASS)),
                    new ReadinessGrader.ItemC(List.of(WAgentEnums.LayerStatus.PASS)));
            case 'I': return new ReadinessGrader.Assessment(new ReadinessGrader.ItemD(List.of(WAgentEnums.LayerStatus.PASS)),
                    new ReadinessGrader.ItemI(List.of(s)),
                    new ReadinessGrader.ItemK(List.of(WAgentEnums.LayerStatus.PASS)),
                    new ReadinessGrader.ItemC(List.of(WAgentEnums.LayerStatus.PASS)));
            case 'K': return new ReadinessGrader.Assessment(new ReadinessGrader.ItemD(List.of(WAgentEnums.LayerStatus.PASS)),
                    new ReadinessGrader.ItemI(List.of(WAgentEnums.LayerStatus.PASS)),
                    new ReadinessGrader.ItemK(List.of(s)),
                    new ReadinessGrader.ItemC(List.of(WAgentEnums.LayerStatus.PASS)));
            default: return new ReadinessGrader.Assessment(new ReadinessGrader.ItemD(List.of(WAgentEnums.LayerStatus.PASS)),
                    new ReadinessGrader.ItemI(List.of(WAgentEnums.LayerStatus.PASS)),
                    new ReadinessGrader.ItemK(List.of(WAgentEnums.LayerStatus.PASS)),
                    new ReadinessGrader.ItemC(List.of(s)));
        }
    }

    public record LayerKey(char layer, String code) {
        public static final LayerKey D_COV = new LayerKey('D', "data.check_coverage");
        public static final LayerKey I_CAL = new LayerKey('I', "onto.check_metric");
        public static final LayerKey K_SMP = new LayerKey('K', "know.select_profiles");
        public static final LayerKey C_CM  = new LayerKey('C', "cog.model_availability");
    }
}
