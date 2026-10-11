package com.chinacreator.gzcm.engine.cognitive.api.outcome;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 更新建议候选引用（《认知域与智能域补充详细设计 v1.0》§9.2 步骤 6 / §9.3 {@code updateCandidates[]}）。
 *
 * <h3>只存引用，禁存正文（DDL V267 {@code update_candidates_text} 列约束）</h3>
 * §9.2 步骤 6 明确：调整假设、更新证据、补充案例、重新评估模型、修改数据规则
 * <b>全部是 Candidate/Proposal</b>。故本类只承载 {@code candidateType} + {@code candidateRef}
 * + {@code status}，禁在评估记录里内联候选正文，更禁据此直接改写生产资产
 * （SUP-OUT-01：单次偏差只产候选，生产写次数为 0）。
 *
 * <h3>候选落点</h3>
 * 认知资产类候选落 cognitive-engine 提案流（§8.4）；数据规则类候选落
 * data-engine {@code /api/v1/data/governance/candidates}（§7.2）；本体类候选落
 * ontology-engine {@code /api/v1/ontology/candidates}（§7.3）。三者都走
 * §7.4 统一候选状态机，{@code GENERATED/VALIDATED} 永不等于 {@code APPROVED}。
 */
@Data
public class UpdateCandidateRef {

    /** 候选类型：调整假设。 */
    public static final String TYPE_ADJUST_HYPOTHESIS = "ADJUST_HYPOTHESIS";
    /** 候选类型：更新证据。 */
    public static final String TYPE_UPDATE_EVIDENCE = "UPDATE_EVIDENCE";
    /** 候选类型：补充案例。 */
    public static final String TYPE_ADD_CASE = "ADD_CASE";
    /** 候选类型：重新评估模型。 */
    public static final String TYPE_REEVALUATE_MODEL = "REEVALUATE_MODEL";
    /** 候选类型：修改数据规则。 */
    public static final String TYPE_MODIFY_DQ_RULE = "MODIFY_DQ_RULE";

    /** §9.2 步骤 6 五类候选词表（单源本类）。 */
    public static final List<String> CANDIDATE_TYPES = List.of(
            TYPE_ADJUST_HYPOTHESIS, TYPE_UPDATE_EVIDENCE, TYPE_ADD_CASE,
            TYPE_REEVALUATE_MODEL, TYPE_MODIFY_DQ_RULE);

    /** 候选类型（CANDIDATE_TYPES 之一）。 */
    private String candidateType;

    /** 候选稳定引用（指向所属引擎候选行的 ID；必填，禁空引用）。 */
    private String candidateRef;

    /** 候选当前状态（§7.4 词表；本类只镜像状态，不推进状态机）。 */
    private String status;

    /** 触发该候选的偏差依据（metricRef 列表；可空 = 非指标驱动）。 */
    private List<String> basisMetricRefs = new ArrayList<>();

    /**
     * 契约校验：返回问题列表（空 = 合法）。
     * 类型必须在五类词表内（禁自造），引用必填。
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (candidateType == null || !CANDIDATE_TYPES.contains(candidateType.trim())) {
            problems.add("candidateType not in " + CANDIDATE_TYPES + ": " + candidateType);
        }
        if (candidateRef == null || candidateRef.isBlank()) {
            problems.add("candidateRef (只存引用，禁空引用)");
        }
        return problems;
    }
}
