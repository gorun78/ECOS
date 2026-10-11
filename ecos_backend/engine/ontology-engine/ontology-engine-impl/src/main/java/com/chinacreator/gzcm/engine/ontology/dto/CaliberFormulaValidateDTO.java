package com.chinacreator.gzcm.engine.ontology.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * H-2 — 口径公式 DSL 校验入参（{@code POST /api/v1/ontology/calibers/validate}，
 * 只校验不落库；PRD-09 v1.2 §1.4 / 详设-09 v1.2 §十一 H-2）。
 */
@Data
public class CaliberFormulaValidateDTO {

    /** 目标指标编码（口径定义本体 code）；缺省取公式左端 IDENT。 */
    private String code;

    /** 公式文本，文法 {@code formula := IDENT '=' expr}（PRD-09 §1.2 EBNF）。必填。 */
    private String formula;

    /** 本口径私有业务事实字段（追加到内建登记册 {@code CaliberFactFieldRegistry} 之上）。 */
    private List<String> factFields;

    /** 声明单位：符号（指标码或事实字段）→ AMOUNT/RATE/MONTH/PERSON_COUNT/HOURS/SCALAR；优先级最高（V3）。 */
    private Map<String, String> units;

    /** 关联公式图（指标码 → 公式文本），供 V4 拓扑判环与 V3 复合单位推导；与指标注册表表达式合并。 */
    private Map<String, String> relatedFormulas;
}
