package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 图谱实例抽取结构化报告 VO（PMO 批次 B3-2 / 方案 §6.1 K1 映射驱动确定性抽取）。
 *
 * <p>强类型出参（禁 {@code Map<String,Object>} 作接口出入参），落地到
 * {@code ecos_knowledge.kg_sync_log.report}（V136 新增 JSONB 列），供前端图谱构建 Tab
 * 展示真实抽取结果（nodeCreated / nodeUpdated / nodeSkipped / edgeCreated / issues）。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code ontologyId} — 本次抽取范围（{@code ALL} 或指定本体 ID）；</li>
 *   <li>{@code ontologies} — 参与抽取的本体版本列表（{@code ontologyId@version}）；</li>
 *   <li>{@code nodeCreated} / {@code nodeUpdated} — 实例节点 upsert 的新建 / 更新数；</li>
 *   <li>{@code nodeSkipped} — 跳过计数（无主键行 / C2 不合法边 / materialized=false 等）；</li>
 *   <li>{@code edgeCreated} — 本次落库的实例边数（映射目标为本体关系 code 时生成）；</li>
 *   <li>{@code invalidMappings} — C4 校验失败被拒绝的实体数（{@code INVALID_MAPPING}）；</li>
 *   <li>{@code issues} — 结构化告警/失败明细（C1~C4 校验结论与元数据可用性）；</li>
 *   <li>{@code nextWatermark} — 本次抽取收敛后的最后水位（供下次增量调用）；</li>
 *   <li>{@code durationMs} — 本次抽取耗时（毫秒）。</li>
 * </ul>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntityInstanceExtractionReportVO {

    /** 抽取范围：本体业务 ID 或 ALL */
    private String ontologyId;

    /** 参与抽取的本体版本列表（{@code ontologyId@version}） */
    private List<String> ontologies = new ArrayList<>();

    /** 本次抽取耗时（毫秒） */
    private long durationMs;

    /** 参与抽取的本体数量 */
    private int ontologyCount;

    /** 实际处理的映射实体数（含被拒绝/跳过） */
    private int entityCount;

    /** 新建的实例节点数（ON CONFLICT 未命中 → INSERT） */
    private int nodeCreated;

    /** 更新的实例节点数（ON CONFLICT 命中 → UPDATE） */
    private int nodeUpdated;

    /** 跳过计数（含无主键行、C2 不合法关系边、materialized=false 实体、端点缺失边等） */
    private int nodeSkipped;

    /** 落库的实例边数（端点节点均存在且 C2 合法） */
    private int edgeCreated;

    /** C4 映射有效性失败被拒绝实例化的实体数（INVALID_MAPPING） */
    private int invalidMappings;

    /** 本次抽取收敛后的最后水位（无更多数据时为 null） */
    private String nextWatermark;

    /** 抽取模式：FULL（全量，忽略水位线）/ INCREMENTAL（按水位线增量）；DRY_RUN 预览时为 FULL/INCREMENTAL 原值 */
    private String mode;

    /** 是否 dry-run 预览（true = 只统计不落库） */
    private boolean dryRun;

    /** 结构化告警/失败明细（上限保护，避免报告无界膨胀） */
    private List<IssueVO> issues = new ArrayList<>();

    /** 追加一条结构化告警明细（上限 50 条）。 */
    public void addIssue(String entityCode, String code, String message) {
        if (issues.size() < 50) {
            issues.add(new IssueVO(entityCode, code, message));
        }
    }

    /** 记录一个参与抽取的本体版本（形如 {@code ontologyId@version}）。 */
    public void addOntology(String ontologyVersion) {
        if (ontologyVersion != null && !ontologyVersion.isBlank() && !ontologies.contains(ontologyVersion)) {
            ontologies.add(ontologyVersion);
        }
    }

    /**
     * 抽取报告中的一条问题明细 — 每条可追溯到实体与校验码（C1~C4 / 元数据可用性）。
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IssueVO {

        /** 关联实体 code（非实体级问题为 null，如元数据端点不可用） */
        private final String entityCode;

        /** 问题码：UNKNOWN_ENTITY_TYPE / INVALID_MAPPING / C2_SKIP / MAPPING_UNAVAILABLE 等 */
        private final String code;

        /** 人类可读描述 */
        private final String message;
    }
}
