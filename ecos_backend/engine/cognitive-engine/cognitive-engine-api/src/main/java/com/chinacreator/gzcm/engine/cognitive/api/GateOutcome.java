package com.chinacreator.gzcm.engine.cognitive.api;

import java.util.Optional;

/**
 * 发布门禁裁决三值枚举（REQ-COG-07 规格 4；V261 列 {@code gate_contract/gate_regression/gate_backtest/gate_calibration}）。
 *
 * <p>三值而非布尔（V261 列形态依据）：必须区分"未跑（PENDING）"与"跑失败（FAIL）"，
 * 否则"任一缺项 → 停留 DRAFT 且发布端点显式 4xx"不可判。
 * PUBLISHED 升格判据 = 四门禁全部 {@link #PASS}；FAIL 与 PENDING 一律拒（fail-closed，禁静默成功）。</p>
 *
 * <p>枚举单源在本类（D.2 规则 3），DDL 不建 CHECK。</p>
 */
public enum GateOutcome {

    PASS, FAIL, PENDING;

    public static Optional<GateOutcome> fromValue(String raw) {
        if (raw == null) return Optional.empty();
        String v = raw.trim().toUpperCase();
        for (GateOutcome o : values()) {
            if (o.name().equals(v)) return Optional.of(o);
        }
        return Optional.empty();
    }

    /** 存储值解析失败按最严格口径 = PENDING（fail-closed，禁默认放行）。 */
    public static GateOutcome orPending(String raw) {
        return fromValue(raw).orElse(PENDING);
    }

    /** 四类门禁名（与 V261 列名逐一对应，顺序即裁决顺序）。 */
    public static final String GATE_CONTRACT = "contract";
    public static final String GATE_REGRESSION = "regression";
    public static final String GATE_BACKTEST = "backtest";
    public static final String GATE_CALIBRATION = "calibration";

    public static java.util.List<String> gateNames() {
        return java.util.List.of(GATE_CONTRACT, GATE_REGRESSION, GATE_BACKTEST, GATE_CALIBRATION);
    }

    public static String gateColumn(String gateName) {
        return "gate_" + gateName;
    }
}
