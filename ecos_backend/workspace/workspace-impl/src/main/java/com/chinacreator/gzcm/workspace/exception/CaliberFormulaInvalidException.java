package com.chinacreator.gzcm.workspace.exception;

import java.util.List;
import java.util.Map;

/**
 * 口径公式 DSL 四关预检未通过（#146ac 附录A 第 31 条 {@code POST /api/v1/workspace/calibers/validate}，
 * 契约错误码列 = 400；HTTP 400 / {@code CALIBER_DSL_INVALID}）。
 *
 * <p>判定权在属主（本体域 DSL 四关单源），本面只搬运结构化违反项，<b>不自建第二套 DSL 判定</b>。
 * 违反项原样来自属主应答的 gate/code/symbol/line/column/offset 键面；属主侧的人读 message
 * 已在信封内，本面不重新拼装文案以免两套口径漂移。</p>
 */
public class CaliberFormulaInvalidException extends WorkspaceDomainException {

    public CaliberFormulaInvalidException(List<Map<String, Object>> violations, String target) {
        super(400, "CALIBER_DSL_INVALID", "口径公式 DSL 四关未通过（违一即 400，不落库）");
        putDetail("violations", violations == null ? List.of() : violations);
        putDetail("gates", List.of("DSL_V1_SYNTAX", "DSL_V2_REFERENCE", "DSL_V3_DIMENSION", "DSL_V4_CYCLE"));
        if (target != null && !target.isBlank()) {
            putDetail("target", target);
        }
    }
}
