package com.chinacreator.gzcm.engine.cognitive.api.backtest;

/**
 * 偏差方向词表（详设-09 v1.2 §十一 H-4 / W225「回测三指标」之一）。
 *
 * <p><b>词表单源 = 本枚举</b>（禁在 Service/Calculator 内散落字面量，与
 * {@code OutcomeEvaluationStatus} / {@code OutcomeEvaluationBlocker} 同纪律）。</p>
 *
 * <h3>三值来自 PRD 冻结原文（不是本批新造）</h3>
 * PRD-09 §5.2「回测五指标（公式与守卫原文不变）」第 ③ 行：
 * {@code 偏差方向 | Σ(预测−实际) 符号 → OVER_FORECAST/UNDER_FORECAST/BALANCED}；
 * V217 的 {@code ecos_fc_backtest.bias_direction VARCHAR(16)} 列注释写作简写
 * {@code OVER/UNDER/NEUTRAL} —— 本枚举取 PRD 全名（三值均可落 VARCHAR(16)），
 * 短名在 {@link #from(String)} 里作兼容别名解析，两套写法不再各表一态。
 *
 * <h3>第四值 NOT_DETERMINABLE 是"只增"</h3>
 * PRD 三值描述的是"有数据时"的结论；缺预测/缺实际时<b>禁</b>冒充 {@code BALANCED}
 * （DG-6 空集与故障严格可分、X-44 禁伪 200），故新增一个显式不可判态并计入分母，
 * 不改变 PRD 三值的判定口径。该新增已登记为待卷 05 复确认项（词表只增不改）。
 *
 * <p>判定为<b>确定性</b>算术：带符号差额 {@code signedDelta = predicted - actual} 取符号（PRD 原文），
 * 仅当配置单源给出 {@code on_target_band} 死区时才用死区收窄 BALANCED
 * （H-4 要点 3：阈值只来自 sysman {@code config_group}，禁前端传阈值 R-66 ①、禁代码硬编码缺省）。
 * <b>LLM 零参与</b>。</p>
 */
public enum DeviationDirection {

    /** 预测高于实际：{@code signedDelta > +band}（无死区配置时即 {@code signedDelta > 0}，高估）。 */
    OVER_FORECAST("forecast above actual"),

    /** 预测低于实际：{@code signedDelta < -band}（无死区配置时即 {@code signedDelta < 0}，低估）。 */
    UNDER_FORECAST("forecast below actual"),

    /** 命中目标：无死区配置 ⇒ 净差恰为 0；配置 {@code on_target_band} 后为 {@code |signedDelta| <= band}。 */
    BALANCED("no net deviation (exact zero, or within configured on-target band)"),

    /**
     * 不可判定（fail-loud 态）：该样本预测或实际缺失 —— 差额无定义。
     * <p>禁以 {@link #BALANCED} 冒充"没偏差"（DG-6：空集与故障严格可分；X-44 禁伪成功）。</p>
     */
    NOT_DETERMINABLE("direction not determinable: predicted or actual unavailable");

    private final String meaning;

    DeviationDirection(String meaning) {
        this.meaning = meaning;
    }

    public String meaning() {
        return meaning;
    }

    /**
     * 宽松解析：兼容 V217 列注释的短名（{@code OVER}/{@code UNDER}/{@code NEUTRAL}），
     * 词表外值 / 空值 ⇒ {@link #NOT_DETERMINABLE}（禁静默当作命中）。
     */
    public static DeviationDirection from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NOT_DETERMINABLE;
        }
        String v = raw.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (v) {
            case "OVER", "OVER_FORECAST" -> OVER_FORECAST;
            case "UNDER", "UNDER_FORECAST" -> UNDER_FORECAST;
            case "BALANCED", "NEUTRAL", "ON_TARGET" -> BALANCED;
            default -> NOT_DETERMINABLE;
        };
    }
}
