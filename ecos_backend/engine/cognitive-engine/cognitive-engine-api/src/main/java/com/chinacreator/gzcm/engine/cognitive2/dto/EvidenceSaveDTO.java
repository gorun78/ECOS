package com.chinacreator.gzcm.engine.cognitive2.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认知证据新增请求 DTO（PMO-59 P2a / ADR-9，入参强类型，出参复用 common-api {@code EvidenceRecordVO}）。
 *
 * <p>对齐表 {@code ecos_cognitive_evidence}（V127）。业务唯一键 {@code evidenceCode} 幂等：
 * 重复 code 由 Service 层返回 400 拒绝（DB 唯一索引兜底）。
 * 服务端自动生成主键 id；{@code status}/{@code isDeleted}/审计列由服务端置位，客户端不可传。</p>
 */
@Data
public class EvidenceSaveDTO {

    /** 业务唯一键（必填，幂等登记；建议格式 EV-yyyyMMdd-xxx） */
    private String evidenceCode;

    /** 租户/域隔离（可选，缺省 default） */
    private String tenantScope;

    /** 来源类型（必填）: SYSTEM_DATA / NEWS / EXPERT / PIPELINE */
    private String sourceType;

    /** 来源定位（管道ID/URL/人工录入ID，可选） */
    private String sourceRef;

    /** 结构化事实载荷 JSON 文本（必填，须为合法 JSON 对象） */
    private String blob;

    /** 可信度 0~1（可选，缺省 0.5；系统数据 0.95+ / 权威新闻 ~0.8 / 小道消息 0.3~0.5） */
    private Double confidence;

    /** 同事实多证据不一致 → 冲突待修正标记（可选，缺省 false） */
    private Boolean isConflict;

    /** 冲突对方证据 id 列表 JSON 数组文本（可选，缺省 "[]"） */
    private String refutingEvidenceIds;

    /** 事实生效时间（可选） */
    private LocalDateTime effectiveTime;

    /** 事实失效时间（可选） */
    private LocalDateTime expireTime;
}
