package com.chinacreator.gzcm.engine.data.fact.period;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 默认期间锁定 — 永不关账（全开放）。
 *
 * <p>覆盖路径：属性 {@code ecos.data.facts.closed-periods} 逗号列表（运维/测试态）。
 * 例：{@code ecos.data.facts.closed-periods=2025-01,2025-02} 使得
 * {@code isClosed("2025-01") == true}。
 */
@Component
public class OpenPeriodsPeriodLock implements PeriodLock {

    private final Set<String> closedPeriods;

    public OpenPeriodsPeriodLock(
            @Value("${ecos.data.facts.closed-periods:}") String closedPeriodsCsv) {
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

    @Override
    public boolean isClosed(String period) {
        return period != null && closedPeriods.contains(period);
    }

    /** 测试/运维态诊断用。 */
    public Set<String> closedPeriods() {
        return Collections.unmodifiableSet(closedPeriods);
    }
}
