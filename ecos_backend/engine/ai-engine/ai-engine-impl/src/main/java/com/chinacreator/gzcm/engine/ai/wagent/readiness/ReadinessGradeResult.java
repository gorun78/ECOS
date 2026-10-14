package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessCheckStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessGrade;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessLayer;

import java.util.List;
import java.util.Map;

/**
 * 定级结果（V230 {@code ecos_wagent_readiness} 的 {@code grade} + 四个 {@code layer_*} 实列
 * + V231 非 pass 明细的内存形态；F10-08 输出面）。
 *
 * <p>四个 DIKC 层<b>恒有值</b>：某层无任何检查项 ⇒ 该层档位为 UNKNOWN（该层证据缺席本身就是缺项），
 * 与 V230 {@code layer_* VARCHAR(10)} 容 {@code unknown} 的列宽口径一致。</p>
 *
 * <p>{@code matchedRule} 是命中规则的 ASCII 标识（如 {@code BLOCKER_PRESENT->D}），
 * 接线落库时应随 {@code layer_summary_json} 快照留痕——定级可复盘、可解释，
 * 禁只留一个字母让下游猜依据。</p>
 */
public record ReadinessGradeResult(ReadinessGrade grade,
                                   Map<ReadinessLayer, ReadinessCheckStatus> layerStatus,
                                   Map<ReadinessCheckStatus, Integer> statusCounts,
                                   List<ReadinessCheck> missingItems,
                                   String matchedRule) {

    public ReadinessGradeResult {
        layerStatus = Map.copyOf(layerStatus);
        statusCounts = Map.copyOf(statusCounts);
        missingItems = List.copyOf(missingItems);
    }
}
