package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.error.FCScenarioErrorCode;
import com.chinacreator.gzcm.common.ontology.CaliberDslContract;

import java.util.List;
import java.util.Map;

/**
 * 口径公式 DSL 四关预检未通过（#146ac 附录A 第 31 条 {@code POST /api/v1/workspace/calibers/validate}，
 * 契约错误码列 = 400；HTTP 400 / {@code CALIBER_DSL_INVALID}）。
 *
 * <p>判定权在属主（本体域 DSL 四关单源），本面只搬运结构化违反项，<b>不自建第二套 DSL 判定</b>。
 * 违反项原样来自属主应答的 gate/code/symbol/line/column/offset 键面；属主侧的人读 message
 * 已在信封内，本面不重新拼装文案以免两套口径漂移。</p>
 *
 * <p><b>两种属主形态（#146ad 补齐）</b>：属主 validate 面把结构化违反项放在 data 段，
 * 而写入/审批咽喉（{@code POST /{code}/versions}、{@code .../approve}）的四关拒绝只回
 * 400 信封 + 机读码 {@code ECOS-ONTO-07x}、<b>data 段缺位</b>（违反项在其人读 message 文本内）。
 * 后者按 {@link #CaliberFormulaInvalidException(String, String)} 承流：状态行同为 400，
 * 但把「哪一关拒绝」取自营签名（机读、可复验），并显式登记符号定位无载体 ——
 * <b>禁</b>把这种形态折成 503 属主不可得（那是将客户端公式错误报成引擎故障）。</p>
 */
public class CaliberFormulaInvalidException extends WorkspaceDomainException {

    private static final List<String> DSL_GATES = List.of(
            CaliberDslContract.GATE_V1_SYNTAX,
            CaliberDslContract.GATE_V2_REFERENCE,
            CaliberDslContract.GATE_V3_DIMENSION,
            CaliberDslContract.GATE_V4_CYCLE);

    public CaliberFormulaInvalidException(List<Map<String, Object>> violations, String target) {
        super(400, FCScenarioErrorCode.CODE_CALIBER_DSL_INVALID, "口径公式 DSL 四关未通过（违一即 400，不落库）");
        putDetail("violations", violations == null ? List.of() : violations);
        putDetail("gates", DSL_GATES);
        if (target != null && !target.isBlank()) {
            putDetail("target", target);
        }
    }

    /**
     * 属主 400 拒绝信封、data 段缺位形态：闸名取属主机读码，符号定位显式登记无载体。
     *
     * @param ownerErrorCode 属主信封 errorCode（{@code ECOS-ONTO-070/071/072/073}）
     * @param rejectedGate   由该码映射出的四关闸名；映射不上为 {@code null}（不猜一关顶替）
     */
    public CaliberFormulaInvalidException(String ownerErrorCode, String rejectedGate) {
        super(400, FCScenarioErrorCode.CODE_CALIBER_DSL_INVALID, "口径公式 DSL 四关未通过（违一即 400，不落库）");
        putDetail("violations", List.of());
        putDetail("gates", DSL_GATES);
        putDetail("ownerErrorCode", ownerErrorCode);
        putDetail("rejectedGate", rejectedGate);
        putDetail("rejectedGateApplicable", rejectedGate != null);
        putDetail("symbolLocation", null);
        putDetail("symbolLocationApplicable", false);
        putDetail("symbolLocationMissingReason",
                "属主写入/审批咽喉的 400 信封 data 段缺位，逐条违反项只在其人读 message 文本内"
                        + "（D-B4-59 属主侧裸回族在册欠账）⇒ 符号定位无结构化载体，闸名取 errorCode 机读码");
    }
}
