package com.chinacreator.gzcm.engine.kb.assumption;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 情景假设·定义态 Mapper（表 {@code ecos_knowledge.ecos_kb_assumption}，V177）。
 * <p>
 * R-8 ②：数值事实 {@code value} 落业务域 {@code ecos_dw.ecos_kb_assumption_value}
 * （由 {@code KbAssumptionValueMapper} 承载）；本表保留定义态
 * （key/scenario/metric/dim_scope_json/value_type/value_ref/value_semantic/base/valid_* /
 * reason/evidence_ref/is_unverified/version/status/approved_* + 基线列）。
 *
 * @author ECOS KB Team
 */
@Mapper
public interface KbAssumptionMapper {

    @Insert("INSERT INTO ecos_knowledge.ecos_kb_assumption ("
            + "id, assumption_key, scenario_type, metric_code, dim_scope_json, value_type, value_ref, "
            + "value_semantic, base_assumption_id, valid_from, valid_to, reason, evidence_ref, is_unverified, "
            + "expire_at, assumption_version, status, approved_by, approved_time, trace_id, domain, version_no, "
            + "is_deleted, create_by, update_by, create_time, update_time) "
            + "VALUES ("
            + "#{id}, #{assumptionKey}, #{scenarioType}, #{metricCode}, #{dimScopeJson}, #{valueType}, #{valueRef}, "
            + "#{valueSemantic}, #{baseAssumptionId}, #{validFrom}, #{validTo}, #{reason}, #{evidenceRef}, #{isUnverified}, "
            + "#{expireAt}, #{assumptionVersion}, #{status}, #{approvedBy}, #{approvedTime}, #{traceId}, #{domain}, #{versionNo}, "
            + "#{isDeleted}, #{createBy}, #{updateBy}, #{createTime}, #{updateTime})")
    int insert(@Param("id") String id,
               @Param("assumptionKey") String assumptionKey,
               @Param("scenarioType") String scenarioType,
               @Param("metricCode") String metricCode,
               @Param("dimScopeJson") String dimScopeJson,
               @Param("valueType") String valueType,
               @Param("valueRef") String valueRef,
               @Param("valueSemantic") String valueSemantic,
               @Param("baseAssumptionId") String baseAssumptionId,
               @Param("validFrom") String validFrom,
               @Param("validTo") String validTo,
               @Param("reason") String reason,
               @Param("evidenceRef") String evidenceRef,
               @Param("isUnverified") Integer isUnverified,
               @Param("expireAt") Date expireAt,
               @Param("assumptionVersion") String assumptionVersion,
               @Param("status") String status,
               @Param("approvedBy") String approvedBy,
               @Param("approvedTime") Date approvedTime,
               @Param("traceId") String traceId,
               @Param("domain") String domain,
               @Param("versionNo") String versionNo,
               @Param("isDeleted") Integer isDeleted,
               @Param("createBy") String createBy,
               @Param("updateBy") String updateBy,
               @Param("createTime") Date createTime,
               @Param("updateTime") Date updateTime);

    @Select("SELECT id, assumption_key AS assumptionKey, scenario_type AS scenarioType, metric_code AS metricCode, "
            + "dim_scope_json AS dimScopeJson, value_type AS valueType, value_ref AS valueRef, "
            + "value_semantic AS valueSemantic, base_assumption_id AS baseAssumptionId, valid_from AS validFrom, "
            + "valid_to AS validTo, reason, evidence_ref AS evidenceRef, is_unverified AS isUnverified, "
            + "expire_at AS expireAt, assumption_version AS assumptionVersion, status, approved_by AS approvedBy, "
            + "approved_time AS approvedTime, trace_id AS traceId, domain, version_no AS versionNo, "
            + "create_by AS createBy, update_by AS updateBy, create_time AS createTime, update_time AS updateTime "
            + "FROM ecos_knowledge.ecos_kb_assumption WHERE id = #{id} AND is_deleted = 0")
    Map<String, Object> findById(@Param("id") String id);

    /** 某 assumption_key 下当前最大版本（生成下一个单调递增版本）。 */
    @Select("SELECT assumption_version AS assumptionVersion FROM ecos_knowledge.ecos_kb_assumption "
            + "WHERE assumption_key = #{assumptionKey} AND is_deleted = 0 ORDER BY assumption_version DESC LIMIT 1")
    Map<String, Object> findLatestVersionByKey(@Param("assumptionKey") String assumptionKey);

    /** DRAFT → PENDING_APPROVAL（submit）。 */
    @Update("UPDATE ecos_knowledge.ecos_kb_assumption "
            + "SET status = 'PENDING_APPROVAL', update_by = #{updateBy}, update_time = #{updateTime} "
            + "WHERE id = #{id} AND status = 'DRAFT' AND is_deleted = 0")
    int markPending(@Param("id") String id, @Param("updateBy") String updateBy, @Param("updateTime") Date updateTime);

