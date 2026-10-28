package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识抽取报告 — 详设-07 §7.3.2 第 12 行
 * （{@code GET /api/v1/knowledge/extract/runs/{taskId}} 与 {@code POST /extract/dry-run} 共用出参）。
 *
 * <p>契约键面（详设原句）：{@code {created,updated,issues[],c3Skipped,vectorDegraded,watermarkAdvanced}}；
 * 其余键为 additive（均可由 runtime-task 结果或审计行直接读出，不合成、不估值）。</p>
 *
 * <p><b>刻意不加 {@code @JsonInclude(NON_NULL)}</b>：{@code vectorDegraded} 的语义是
 * 「本次抽取是否存在向量退化」——抽取链（映射驱动实例化）全程不触碰向量索引，<b>无该信号源</b>，
 * 故出 {@code null} 而<b>不</b>写死 {@code false}（禁假值），并用
 * {@code vectorDegradedApplicable=false} 明示「本报告不适用该维度」。若给 VO 加 NON_NULL，
 * 该契约键会在序列化时整体消失，反而让消费方分不清「不适用」与「字段未实现」。</p>
 *
 * <p>{@code watermarkAdvanced}：水位线是否推进 = 报告是否回填了非空 {@code nextWatermark}
 * （dry-run 分支在抽取服务内即不回填）。{@code c3Skipped} 只在 C3 校验被显式关闭时为 true
 * （REQ-KB-01 禁静默关闭）。</p>
 */
@Data
public class KnowledgeExtractRunReportVO {

    /** runtime-task 任务 ID（dry-run 同步预览时也为本次 jobId 生成的任务标识）。 */
    private String taskId;

    /** 业务抽取 jobId（{@code KBK1S-} 前缀）。 */
    private String jobId;

    /** 任务状态：SUBMITTED / RUNNING / SUCCEEDED / FAILED / UNKNOWN。 */
    private String status;

    /** 进度百分比（0~100；runtime-task 未给出时为 null）。 */
    private Integer progress;

    /** runtime-task 状态说明。 */
    private String statusMessage;

    /** 失败原因（仅 FAILED 面）。 */
    private String errorMessage;

    /** 是否已可从 runtime-task 结果读出抽取报告（false = 任务未完成或结果未落）。 */
    private Boolean reportAvailable;

    /** 本批新建实例节点数（契约键 {@code created}）。 */
    private Integer created;

    /** 本批更新实例节点数（契约键 {@code updated}）。 */
    private Integer updated;

    /** 结构化告警/失败明细（C1~C4 校验结论与元数据可用性；上游上限 50 条）。 */
    private List<IssueVO> issues = new ArrayList<>();

    /** C3 属性完整性校验被显式关闭（REQ-KB-01 禁静默关闭）。 */
    private Boolean c3Skipped;

    /** 向量退化（抽取链无该信号源 ⇒ 恒 null，见类注释）。 */
    private Boolean vectorDegraded;

    /** {@code vectorDegraded} 是否适用本报告（抽取链固定 false）。 */
    private Boolean vectorDegradedApplicable;

    /** 水位线是否推进（= {@code nextWatermark} 非空）。 */
    private Boolean watermarkAdvanced;

    /** 本次收敛后的水位（dry-run / 无数据为 null）。 */
    private String nextWatermark;

    /** 抽取模式：FULL / INCREMENTAL。 */
    private String mode;

    /** 是否干跑。 */
    private Boolean dryRun;

    /** 本次抽取耗时（毫秒）。 */
    private Long durationMs;

    /** 参与抽取的本体版本（{@code ontologyId@version}）。 */
    private List<String> ontologies = new ArrayList<>();

    /** 参与抽取的本体数量。 */
    private Integer ontologyCount;

    /** 实际处理的映射实体数（含被拒绝/跳过）。 */
    private Integer entityCount;

    /** 跳过计数（无主键行 / C2 不合法边 / materialized=false 等）。 */
    private Integer nodeSkipped;

    /** 落库的实例边数。 */
    private Integer edgeCreated;

    /** C4 映射有效性失败被拒绝实例化的实体数。 */
    private Integer invalidMappings;

    /** 是否因页上限截断（true 表示还有数据未抽）。 */
    private Boolean truncated;

    /** 本次实抽行数。 */
    private Integer rowsRead;

    /** runtime-task 任务开始时刻（epoch millis）。 */
    private Long startedAt;

    /** runtime-task 任务结束时刻（epoch millis）。 */
    private Long finishedAt;

    /** 抽取报告中的一条问题明细（可追溯到实体与校验码）。 */
    @Data
    public static class IssueVO {

        /** 关联实体 code（非实体级问题为 null）。 */
        private String entityCode;

        /** 问题码：NO_SNAPSHOT / INVALID_MAPPING / C2_SKIP / MAPPING_UNAVAILABLE 等。 */
        private String code;

        /** 人类可读描述。 */
        private String message;
    }
}
