package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * sys_compliance_rule Mapper。
 *
 * <p><b>列型分叉的收口（#147Y，2026-11-02 live 取证）</b>：P0-4 时代本类按
 * {@code EXTRACT(EPOCH FROM x) * 1000::BIGINT}/{@code TO_TIMESTAMP(#{x} / 1000.0)}
 * 写就，前提是时间列为 {@code TIMESTAMP}。但该前提对<b>本表不成立</b>：
 * V100 建表把 {@code effective_date/expiry_date/created_at/updated_at} 定为
 * {@code BIGINT}（epoch 毫秒），live {@code information_schema} 回查一致 ⇒
 * {@code extract(unknown, bigint)} 不存在使 4 枚 SELECT 全 500，
 * {@code TO_TIMESTAMP} 产出 timestamptz 使 INSERT/UPDATE 全 500（实测两条报错见
 * {@code ComplianceRuleMapperTest} 文件头）。现按列型直传 epoch 毫秒。</p>
 *
 * <p><b>禁与同族 {@code ExpertRuleMapper} 一刀切</b>：那张表的 {@code created_at}
 * 实为 {@code timestamp without time zone}，其 EXTRACT/TO_TIMESTAMP 形态是正确的；
 * 两表列型不同，改一侧不得连带"顺手统一"另一侧。该不对称现由
 * {@code ComplianceRuleMapperTest} 按 DDL 单源双向钉住。</p>
 *
 * <p><b>0 ⇔ NULL 哨兵语义保持不变</b>：{@code effectiveDate/expiryDate == 0} 表示
 * "未设置"，落库写 NULL（读回经 {@code COALESCE} 归 0）。写入用 {@code NULLIF} 而非
 * {@code CASE WHEN}，既免 PG 对裸参数报 "could not determine data type"，
 * 也不再引入 {@code ::} 强转（方言面违例名录同批收缩）。</p>
 */
@Mapper
public interface ComplianceRuleMapper {

    /** 共用列前段（四枚 SELECT 同形）。 */
    String BASE_COLUMNS =
            "id, name, domain, rule_type AS ruleType, condition, action, priority, enabled, " +
            "description, status, required_fact_list AS requiredFactList, extracted_rule_id AS extractedRuleId, " +
            "approved_by AS approvedBy, ";

    /** 四枚时间列在本表一律为 {@code BIGINT} epoch 毫秒；NULL 归 0 由 SQL 端 COALESCE 承担。 */
    String TIME_COLUMNS_SELECT =
            "COALESCE(effective_date, 0) AS effectiveDate, " +
            "COALESCE(expiry_date, 0) AS expiryDate, " +
            "version, " +
            "COALESCE(created_at, 0) AS createdAt, " +
            "COALESCE(updated_at, 0) AS updatedAt ";

    @Select("SELECT " + BASE_COLUMNS + TIME_COLUMNS_SELECT +
            "FROM sys_compliance_rule WHERE id = #{id}")
    ComplianceRule findById(@Param("id") String id);

    @Select("SELECT " + BASE_COLUMNS + TIME_COLUMNS_SELECT +
            "FROM sys_compliance_rule WHERE domain = #{domain}")
    List<ComplianceRule> findByDomain(@Param("domain") String domain);

    @Select("SELECT " + BASE_COLUMNS + TIME_COLUMNS_SELECT +
            "FROM sys_compliance_rule")
    List<ComplianceRule> findAll();

    @Select("SELECT " + BASE_COLUMNS + TIME_COLUMNS_SELECT +
            "FROM sys_compliance_rule WHERE status = #{status}")
    List<ComplianceRule> findByStatus(@Param("status") String status);

    @Insert("INSERT INTO sys_compliance_rule (id, name, domain, rule_type, condition, action, priority, enabled, " +
            "description, status, required_fact_list, extracted_rule_id, approved_by, " +
            "effective_date, expiry_date, " +
            "version, created_at, updated_at) " +
            "VALUES (#{id}, #{name}, #{domain}, #{ruleType}, #{condition}, #{action}, #{priority}, #{enabled}, " +
            "#{description}, #{status}, #{requiredFactList}, #{extractedRuleId}, #{approvedBy}, " +
            "NULLIF(#{effectiveDate}, 0), " +
            "NULLIF(#{expiryDate}, 0), " +
            "#{version}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = false)
    int insert(ComplianceRule rule);

    @Update("UPDATE sys_compliance_rule SET name=#{name}, domain=#{domain}, rule_type=#{ruleType}, " +
            "condition=#{condition}, action=#{action}, priority=#{priority}, enabled=#{enabled}, " +
            "description=#{description}, status=#{status}, required_fact_list=#{requiredFactList}, " +
            "approved_by=#{approvedBy}, " +
            "effective_date=NULLIF(#{effectiveDate}, 0), " +
            "expiry_date=NULLIF(#{expiryDate}, 0), " +
            "version=#{version}, " +
            "updated_at=#{updatedAt} " +
            "WHERE id=#{id}")
    int update(ComplianceRule rule);

    @Delete("DELETE FROM sys_compliance_rule WHERE id = #{id}")
    int deleteById(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM sys_compliance_rule")
    long count();
}
