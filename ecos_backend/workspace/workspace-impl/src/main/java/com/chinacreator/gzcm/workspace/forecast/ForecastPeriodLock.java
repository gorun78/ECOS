package com.chinacreator.gzcm.workspace.forecast;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 预测 run 期间锁只读判定（H-5 / C19 关账面）：关账期间禁重算/重试/复制。
 *
 * <p><b>复用而非第二套</b>：判定口径与 data-engine {@code OpenPeriodsPeriodLock} 同源，
 * 均读同一份运维配置单源 {@code ecos.data.facts.closed-periods}（逗号 CSV {@code YYYY-MM}）。
 * 此处仅<b>只读镜像</b>同一属性做入口 fail-loud 拦截，不新建表、不自算账期、不持久化关账态
 * （关账写入的属主与真源在 facts 侧；workspace 只据同一 CSV 拒绝命中关账期的 run 编排）。</p>
 */
@Component
public class ForecastPeriodLock {

    private final Set<String> closedPeriods;

    public ForecastPeriodLock(@Value("${ecos.data.facts.closed-periods:}") String closedPeriodsCsv) {
        if (closedPeriodsCsv == null || closedPeriodsCsv.isBlank()) {
            this.closedPeriods = Collections.emptySet();
            return;
        }
        this.closedPeriods = List.of(closedPeriodsCsv.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /** 与 {@code OpenPeriodsPeriodLock.isClosed} 同语义：period 命中关账 CSV 即视为已关账。 */
    public boolean isClosed(String period) {
        return period != null && closedPeriods.contains(period);
    }

    /**
     * 从 run 的 asOfTime 抽取所属关账判定期（{@code YYYY-MM}）。
     * asOfTime 允许 {@code 2025-08-31T23:59:59} / {@code 2025-08-31} / {@code 2025-08}；截取前 7 位月键。
     */
    public static String periodKeyOf(String asOfTime) {
        if (asOfTime == null) {
            return null;
        }
        String s = asOfTime.trim();
        return s.length() >= 7 ? s.substring(0, 7) : (s.isEmpty() ? null : s);
    }

    /** 测试/诊断用。 */
    public Set<String> closedPeriods() {
        return Collections.unmodifiableSet(closedPeriods);
    }
}
