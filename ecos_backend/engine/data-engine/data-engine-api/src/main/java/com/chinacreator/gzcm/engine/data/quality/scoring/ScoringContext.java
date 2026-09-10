package com.chinacreator.gzcm.engine.data.quality.scoring;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Data;

/**
 * 维度评估上下文 DTO — 单个评估器的入参载体（PMO-48-B T8 SPI）。
 *
 * <p>关键约定：</p>
 * <ul>
 *   <li>评估器<b>只算+返回</b>，不写库（落库在 {@link com.chinacreator.gzcm.engine.data.quality.DqScoreService#recomputeForAsset}）</li>
 *   <li>Phase 2 简化：评估器直接读 {@code dq_rule_check.passed / total_rows / failed_rows}，不拉样本</li>
 *   <li>IStorageAdapter 拉样本属于 Phase 3 监控调度器建表时再放开；本阶段所有评估器仅读既有 dq_rule 与 dq_rule_check 字段</li>
 *   <li>样本 0 行/NULL → 1.0（空表视完整/无重复/无错误），评估器内部需自行判空</li>
 * </ul>
 *
 * <p>字段语义：</p>
 * <ul>
 *   <li>{@code ruleId / scopeType / scopeId} — 评估上下文定位</li>
 *   <li>{@code parameters} — 规则参数 JSON 反序列化的 Map（如 min/max/regex/enum 列表）</li>
 *   <li>{@code connectionConfig} — 数据源连接配置引用（不存敏感字段，敏感字段在落库前由 {@code DqSecurityService} mask）</li>
 *   <li>{@code sampleFailures / totalRows / failedRows} — 来自 {@code ecos_dq.dq_rule_check} 行的诊断位</li>
 *   <li>{@code executedAt} — 评估触发时间（用于 FRESHNESS）</li>
 * </ul>
 *
 * @author PMO-48-B T8
 */
@Data
public class ScoringContext {

    /** 触发的规则 ID（dq_rule.id，VARCHAR(64) PK） */
    private String ruleId;

    /** 范围类型: FIELD / TABLE / DATASOURCE / DOMAIN / SYSTEM */
    private String scopeType;

    /** 范围 ID (TABLE 时 = table_qualified_name；FIELD 时 = datasource:table:field) */
    private String scopeId;

    /** 规则参数（已脱敏；非敏感键保留原值） */
    private Map<String, Object> parameters;

    /** 数据源连接配置（仅暴露非敏感键；敏感键由 DqSecurityService#maskParameters 替换为 MASKED） */
    private Map<String, Object> connectionConfig;

    /** 样本失败行明细（dq_rule_check.sample_failures JSONB 反序列化；Phase 2 默认空） */
    private Map<String, Object> sampleFailures;

    /** 本次评估样本总行数（source 0 -> 全表为 0 -> 评估器视无重复/完整/无错误） */
    private long totalRows;

    /** 本次评估失败行数 (dq_rule_check.failed_rows) */
    private long failedRows;

    /** 评估端触发的时间 (NOW() 来自 PG) */
    private LocalDateTime executedAt;

    /** 命中规则的执行记录（rule_check 行级快照，可选传入便于评估器调试输出） */
    private Map<String, Object> lastCheck;

    /** 目标字段（target_field；敏感字段判定时由评估器兜底 mask） */
    private String targetField;

    /** 默认 fresh regex（保留给 FRESHNESS 评估器读参数 threshold.minutes；评估器自行 setDefault） */
    public static final String FRESHNESS_KEY = "freshness_minutes";
}
