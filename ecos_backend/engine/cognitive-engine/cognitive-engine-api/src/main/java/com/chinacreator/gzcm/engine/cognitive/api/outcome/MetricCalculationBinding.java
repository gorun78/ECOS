package com.chinacreator.gzcm.engine.cognitive.api.outcome;

import com.chinacreator.gzcm.engine.cognitive.api.context.CalculationResultRef;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单指标的确定性计算产物绑定（《认知域与智能域补充详细设计 v1.0》§9.2 步骤 1/3、§9.3 末段铁律）。
 *
 * <p>为什么必须显式绑定：{@code metricRefs[]} 只说"要评哪些指标"，
 * {@link CalculationResultRef} 只说"某个确定性计算的产物在哪"，两者之间<b>没有</b>可推导的对应关系。
 * 按下标位置隐式配对一旦上游顺序变化就会把 A 指标的预测值配到 B 指标的实际值上，
 * 算出一个语法合法、业务全错的误差 —— 这类错误无法从结果反查，属禁捷径（附录 B）。</p>
 *
 * <p>任一侧引用缺失是<b>合法输入</b>（表示该侧无值），由服务层落成
 * {@code applicable=false} + 明确不适用原因，禁补 0（0 是合法业务值，与"取不到"必须可分，DG-6）。</p>
 */
@Data
public class MetricCalculationBinding {

    /** 指标引用（必填；须出现在请求 {@code metricRefs[]} 内，否则该绑定被忽略并登记 warning）。 */
    private String metricRef;

    /** 预测侧计算产物引用（可空 = 无预测值）。 */
    private CalculationResultRef forecastRef;

    /** 实际侧计算产物引用（可空 = 无实际值）。 */
    private CalculationResultRef actualRef;

    /**
     * 契约校验：返回问题列表（空 = 合法）。
     * 两侧引用同时缺失 ⇒ 该绑定无任何输入，属结构性错误（不是"取不到值"，是"根本没给"）。
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (metricRef == null || metricRef.isBlank()) {
            problems.add("metricRef");
        }
        if (forecastRef == null && actualRef == null) {
            problems.add("forecastRef/actualRef (两侧计算产物引用不得同时缺失)");
        }
        if (forecastRef != null) {
            forecastRef.validate().forEach(f -> problems.add("forecastRef." + f));
        }
        if (actualRef != null) {
            actualRef.validate().forEach(f -> problems.add("actualRef." + f));
        }
        return problems;
    }
}
