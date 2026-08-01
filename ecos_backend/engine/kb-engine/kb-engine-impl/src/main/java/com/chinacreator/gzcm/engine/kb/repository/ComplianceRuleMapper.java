package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ComplianceRuleMapper {

    @Select("SELECT id, name, domain, rule_type AS ruleType, condition, action, priority, enabled, " +
            "description, status, required_fact_list AS requiredFactList, extracted_rule_id AS extractedRuleId, " +
            "approved_by AS approvedBy, effective_date AS effectiveDate, expiry_date AS expiryDate, " +
            "version, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM sys_compliance_rule WHERE id = #{id}")
    ComplianceRule findById(@Param("id") String id);

    @Select("SELECT id, name, domain, rule_type AS ruleType, condition, action, priority, enabled, " +
            "description, status, required_fact_list AS requiredFactList, extracted_rule_id AS extractedRuleId, " +
            "approved_by AS approvedBy, effective_date AS effectiveDate, expiry_date AS expiryDate, " +
            "version, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM sys_compliance_rule WHERE domain = #{domain}")
    List<ComplianceRule> findByDomain(@Param("domain") String domain);

    @Select("SELECT id, name, domain, rule_type AS ruleType, condition, action, priority, enabled, " +
            "description, status, required_fact_list AS requiredFactList, extracted_rule_id AS extractedRuleId, " +
            "approved_by AS approvedBy, effective_date AS effectiveDate, expiry_date AS expiryDate, " +
            "version, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM sys_compliance_rule")
    List<ComplianceRule> findAll();

    @Select("SELECT id, name, domain, rule_type AS ruleType, condition, action, priority, enabled, " +
            "description, status, required_fact_list AS requiredFactList, extracted_rule_id AS extractedRuleId, " +
            "approved_by AS approvedBy, effective_date AS effectiveDate, expiry_date AS expiryDate, " +
            "version, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM sys_compliance_rule WHERE status = #{status}")
    List<ComplianceRule> findByStatus(@Param("status") String status);

    @Insert("INSERT INTO sys_compliance_rule (id, name, domain, rule_type, condition, action, priority, enabled, " +
            "description, status, required_fact_list, extracted_rule_id, approved_by, effective_date, expiry_date, " +
            "version, created_at, updated_at) " +
            "VALUES (#{id}, #{name}, #{domain}, #{ruleType}, #{condition}, #{action}, #{priority}, #{enabled}, " +
            "#{description}, #{status}, #{requiredFactList}, #{extractedRuleId}, #{approvedBy}, " +
            "#{effectiveDate}, #{expiryDate}, #{version}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = false)
    int insert(ComplianceRule rule);

    @Update("UPDATE sys_compliance_rule SET name=#{name}, domain=#{domain}, rule_type=#{ruleType}, " +
            "condition=#{condition}, action=#{action}, priority=#{priority}, enabled=#{enabled}, " +
            "description=#{description}, status=#{status}, required_fact_list=#{requiredFactList}, " +
            "approved_by=#{approvedBy}, effective_date=#{effectiveDate}, expiry_date=#{expiryDate}, " +
            "version=#{version}, updated_at=#{updatedAt} " +
            "WHERE id=#{id}")
    int update(ComplianceRule rule);

    @Delete("DELETE FROM sys_compliance_rule WHERE id = #{id}")
    int deleteById(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM sys_compliance_rule")
    long count();
}
