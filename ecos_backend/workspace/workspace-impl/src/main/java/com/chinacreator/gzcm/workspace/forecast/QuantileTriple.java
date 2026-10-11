package com.chinacreator.gzcm.workspace.forecast;

import java.math.BigDecimal;

/**
 * 区间三值分位（P10/P50/P90）—— 全存全展，禁插值合成（R-62 ①）。
 *
 * <p>三值均来自上游真值（cognitive 计算落 {@code *_result_json} / 登记表 {@code amount_p10/p50/p90}）。
 * 任一值为 {@code null} 表示该分位<b>无真值</b>，编排面原样透传 null，
 * 严禁由另两值线性内插 / 取均值 / 兜底生成（X-44/X-50 假阳性红线）。金额一律 BigDecimal。</p>
 */
public final class QuantileTriple {

    private final BigDecimal p10;
    private final BigDecimal p50;
    private final BigDecimal p90;

    public QuantileTriple(BigDecimal p10, BigDecimal p50, BigDecimal p90) {
        this.p10 = p10;
        this.p50 = p50;
        this.p90 = p90;
    }

    public BigDecimal getP10() {
        return p10;
    }

    public BigDecimal getP50() {
        return p50;
    }

    public BigDecimal getP90() {
        return p90;
    }
}
