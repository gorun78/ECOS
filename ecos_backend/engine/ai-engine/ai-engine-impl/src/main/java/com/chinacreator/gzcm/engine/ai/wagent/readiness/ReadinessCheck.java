package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessCheckStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessLayer;

/**
 * 单项就绪检查结论（详细设计-10 F10-08 定级算法的唯一输入面；V231
 * {@code ecos_wagent_readiness_item} 的 {@code layer/check_code/status} 三实列形态）。
 *
 * <p><b>为什么只有三列</b>：F10-08 边界原文「grade 与 status 由服务端计算，LLM 仅可填
 * {@code description/impact} 文本列」⇒ 定级输入面<b>结构上不含任何文本/度量值</b>：
 * 度量值（coverage、staleness_days 等）在 {@code ThresholdPolicy} 侧与阈值比完就已折算成
 * 档位，定级器只见档位。这样 LLM 或前端伪造的文案无法进入定级链，
 * {@code ReadinessGraderTest#gradeIsDeterministicFromThresholdsNotLlm} 直接反射断言本记录
 * 的字段集合。</p>
 *
 * <p><b>status 缺席 ⇒ UNKNOWN（fail-closed）</b>：行存在但档位为空，属"未探测/探测未回"态，
 * 依 F10-08「失败 = UNKNOWN 且计入缺项，禁算 pass」归一为 UNKNOWN，<b>不抛异常也不冒充 pass</b>。</p>
 */
public record ReadinessCheck(ReadinessLayer layer, String checkCode, ReadinessCheckStatus status) {

    public ReadinessCheck {
        if (layer == null) {
            throw new IllegalArgumentException("ReadinessCheck.layer 必填（DIKC 四层枚举 D/I/K/C）");
        }
        if (checkCode == null || checkCode.isBlank()) {
            throw new IllegalArgumentException("ReadinessCheck.checkCode 必填（缺码无法点名缺项）");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 为 null 请经 of() 构造（null 归一为 UNKNOWN）");
        }
    }

    /** 归一构造：status 为 null ⇒ UNKNOWN（禁把"没结论"冒充实测档位）。 */
    public static ReadinessCheck of(ReadinessLayer layer, String checkCode, ReadinessCheckStatus status) {
        return new ReadinessCheck(layer, checkCode, status == null ? ReadinessCheckStatus.UNKNOWN : status);
    }

    /** 是否计入缺项（非 pass 即缺项，含 UNKNOWN——F10-08「UNKNOWN 且计入缺项」）。 */
    public boolean missing() {
        return status != ReadinessCheckStatus.PASS;
    }
}
