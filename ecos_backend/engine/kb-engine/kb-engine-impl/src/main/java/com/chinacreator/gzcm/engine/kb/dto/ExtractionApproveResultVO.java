package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽取候选审核入图结果 VO — {@code POST /api/v1/knowledge/extract/candidates/{fileId}/approve}
 * 出参（方案 §5.3：候选审核入图）。
 *
 * <p>强类型化既有的匿名 Map 返回（{@code id/status/counts/rejectedReasons}），
 * 逻辑仍复用 {@code KnowledgeExtractionService#approve}（写 graph_node/graph_edge + 规则入库）。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtractionApproveResultVO {

    /** 抽取草稿 ID（= fileId） */
    private String id;

    /** 审核后状态（APPROVED） */
    private String status;

    /** 写入的规则数 */
    private int rules;

    /** 写入的实体节点数 */
    private int entities;

    /** 写入的关系边数 */
    private int links;

    /** 被拒绝/跳过的原因明细（如规则重复、写入失败） */
    private List<String> rejectedReasons = new ArrayList<>();
}
