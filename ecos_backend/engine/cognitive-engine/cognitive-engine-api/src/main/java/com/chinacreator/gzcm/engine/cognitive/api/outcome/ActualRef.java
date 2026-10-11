package com.chinacreator.gzcm.engine.cognitive.api.outcome;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 实际值权威引用（《认知域与智能域补充详细设计 v1.0》§9.3 {@code actualRef}）。
 *
 * <p>引用态契约（§14 / 铁律 §0.6）：实际值正文属业务域 A 层权威事实，落
 * {@code ecos_dw}/所属业务引擎；认知域<b>只持引用</b>，不复制数值正文，
 * 不落第二份实际值副本。</p>
 *
 * <p>三要素与 {@link com.chinacreator.gzcm.engine.cognitive.api.context.ContextSourceRef}
 * 同纪律（id/version/asOfTime 缺一即非法引用，SUP-CTX-01 同族）。</p>
 */
@Data
public class ActualRef {

    /** 实际值资产 ID（必填）。 */
    private String assetId;

    /** 资产版本（必填；未关账/无版本 ⇒ §9.4 ACTUALS_NOT_CLOSED）。 */
    private String version;

    /** 取值时点（ISO-8601 UTC 字符串，必填；如 2027-09-10T00:00:00Z）。 */
    private String asOfTime;

    /** 关账标记（可空 = 未声明；未声明按未关账处理，禁默认当作已关账）。 */
    private Boolean closed;

    /** 该实际值的口径 ID（可空；与预测口径比对，不一致 ⇒ CALIBER_MISMATCH）。 */
    private String caliberId;

    /**
     * 契约校验：返回缺失字段名列表（空 = 合法）。
     * 三要素缺一即视为非法引用，禁降级为"当作没有实际值"继续评估（§9.1）。
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (isBlank(assetId)) problems.add("assetId");
        if (isBlank(version)) problems.add("version");
        if (isBlank(asOfTime)) problems.add("asOfTime");
        return problems;
    }

    /** 是否显式声明已关账（null/false 一律视为未关账，禁乐观默认）。 */
    public boolean isDeclaredClosed() {
        return Boolean.TRUE.equals(closed);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
