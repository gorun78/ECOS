package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 合规规则版本历史行 VO（PMO-74 H11-T4）。
 *
 * <p>承接 {@code ComplianceRuleVersionService#getVersions} 的
 * {@code jdbcTemplate.queryForList} 行 Map，键名保持数据库列名（snake_case）与原响应一致：
 * id / rule_id / version_number / snapshot / changed_by / changed_at / change_note。
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
}
