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

    // ── R4 承参与信心面（#146ay / §8.4.2 行 432；V296/V297 列位，additive 只增不改上方既有字段）──

    /** 承参维度主体类型（§8.4.2 禁场景专有列；可选） */
    private String subjectType;

    /** 承参维度主体标识（与 subjectType 成对） */
    private String subjectId;

    /** 立据/理由（§8.4.2 与 statement 并列；显式给出必须非空白，省略走列默认空串兼容存量契约） */
    private String reason;

    /** 信心度 ∈[0,1]（V296 NUMERIC(9,6)；越界 fail-loud 400，禁静默 clamp/默认） */
    private Double confidence;

    /** 归属场景 id（E3 承参过滤维，V297；可选，注册期绑定） */
    private String scenarioId;

    /** 归属 Mind id（E3 带 mind 参时严格等值过滤，V297；可选） */
    private String mindId;
}
