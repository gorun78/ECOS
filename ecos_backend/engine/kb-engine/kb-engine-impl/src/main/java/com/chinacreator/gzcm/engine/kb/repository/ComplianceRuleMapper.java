package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * sys_compliance_rule Mapper (P0-4 修).
 *
 * <p>PG TIMESTAMP ↔ Java long 不兼容, 改 SQL 端显式 epoch 转换:
 * <ul>
 *   <li>SELECT: {@code EXTRACT(EPOCH FROM created_at) * 1000::BIGINT AS createdAt} → long</li>
 *   <li>INSERT/UPDATE: {@code TO_TIMESTAMP(#{createdAt} / 1000.0)} → timestamp</li>
 * </ul>
 *
 * <p>compliance-rules 06 T4 之前 500 (BadSqlGrammar: Bad value for type long) 即 P0-4 实测锤点.
 */
@Mapper
public interface ComplianceRuleMapper {

    @Select("SELECT id, name, domain, rule_type AS ruleType, condition, action, priority, enabled, " +
            "description, status, required_fact_list AS requiredFactList, extracted_rule_id AS extractedRuleId, " +
            "approved_by AS approvedBy, " +
            "CASE WHEN effective_date IS NULL THEN 0 ELSE EXTRACT(EPOCH FROM effective_date) * 1000::BIGINT END AS effectiveDate, " +
            "CASE WHEN expiry_date IS NULL THEN 0 ELSE EXTRACT(EPOCH FROM expiry_date) * 1000::BIGINT END AS expiryDate, " +
            "version, " +
            "EXTRACT(EPOCH FROM created_at) * 1000::BIGINT AS createdAt, " +
            "EXTRACT(EPOCH FROM updated_at) * 1000::BIGINT AS updatedAt " +
            "FROM sys_compliance_rule WHERE id = #{id}")
    ComplianceRule findById(@Param("id") String id);

    @Select("SELECT id, name, domain, rule_type AS ruleType, condition, action, priority, enabled, " +
            "description, status, required_fact_list AS requiredFactList, extracted_rule_id AS extractedRuleId, " +
            "approved_by AS approvedBy, " +
            "CASE WHEN effective_date IS NULL THEN 0 ELSE EXTRACT(EPOCH FROM effective_date) * 1000::BIGINT END AS effectiveDate, " +
            "CASE WHEN expiry_date IS NULL THEN 0 ELSE EXTRACT(EPOCH FROM expiry_date) * 1000::BIGINT END AS expiryDate, " +
            "version, " +
            "EXTRACT(EPOCH FROM created_at) * 1000::BIGINT AS createdAt, " +
            "EXTRACT(EPOCH FROM updated_at) * 1000::BIGINT AS updatedAt " +
            "FROM sys_compliance_rule WHERE domain = #{domain}")
    List<ComplianceRule> findByDomain(@Param("domain") String domain);

    @Select("SELECT id, name, domain, rule_type AS ruleType, condition, action, priority, enabled, " +
            "description, status, required_fact_list AS requiredFactList, extracted_rule_id AS extractedRuleId, " +
            "approved_by AS approvedBy, " +
            "CASE WHEN effective_date IS NULL THEN 0 ELSE EXTRACT(EPOCH FROM effective_date) * 1000::BIGINT END AS effectiveDate, " +
            "CASE WHEN expiry_date IS NULL THEN 0 ELSE EXTRACT(EPOCH FROM expiry_date) * 1000::BIGINT END AS expiryDate, " +
            "version, " +
            "EXTRACT(EPOCH FROM created_at) * 1000::BIGINT AS createdAt, " +
            "EXTRACT(EPOCH FROM updated_at) * 1000::BIGINT AS updatedAt " +
            "FROM sys_compliance_rule")
    List<ComplianceRule> findAll();

    @Select("SELECT id, name, domain, rule_type AS ruleType, condition, action, priority, enabled, " +
            "description, status, required_fact_list AS requiredFactList, extracted_rule_id AS extractedRuleId, " +
            "approved_by AS approvedBy, " +
            "CASE WHEN effective_date IS NULL THEN 0 ELSE EXTRACT(EPOCH FROM effective_date) * 1000::BIGINT END AS effectiveDate, " +
            "CASE WHEN expiry_date IS NULL THEN 0 ELSE EXTRACT(EPOCH FROM expiry_date) * 1000::BIGINT END AS expiryDate, " +
            "version, " +
            "EXTRACT(EPOCH FROM created_at) * 1000::BIGINT AS createdAt, " +
            "EXTRACT(EPOCH FROM updated_at) * 1000::BIGINT AS updatedAt " +
            "FROM sys_compliance_rule WHERE status = #{status}")
    List<ComplianceRule> findByStatus(@Param("status") String status);

    @Insert("INSERT INTO sys_compliance_rule (id, name, domain, rule_type, condition, action, priority, enabled, " +
            "description, status, required_fact_list, extracted_rule_id, approved_by, " +
            "effective_date, expiry_date, " +
            "version, created_at, updated_at) " +
            "VALUES (#{id}, #{name}, #{domain}, #{ruleType}, #{condition}, #{action}, #{priority}, #{enabled}, " +
            "#{description}, #{status}, #{requiredFactList}, #{extractedRuleId}, #{approvedBy}, " +
            "CASE WHEN #{effectiveDate} = 0 THEN NULL ELSE TO_TIMESTAMP(#{effectiveDate} / 1000.0) END, " +
            "CASE WHEN #{expiryDate} = 0 THEN NULL ELSE TO_TIMESTAMP(#{expiryDate} / 1000.0) END, " +
            "#{version}, " +
            "TO_TIMESTAMP(#{createdAt} / 1000.0), " +
            "TO_TIMESTAMP(#{updatedAt} / 1000.0))")
    @Options(useGeneratedKeys = false)
    int insert(ComplianceRule rule);

    @Update("UPDATE sys_compliance_rule SET name=#{name}, domain=#{domain}, rule_type=#{ruleType}, " +
            "condition=#{condition}, action=#{action}, priority=#{priority}, enabled=#{enabled}, " +
            "description=#{description}, status=#{status}, required_fact_list=#{requiredFactList}, " +
            "approved_by=#{approvedBy}, " +
            "effective_date=CASE WHEN #{effectiveDate} = 0 THEN NULL ELSE TO_TIMESTAMP(#{effectiveDate} / 1000.0) END, " +
            "expiry_date=CASE WHEN #{expiryDate} = 0 THEN NULL ELSE TO_TIMESTAMP(#{expiryDate} / 1000.0) END, " +
            "version=#{version}, " +
            "updated_at=TO_TIMESTAMP(#{updatedAt} / 1000.0) " +
            "WHERE id=#{id}")
    int update(ComplianceRule rule);

    @Delete("DELETE FROM sys_compliance_rule WHERE id = #{id}")
    int deleteById(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM sys_compliance_rule")
    long count();
}
