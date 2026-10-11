package com.chinacreator.gzcm.engine.cognitive2.service.dsl;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;

/**
 * 已校验 DSL 的<b>求值期</b>语义违例（H-2b）。继承 {@link CognitiveGuardException}
 * ⇒ 冻结码 {@code ECOS-COG-422F} → 真实 HTTP 422，禁 200 + 伪造数值（X-44）。
 *
 * <p>设计依据：PRD-09 v1.2 §1.2「四关只判静态合法性」+ 卷 09 §十一 H-2
 * 「递归下降求值 + 白名单函数，禁脚本引擎」。求值期任何"取不到数 / 算不下去"
 * 都是数据缺陷，必须 fail-loud 定位到符号，禁静默补 0、禁均值兜底。</p>
 */
public class CaliberFormulaEvaluationException extends CognitiveGuardException {

    /** 求值失败原因（单源枚举，新增原因必须先补本表，禁自由字符串）。 */
    public enum Reason {
        /** 标识符在事实/指标供给里不存在。 */
        UNKNOWN_SYMBOL,
        /** {@code IDENT[QUAL]} 的维度限定在供给里无对应取值。 */
        MISSING_DIMENSION,
        /** 除数为 0（BigDecimal compareTo 判零，禁近似）。 */
        DIVISION_BY_ZERO,
        /** 非白名单函数（解析层已拦，求值层二次防御）。 */
        NON_WHITELIST_FUNCTION,
        /** 量纲不可推导（金额 + 比率直加等，与 ontology V3 同一套单位代数）。 */
        UNIT_CONFLICT,
        /** ROUND 位数非整数或越界。 */
        ROUND_SCALE_INVALID,
        /** 嵌套深度超上限（防御脏公式，禁 StackOverflowError 冒充业务错误）。 */
        NESTING_TOO_DEEP
    }

    private final Reason reason;
    private final String symbol;
    private final Integer line;
    private final Integer column;
    private final Integer offset;

    public CaliberFormulaEvaluationException(Reason reason, String message,
                                            String symbol, Integer line, Integer column, Integer offset) {
        super(CognitiveErrorCode.E422_FORMULA_EVALUATION, message, reason.name());
        this.reason = reason;
        this.symbol = symbol;
        this.line = line;
        this.column = column;
        this.offset = offset;
    }

    public Reason getReason() {
        return reason;
    }

    public String getSymbol() {
        return symbol;
    }

    public Integer getLine() {
        return line;
    }

    public Integer getColumn() {
        return column;
    }

    public Integer getOffset() {
        return offset;
    }
}
