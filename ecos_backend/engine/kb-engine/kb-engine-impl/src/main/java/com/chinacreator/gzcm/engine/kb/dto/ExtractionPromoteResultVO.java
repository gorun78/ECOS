package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.List;

/**
 * 抽取实体转候选本体（PMO-50 T5）返回体。
 *
 * <p>单实体失败不中止整批（错误隔离），失败明细见 {@link #failures}。
 */
@Data
public class ExtractionPromoteResultVO {

    /** 请求提交的实体数 */
    private int requested;

    /** 成功生成候选本体提案的实体数 */
    private int created;

    /** 已生成的提案 ID 列表（ontology-engine {@code ecos_ontology_proposals.id}） */
    private List<String> proposalIds;

    /** 失败明细（格式 {@code 实体名: 原因}） */
    private List<String> failures;
}