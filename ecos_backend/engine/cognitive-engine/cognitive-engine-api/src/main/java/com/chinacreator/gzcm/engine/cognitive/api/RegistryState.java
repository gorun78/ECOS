package com.chinacreator.gzcm.engine.cognitive.api;

import java.util.Optional;

/**
 * 认知模型注册表状态枚举（REQ-COG-07 规格 4；V261 列 {@code registry_state}）。
 *
 * <p>状态机：{@code DRAFT → PUBLISHED} 单向升格，四类发布门禁
 * （contract/regression/backtest/calibration）全 PASS 才可升格；
 * 存量行一律回落 DRAFT（V261 头注：门禁在库侧从无留痕，不得推定已发布）。
 * 与旧 {@code status}（active/retired 运营标记）解耦——"是否在用"≠"是否过门禁"。</p>
 *
 * <p>枚举单源在本类（D.2 规则 3），DDL 不建 CHECK。</p>
 */
public enum RegistryState {

    DRAFT, PUBLISHED;

    public static Optional<RegistryState> fromValue(String raw) {
        if (raw == null) return Optional.empty();
        String v = raw.trim().toUpperCase();
        for (RegistryState s : values()) {
            if (s.name().equals(v)) return Optional.of(s);
        }
        return Optional.empty();
    }

    /** 已发布态 = 不可变（REQ-COG-07 规格 3：内容禁 UPDATE，变更只准新建版本行）。 */
    public static boolean isImmutable(String raw) {
        return fromValue(raw).orElse(null) == PUBLISHED;
    }
}
