package com.chinacreator.gzcm.engine.cognitive2.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 认知假设新增请求 DTO（PMO-59 P2a / ADR-9 心智层 H 库，入参强类型，出参复用 common-api {@code HypothesisVO}）。
 *
 * <p>对齐表 {@code ecos_cognitive_hypothesis}（V128）。业务唯一键 {@code hypothesisCode} 幂等：
 * 重复 code 由 Service 层返回 400 拒绝（DB 唯一索引兜底）。
 * 服务端自动生成主键 id；注册即初始 {@code status=VALID}/{@code is_valid=true}，审计列服务端置位。</p>
 */
@Data
public class HypothesisSaveDTO {

    /** 业务唯一键（必填，幂等登记；建议格式 HYP-yyyyMMdd-xxx） */
    private String hypothesisCode;

    /** 租户/域隔离（可选，缺省 default） */
    private String tenantScope;

    /** 假设陈述（必填，业务语言） */
    private String statement;

    /** 业务域（可选，如 supply-chain / pricing） */
    private String domain;

    /** 关联经营变量（可选，对齐 {@code ecos_cognitive_belief.variable_name}，失效检测联动键） */
    private String metricRef;

    /** 支撑证据 id 列表（可选，引用 {@code ecos_cognitive_evidence.id}，建议非空） */
    private List<String> evidenceIds = new ArrayList<>();
}
