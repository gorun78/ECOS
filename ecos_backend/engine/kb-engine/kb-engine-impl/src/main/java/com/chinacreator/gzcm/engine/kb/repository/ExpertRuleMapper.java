package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.ExpertRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ExpertRuleMapper {

    @Select("SELECT id, name, domain, rule_type as ruleType, condition_expr as condition, action_expr as action, priority, enabled, description, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.expert_rule WHERE id = #{id}")
    ExpertRule findById(@Param("id") String id);

    @Select("SELECT id, name, domain, rule_type as ruleType, condition_expr as condition, action_expr as action, priority, enabled, description, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.expert_rule WHERE domain = #{domain}")
    List<ExpertRule> findByDomain(@Param("domain") String domain);

    @Select("SELECT id, name, domain, rule_type as ruleType, condition_expr as condition, action_expr as action, priority, enabled, description, created_at as createdAt, updated_at as updatedAt FROM ecos_knowledge.expert_rule")
    List<ExpertRule> findAll();

    @Insert("INSERT INTO ecos_knowledge.expert_rule (id, name, domain, rule_type, condition_expr, action_expr, priority, enabled, description, created_at, updated_at) " +
            "VALUES (#{id}, #{name}, #{domain, jdbcType=VARCHAR}, #{ruleType}, #{condition}, #{action}, #{priority}, #{enabled}, #{description, jdbcType=VARCHAR}, #{createdAt}, #{updatedAt})")
    int insert(ExpertRule rule);

    @Update("UPDATE ecos_knowledge.expert_rule SET name=#{name}, domain=#{domain, jdbcType=VARCHAR}, rule_type=#{ruleType}, " +
            "condition_expr=#{condition}, action_expr=#{action}, priority=#{priority}, enabled=#{enabled}, " +
            "description=#{description, jdbcType=VARCHAR}, updated_at=#{updatedAt} WHERE id=#{id}")
    int update(ExpertRule rule);

    @Delete("DELETE FROM ecos_knowledge.expert_rule WHERE id = #{id}")
    int deleteById(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM ecos_knowledge.expert_rule")
    long count();
}