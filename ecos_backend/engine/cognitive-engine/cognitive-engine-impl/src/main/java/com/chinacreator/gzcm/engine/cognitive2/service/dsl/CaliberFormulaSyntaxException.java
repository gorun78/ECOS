package com.chinacreator.gzcm.engine.cognitive2.service.dsl;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;

/**
 * DSL V1 语法违例（H-2b）。继承 {@link CognitiveGuardException} ⇒ 由
 * {@code CognitiveErrorAdvice} 直接渲染真实 HTTP 400 + 冻结码
 * {@code ECOS-COG-400F}，禁 200 兜底（X-44）。
 *
 * <p>消息形态与 ontology 侧 {@code CaliberDslParser.SyntaxError} 同格式
 * （{@code DSL_V1 syntax: ... — unexpected 'symbol' (line L, column C, offset N)}），
 * 便于向导两侧对齐排错。</p>
 */
public class CaliberFormulaSyntaxException extends CognitiveGuardException {

    private final String symbol;
    private final int line;
    private final int column;
    private final int offset;

    public CaliberFormulaSyntaxException(String message, String symbol, int line, int column, int offset) {
        super(CognitiveErrorCode.E400_FORMULA_SYNTAX, message, "formula-dsl V1 语法");
        this.symbol = symbol;
        this.line = line;
        this.column = column;
        this.offset = offset;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getOffset() {
        return offset;
    }
}
