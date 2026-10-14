package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 一个检查项在本档（profile）下的阈值集合（详细设计-10 §10.2.8 {@code config_group='wagent.readiness.threshold'}
 * 的读取结果形态；V231 {@code threshold_json} 的内存来源）。
 *
 * <p><b>键形态单源 = V265 seed 字面</b>：{@code wagent.readiness.{profile}.{engine}.{metric}}
 * （如 {@code wagent.readiness.forecast.data.check_coverage}）。{@code ThresholdPolicy} 先取
 * profile 专属行、缺失回落 {@code default} 行（seed 注释"profile 命中优先级高于 default"），
 * 两者皆无 ⇒ 本集合 {@link #present()} 为 false，检查项记 UNKNOWN 而非"用代码默认值"
 * ——设计原文「阈值不得硬编码进代码」，故本类<b>没有任何数值字面量</b>。</p>
 *
 * <p>{@code resolvedFrom} 逐条留痕（forecast / default），落 {@code threshold_json} 使评估可复盘：
 * 同一检查项在不同档位取到不同界，是定级差异的唯一解释来源。</p>
 */
public record ThresholdSet(String profileId, Map<String, ThresholdValue> byFragment) {

    /** 单个阈值：原始字符串 + 命中来源档位。 */
    public record ThresholdValue(String fragment, String effectiveKey, String raw, String resolvedFrom) {

        public ThresholdValue {
            if (fragment == null || fragment.isBlank()) {
                throw new IllegalArgumentException("ThresholdValue.fragment 必填");
            }
        }

        /** 严格解析为数值：非数 / 空 ⇒ empty（禁宽容成 0）。 */
        public Optional<BigDecimal> number() {
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new BigDecimal(raw.trim()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        /** 严格解析为布尔：只认 seed 的 true/false 字面（大小写不敏感），其它 ⇒ empty。 */
        public Optional<Boolean> bool() {
            if (raw == null) {
                return Optional.empty();
            }
            String v = raw.trim();
            if ("true".equalsIgnoreCase(v)) {
                return Optional.of(Boolean.TRUE);
            }
            if ("false".equalsIgnoreCase(v)) {
                return Optional.of(Boolean.FALSE);
            }
            return Optional.empty();
        }
    }

    public ThresholdSet {
        byFragment = Collections.unmodifiableMap(new LinkedHashMap<>(byFragment));
    }

    /** 全部阈值缺失（配置组未 seed 或 sysman 未装配）。 */
    public static ThresholdSet unset(String profileId) {
        return new ThresholdSet(profileId, Map.of());
    }

    /** 本集合是否至少命中一条阈值。 */
    public boolean present() {
        return !byFragment.isEmpty();
    }

    public Optional<ThresholdValue> value(String fragment) {
        return Optional.ofNullable(byFragment.get(fragment));
    }

    /** 声明的阈值片段是否全部命中（任一条缺失即不可判档，禁"部分阈值 + 部分默认"混算）。 */
    public boolean hasAll(List<String> fragments) {
        for (String fragment : fragments) {
            if (!byFragment.containsKey(fragment)) {
                return false;
            }
        }
        return true;
    }

    /** 缺失片段清单（UNKNOWN 原因点名用）。 */
    public List<String> missing(List<String> fragments) {
        List<String> miss = new ArrayList<>();
        for (String fragment : fragments) {
            if (!byFragment.containsKey(fragment)) {
                miss.add(fragment);
            }
        }
        return Collections.unmodifiableList(miss);
    }

    /** {@code threshold_json} 快照（片段 → {key, value, resolved_from}，禁只落数值丢来源）。 */
    public Map<String, Object> asSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        byFragment.forEach((fragment, tv) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", tv.effectiveKey());
            row.put("value", tv.raw());
            row.put("resolved_from", tv.resolvedFrom());
            snapshot.put(fragment, row);
        });
        return Collections.unmodifiableMap(snapshot);
    }
}
