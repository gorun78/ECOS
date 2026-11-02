package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 合规规则版本历史行 VO（PMO-74 H11-T4）。
 *
 * <p>承接 {@code ComplianceRuleVersionService#getVersions} 的
 * {@code jdbcTemplate.queryForList} 行 Map，键名保持数据库列名（snake_case）与原响应一致：
 * id / rule_id / version_number / snapshot / changed_by / changed_at / change_note，
 * #147V 只增三键：git_ref（V305 列）/ content_source / content_reason（后两键为读面派生，非列）。
 * 溯源 DDL：{@code gateway/src/main/resources/db/migration/V100__compliance_tables.sql}
 * （id VARCHAR(64), rule_id VARCHAR(64), version_number INT, snapshot TEXT,
 * changed_by VARCHAR(128), changed_at BIGINT）。</p>
 */
@Data
public class RuleVersionVO {

    @JsonProperty("id")
    private String id;

    @JsonProperty("rule_id")
    private String ruleId;

    @JsonProperty("version_number")
    private Integer versionNumber;

    /** JSON 格式的规则快照原文（不解析，保持响应字节级兼容） */
    @JsonProperty("snapshot")
    private String snapshot;

    @JsonProperty("changed_by")
    private String changedBy;

    @JsonProperty("changed_at")
    private Long changedAt;

    @JsonProperty("change_note")
    private String changeNote;

    /**
     * Git 归档引用（commit SHA；V305 列）。
     *
     * <p>空 = 该行从未归档或归档失败 ⇒ 内容仍在权威库；非空 = 对象库里有本行历史，
     * 实际取没取到看 {@code content_source}（禁凭本列存在就宣称内容来自 Git）。</p>
     */
    @JsonProperty("git_ref")
    private String gitRef;

    /** 本行 {@code snapshot} 的内容来源（#147V 三态，见 {@link RuleContentSource}）。 */
    @JsonProperty("content_source")
    private String contentSource;

    /** 退回缘由（仅 {@code content_source=DB_FALLBACK} 时非空）。 */
    @JsonProperty("content_reason")
    private String contentReason;
}
