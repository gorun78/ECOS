package com.chinacreator.gzcm.engine.ontology.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * H-2 — 口径公式 DSL 四关校验结果（{@code POST /api/v1/ontology/calibers/validate} 出参；
 * PRD-09 v1.2 §1.2 V1~V4）。
 *
 * <p>四条校验关（违一即 400，错误信息定位到符号位置）：</p>
 * <ul>
 *   <li>{@code DSL_V1_SYNTAX} — 受限递归下降文法（PRD-09 §1.2 EBNF，函数白名单 SUM/ROUND/MIN/MAX/IF）；</li>
 *   <li>{@code DSL_V2_REFERENCE} — 引用闭合：IDENT 必须解析到指标注册表（ecos_metric_definition，R-4 唯一语义入口）
 *       指标码或业务事实字段（含 {@code IDENT[QUALIFIER]} 限定符形态）；</li>
 *   <li>{@code DSL_V3_DIMENSION} — 量纲一致（复用 MetricUnitDerivationGuard 单位体系：金额×比率=金额；禁金额+比率直加）；</li>
 *   <li>{@code DSL_V4_CYCLE} — 公式图拓扑排序无环（成环时打印环路径）。</li>
 * </ul>
 */
@Data
public class CaliberFormulaValidationVO {

    /** 全部四关通过。 */
    private boolean valid;

    /** 违反项（valid=false 时逐条给出，含 gate/错误码/符号/行列偏移定位）。 */
    private List<Violation> violations = new ArrayList<>();

    /** 解析出的公式目标（左端 IDENT）。 */
    private String target;

    /** 参与判定的指标注册表规模（可观测性：V2/V4 的语义面来源）。 */
    private int registryMetricCount;

    /** 单条违反项。 */
    @Data
    public static class Violation {
        /** DSL_V1_SYNTAX | DSL_V2_REFERENCE | DSL_V3_DIMENSION | DSL_V4_CYCLE。 */
        private String gate;
        /** ECOS-ONTO-070/071/072/073。 */
        private String code;
        /** 人读消息，内嵌 {@code 'symbol' (line L, column C, offset N)} 定位。 */
        private String message;
        /** 违规符号（定位到符号；环路径时为首个成环节点）。 */
        private String symbol;
        private Integer line;
        private Integer column;
        private Integer offset;

        public Violation() {
        }

        public Violation(String gate, String code, String message, String symbol,
                         Integer line, Integer column, Integer offset) {
            this.gate = gate;
            this.code = code;
            this.message = message;
            this.symbol = symbol;
            this.line = line;
            this.column = column;
            this.offset = offset;
        }

        @Override
        public String toString() {
            return gate + "/" + code + " " + message;
        }
    }
}
