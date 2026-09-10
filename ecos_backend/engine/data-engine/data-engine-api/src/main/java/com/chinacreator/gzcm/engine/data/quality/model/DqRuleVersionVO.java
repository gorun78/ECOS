package com.chinacreator.gzcm.engine.data.quality.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 规则版本快照 VO — 对应 {@code ecos_dq.dq_rule_version}。
 *
 * @author PMO-48-A T3
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqRuleVersionVO {

    /** 版本记录 ID (PK) */
    private String id;

    /** 所属规则 ID */
    private String ruleId;

    /** 版本号 */
    private Integer versionNumber;

    /** 该版本的规则完整快照 (JSONB 原样字符串, 由 Service 反序列化并对敏感键脱敏) */
    private String snapshotJson;

    /** 变更人 */
    private String changedBy;

    /** 变更时间 */
    private LocalDateTime changedAt;

    /** 变更说明 */
    private String changeNote;
}
