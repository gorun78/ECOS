package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 图谱构建 dry-run 预览 VO — {@code POST /api/v1/knowledge/graph/build/preview} 出参
 * （方案 §5.3「图谱构建与 dry-run 预览」/ §附录 B graph_build Tab「dry-run + C1~C4 报告」）。
 *
 * <p>字段名与前端的 {@code GraphBuildPreview}（create/update/skip/samples）对齐；
 * 额外暴露 {@code edgeCreate / invalidMappings / issues} 以承载 C1~C4 校验结论。</p>
 *
 * <p><b>实现口径</b>：复用 B3-2 已有的契约驱动实例抽取（{@code KbEntityInstanceExtractionService}
 * 的 dry-run 分支），真实读取本体映射契约与 DW 层实例行，只统计不落库。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphBuildPreviewVO {

    /** 将新建的实例节点数 */
    private int create;

    /** 将更新的实例节点数 */
    private int update;

    /** 跳过计数（无主键行 / materialized=false / 非法边等） */
    private int skip;

    /** 将落库的实例边数 */
    private int edgeCreate;

    /** 实际处理的映射实体数 */
    private int entityCount;

    /** C4 映射有效性失败被拒绝的实体数 */
    private int invalidMappings;

    /** 抽取范围（本体 ID 或 ALL） */
    private String ontologyId;

    /** 参与抽取的本体版本列表（ontologyId@version） */
    private List<String> ontologies = new ArrayList<>();

    /** 固定 true（本端点即 dry-run） */
    private boolean dryRun = true;

    /** 预览耗时（毫秒） */
    private long durationMs;

    /** C1~C4 / 元数据可用性问题明细（上限 50 条） */
    private List<EntityInstanceExtractionReportVO.IssueVO> issues = new ArrayList<>();
}