    /** PENDING_APPROVAL → ACTIVE（approve；approved_by/approved_time 落值）。 */
    @Update("UPDATE ecos_knowledge.ecos_kb_assumption "
            + "SET status = 'ACTIVE', approved_by = #{approvedBy}, approved_time = #{approvedTime}, "
            + "update_by = #{updateBy}, update_time = #{updateTime} "
            + "WHERE id = #{id} AND status = 'PENDING_APPROVAL' AND is_deleted = 0")
    int markActive(@Param("id") String id, @Param("approvedBy") String approvedBy,
                   @Param("approvedTime") Date approvedTime, @Param("updateBy") String updateBy,
                   @Param("updateTime") Date updateTime);

    /** ACTIVE → EXPIRED（expireOverdue 用；仅将已到期 ACTIVE 置 EXPIRED）。 */
    @Update("UPDATE ecos_knowledge.ecos_kb_assumption "
            + "SET status = 'EXPIRED', update_by = #{updateBy}, update_time = #{updateTime} "
            + "WHERE id = #{id} AND status = 'ACTIVE' AND is_deleted = 0")
    int markExpired(@Param("id") String id, @Param("updateBy") String updateBy, @Param("updateTime") Date updateTime);

    /** 某 assumption_key 下全部 ACTIVE 行置 SUPERSEDED（建新版本后调用，I-6 只对新旧之间作用）。 */
    @Update("UPDATE ecos_knowledge.ecos_kb_assumption "
            + "SET status = 'SUPERSEDED', update_by = #{updateBy}, update_time = #{updateTime} "
            + "WHERE assumption_key = #{assumptionKey} AND status = 'ACTIVE' AND is_deleted = 0")
    int supersedeActiveByKey(@Param("assumptionKey") String assumptionKey,
                           @Param("updateBy") String updateBy, @Param("updateTime") Date updateTime);

    /** list 查询（scenarioType/metric/status 过滤 + 分页）。 */
    @Select("<script>"
            + "SELECT id, assumption_key AS assumptionKey, scenario_type AS scenarioType, metric_code AS metricCode, "
            + "dim_scope_json AS dimScopeJson, value_type AS valueType, value_semantic AS valueSemantic, "
            + "base_assumption_id AS baseAssumptionId, valid_from AS validFrom, valid_to AS validTo, "
            + "reason, evidence_ref AS evidenceRef, is_unverified AS isUnverified, expire_at AS expireAt, "
            + "assumption_version AS assumptionVersion, status, approved_by AS approvedBy, "
            + "trace_id AS traceId, domain, create_time AS createTime, update_time AS updateTime "
            + "FROM ecos_knowledge.ecos_kb_assumption WHERE is_deleted = 0 "
            + "<if test='scenarioType != null AND scenarioType != \"\"'> AND scenario_type = #{scenarioType}</if> "
            + "<if test='metricCode != null AND metricCode != \"\"'> AND metric_code = #{metricCode}</if> "
            + "<if test='status != null AND status != \"\"'> AND status = #{status}</if> "
            + "ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Map<String, Object>> list(@Param("scenarioType") String scenarioType,
                                   @Param("metricCode") String metricCode,
                                   @Param("status") String status,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    @Select("<script>"
            + "SELECT COUNT(*) FROM ecos_knowledge.ecos_kb_assumption WHERE is_deleted = 0 "
            + "<if test='scenarioType != null AND scenarioType != \"\"'> AND scenario_type = #{scenarioType}</if> "
            + "<if test='metricCode != null AND metricCode != \"\"'> AND metric_code = #{metricCode}</if> "
            + "<if test='status != null AND status != \"\"'> AND status = #{status}</if>"
            + "</script>")
    long count(@Param("scenarioType") String scenarioType,
               @Param("metricCode") String metricCode,
               @Param("status") String status);

    /**
     * resolve：ACTIVE 且未过期（expire_at IS NULL OR expire_at &gt; NOW()），按 scenarioType+metric 命中，
     * dim_scope_json 包含匹配（精确优先由服务层排序兜底）。
     */
    @Select("<script>"
            + "SELECT id, assumption_key AS assumptionKey, scenario_type AS scenarioType, metric_code AS metricCode, "
            + "dim_scope_json AS dimScopeJson, value_type AS valueType, value_ref AS valueRef, "
            + "value_semantic AS valueSemantic, base_assumption_id AS baseAssumptionId, "
            + "assumption_version AS assumptionVersion, is_unverified AS isUnverified, expire_at AS expireAt, "
            + "status, domain "
            + "FROM ecos_knowledge.ecos_kb_assumption "
            + "WHERE is_deleted = 0 AND status = 'ACTIVE' "
            + "AND (expire_at IS NULL OR expire_at &gt; CURRENT_TIMESTAMP) "
            + "AND scenario_type = #{scenarioType} AND metric_code = #{metricCode} "
            + "<if test='dimScopeFilter != null AND dimScopeFilter != \"\"'> AND dim_scope_json LIKE CONCAT('%', #{dimScopeFilter}, '%')</if> "
            + "ORDER BY assumption_version DESC"
            + "</script>")
    List<Map<String, Object>> resolve(@Param("scenarioType") String scenarioType,
                                      @Param("metricCode") String metricCode,
                                      @Param("dimScopeFilter") String dimScopeFilter);
}
