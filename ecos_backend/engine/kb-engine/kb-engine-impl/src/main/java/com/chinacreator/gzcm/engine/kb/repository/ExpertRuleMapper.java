package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.ExpertRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 专家规则 Mapper — ecos_knowledge.expert_rule。
 *
 * <p>PG TIMESTAMP ↔ Java long 不兼容: SELECT 端用
 * {@code EXTRACT(EPOCH FROM ...) * 1000::BIGINT}, INSERT/UPDATE 端用
 * {@code TO_TIMESTAMP(#{x} / 1000.0)}。
 */
@Mapper
public interface ExpertRuleMapper {

    @Select("SELECT id, name, domain, rule_type as ruleType, condition_expr as condition, action_expr as action, priority, enabled, description, EXTRACT(EPOCH FROM created_at) * 1000::BIGINT as createdAt, EXTRACT(EPOCH FROM updated_at) * 1000::BIGINT as updatedAt FROM ecos_knowledge.expert_rule WHERE id = #{id}")
    ExpertRule findById(@Param("id") String id);

    @Select("SELECT id, name, domain, rule_type as ruleType, condition_expr as condition, action_expr as action, priority, enabled, description, EXTRACT(EPOCH FROM created_at) * 1000::BIGINT as createdAt, EXTRACT(EPOCH FROM updated_at) * 1000::BIGINT as updatedAt FROM ecos_knowledge.expert_rule WHERE domain = #{domain}")
    List<ExpertRule> findByDomain(@Param("domain") String domain);

    @Select("SELECT id, name, domain, rule_type as ruleType, condition_expr as condition, action_expr as action, priority, enabled, description, EXTRACT(EPOCH FROM created_at) * 1000::BIGINT as createdAt, EXTRACT(EPOCH FROM updated_at) * 1000::BIGINT as updatedAt FROM ecos_knowledge.expert_rule")
    List<ExpertRule> findAll();

    @Insert("INSERT INTO ecos_knowledge.expert_rule (id, name, domain, rule_type, condition_expr, action_expr, priority, enabled, description, created_at, updated_at) " +
            "VALUES (#{id}, #{name}, #{domain, jdbcType=VARCHAR}, #{ruleType}, #{condition}, #{action}, #{priority}, #{enabled}, #{description, jdbcType=VARCHAR}, TO_TIMESTAMP(#{createdAt} / 1000.0), TO_TIMESTAMP(#{updatedAt} / 1000.0))")
    int insert(ExpertRule rule);

    @Update("UPDATE ecos_knowledge.expert_rule SET name=#{name}, domain=#{domain, jdbcType=VARCHAR}, rule_type=#{ruleType}, " +
            "condition_expr=#{condition}, action_expr=#{action}, priority=#{priority}, enabled=#{enabled}, " +
            "description=#{description, jdbcType=VARCHAR}, updated_at=TO_TIMESTAMP(#{updatedAt} / 1000.0) WHERE id=#{id}")
    int update(ExpertRule rule);

    @Delete("DELETE FROM ecos_knowledge.expert_rule WHERE id = #{id}")
    int deleteById(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM ecos_knowledge.expert_rule")
    long count();
}