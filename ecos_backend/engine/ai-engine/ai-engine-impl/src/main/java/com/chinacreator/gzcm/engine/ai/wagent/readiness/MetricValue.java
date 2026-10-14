package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单层探测回的一个度量值（详细设计-10 F10-08 算法树「返回度量」枝的载体；
 * V231 {@code measured_json} 的内存形态）。
 *
 * <p><b>三态而非两态</b>：{@link Kind#ABSENT} 是「没测到」，与"测到 0"语义完全不同。
 * 度量缺失 ⇒ 检查项 {@code UNKNOWN}（F10-08 硬规则②「探测异常 ⇒ UNKNOWN，禁 fail-open」），
 * 禁把缺失折叠成 0 再交给阈值比较（{@code 0 < 阈值} 会把"没数据"洗成"数据不达标"，
 * 也会让 {@code onto.conflict_count} 的 0 值与"未探测"混为一谈）。</p>
 *
 * <p><b>类型由检查项声明而不是由 JSON 猜</b>：NUMBER 走 {@link BigDecimal}（金额/比率铁律：
 * 禁 {@code double} 承载，详设-10 §10.5.2 数字一致性），BOOLEAN 走装箱 {@code Boolean}
 * （{@code null} 即 ABSENT，禁"非真即假"折叠）。</p>
 */
public record MetricValue(Kind kind, BigDecimal number, Boolean bool, Map<String, Object> evidence) {

    /** 度量形态。 */
    public enum Kind { NUMBER, BOOLEAN, ABSENT }

    public MetricValue {
        if (kind == null) {
            throw new IllegalArgumentException("MetricValue.kind 必填（三态之一，禁空形态）");
        }
        if (kind == Kind.NUMBER && number == null) {
            throw new IllegalArgumentException("NUMBER 度量必须带 number 值（缺值请用 absent()）");
        }
        if (kind == Kind.BOOLEAN && bool == null) {
            throw new IllegalArgumentException("BOOLEAN 度量必须带 bool 值（缺值请用 absent()）");
        }
        if (kind == Kind.ABSENT && (number != null || bool != null)) {
            throw new IllegalArgumentException("ABSENT 度量不得携带任何数值（禁半缺态冒充实测）");
        }
        evidence = evidence == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
    }

    /** 数值度量（覆盖率、天数、样本量、冲突数）。 */
    public static MetricValue number(BigDecimal value) {
        return new MetricValue(Kind.NUMBER, value, null, Map.of());
    }

    /** 数值度量 + 取证线索（落 {@code measured_json}，禁无痕度量）。 */
    public static MetricValue number(BigDecimal value, Map<String, Object> evidence) {
        return new MetricValue(Kind.NUMBER, value, null, evidence);
    }

    /** 布尔度量（口径是否已定义、CM/回测是否就绪）。 */
    public static MetricValue bool(Boolean value) {
        return value == null
                ? new MetricValue(Kind.ABSENT, null, null, Map.of())
                : new MetricValue(Kind.BOOLEAN, null, value, Map.of());
    }

    /** 布尔度量 + 取证线索。 */
    public static MetricValue bool(Boolean value, Map<String, Object> evidence) {
        return value == null
                ? new MetricValue(Kind.ABSENT, null, null, evidence)
                : new MetricValue(Kind.BOOLEAN, null, value, evidence);
    }

    /** 未测到（探测未回/度量端点未落地/响应缺字段）。 */
    public static MetricValue absent() {
        return new MetricValue(Kind.ABSENT, null, null, Map.of());
    }

    /** 未测到 + 原因（原因进 {@code measured_json}，缺项可解释）。 */
    public static MetricValue absent(String reason) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("reason", reason == null ? "metric-absent" : reason);
        return new MetricValue(Kind.ABSENT, null, null, ev);
    }

    /** 是否可用（ABSENT ⇒ 不可用，检查项只能记 UNKNOWN）。 */
    public boolean available() {
        return kind != Kind.ABSENT;
    }
}
