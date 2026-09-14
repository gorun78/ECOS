package com.chinacreator.gzcm.engine.data.quality.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import com.chinacreator.gzcm.engine.data.quality.model.DqRuleQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVersionVO;

/**
 * DQ 规则 MyBatis Mapper — 读 {@code ecos_dq.dq_rule} / {@code ecos_dq.dq_rule_version}（只读, Phase 1）。
 * <p>
 * PMO-48-A T3: 跨 schema 查询需显式 {@code ecos_dq.} 前缀；gateway 已配置
 * {@code map-underscore-to-camel-case: true}，驼峰列自动映射 VO。
 * </p>
 *
 * @author PMO-48-A T3
 */
@Mapper
public interface DqRuleMapper {

    /** 列表查询公共 SQL 列（跨 schema 显式 ecos_dq. 前缀） */
    String COLUMNS = "id, rule_name AS ruleName, rule_code AS ruleCode, category, domain, "
            + "rule_type AS ruleType, severity, target_kind AS targetKind, target_id AS targetId, "
            + "target_table AS targetTable, target_field AS targetField, "
            + "target_pipeline_id AS targetPipelineId, parameters::text AS parametersJson, "
            + "status, version, approved_by AS approvedBy, effective_date AS effectiveDate, "
            + "expiry_date AS expiryDate, source_type AS sourceType, source_ref AS sourceRef, "
            + "description, created_by AS createdBy, updated_by AS updatedBy, "
            + "created_at AS createdAt, updated_at AS updatedAt";

    /**
     * 按过滤条件分页查询规则列表。
     *
     * @param q  查询条件（全部字段可选）
     * @param limit  每页大小
     * @param offset 偏移量 ((pageNum-1) * pageSize)
     * @return 规则摘要列表
     */
    @SelectProvider(type = DqRuleSqlProvider.class, method = "listByFilter")
    List<DqRuleVO> listByFilter(@Param("q") DqRuleQuery q,
                                @Param("limit") int limit,
                                @Param("offset") int offset);

    /**
     * 按过滤条件统计总数（与 listByFilter 同 WHERE）。
     */
    @SelectProvider(type = DqRuleSqlProvider.class, method = "countByFilter")
    long countByFilter(@Param("q") DqRuleQuery q);

    /**
     * 按 ID 查规则详情。
     */
    @Select("SELECT " + COLUMNS + " FROM ecos_dq.dq_rule WHERE id = #{id} AND is_deleted = FALSE")
    DqRuleVO findById(@Param("id") String id);

    /**
     * 查询规则的版本快照历史（按版本号降序）。
     */
    @Select("SELECT id, rule_id AS ruleId, version_number AS versionNumber, "
            + "snapshot::text AS snapshotJson, changed_by AS changedBy, "
            + "changed_at AS changedAt, change_note AS changeNote "
            + "FROM ecos_dq.dq_rule_version WHERE rule_id = #{ruleId} "
            + "ORDER BY version_number DESC")
    List<DqRuleVersionVO> listVersionsByRuleId(@Param("ruleId") String ruleId);
}
