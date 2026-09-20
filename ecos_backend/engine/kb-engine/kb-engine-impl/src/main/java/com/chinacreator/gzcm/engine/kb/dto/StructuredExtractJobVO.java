package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 结构化抽取作业摘要 VO（K1 批次 E4 端点出参）。
 *
 * <p>数据源：{@code ecos_knowledge.kb_extract_watermark} 按 updated_at 聚合——
 * 每次结构化抽取落水位线即代表一次作业（jobId 由触发端点生成且不入水位表，
 * 故列表侧以「最近水位更新」为作业事实源）。</p>
 */
@Data
public class StructuredExtractJobVO {

    /** 作业标识（无法从水位表溯源时为 null，由 E5 详情按触发链路补全） */
    private String jobId;

    /** 抽取模式：FULL / INCREMENTAL */
    private String mode;

    /** 作业状态：水位落库即视为 SUCCESS（dry-run 不落水位，不出现在列表） */
    private String status;

    /** 作业触发时间（水位 updated_at，ISO-8601 字符串） */
    private String startedAt;

    /** 作业耗时（毫秒；无 report 溯源时为 null） */
    private Long durationMs;
}
