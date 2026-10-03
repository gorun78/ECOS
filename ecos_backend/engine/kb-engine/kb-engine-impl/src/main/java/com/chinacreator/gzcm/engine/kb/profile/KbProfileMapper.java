package com.chinacreator.gzcm.engine.kb.profile;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 历史画像·定义态 Mapper（表 {@code ecos_knowledge.ecos_kb_profile}，V176）。
 * <p>
 * R-8 ②：数值统计事实（p10/p50/p90/mean/CI/sample/missing）落业务域
 * {@code ecos_dw.ecos_kb_profile_stats}（由 {@code KbProfileStatsMapper} 经 data-engine 写通道代写），
 * 本表只保留定义态（key/metric/dims/window/ci_level/stats_method/confidence/degrade_* / source_query_ref /
 * stats_ref / version / status / approved_* / task_id / trace_id + 基线列）。
 * <p>
 * 纪律：schema 限定表名（DR01）、JSON 用 TEXT（MC02）、主键应用侧 UUID（MC01）、无 ON CONFLICT / ::（MC03）。
 *
 * @author ECOS KB Team
 */
@Mapper
public interface KbProfileMapper {

    @Insert("INSERT INTO ecos_knowledge.ecos_kb_profile ("
            + "id, profile_key, metric_code, group_dims_json, window_from, window_to, stats_ref, "
            + "ci_level, stats_method, confidence, degrade_from, degrade_path_json, "
            + "applicable_condition, source_query_ref, profile_version, status, approved_by, approved_time, "
            + "task_id, trace_id, domain, version_no, is_deleted, create_by, update_by, create_time, update_time) "
            + "VALUES ("
            + "#{id}, #{profileKey}, #{metricCode}, #{groupDimsJson}, #{windowFrom}, #{windowTo}, #{statsRef}, "
            + "#{ciLevel}, #{statsMethod}, #{confidence}, #{degradeFrom}, #{degradePathJson}, "
            + "#{applicableCondition}, #{sourceQueryRef}, #{profileVersion}, #{status}, #{approvedBy}, #{approvedTime}, "
            + "#{taskId}, #{traceId}, #{domain}, #{versionNo}, #{isDeleted}, #{createBy}, #{updateBy}, #{createTime}, #{updateTime})")
    int insert(@Param("id") String id,
               @Param("profileKey") String profileKey,
               @Param("metricCode") String metricCode,
               @Param("groupDimsJson") String groupDimsJson,
               @Param("windowFrom") String windowFrom,
               @Param("windowTo") String windowTo,
               @Param("statsRef") String statsRef,
               @Param("ciLevel") String ciLevel,
               @Param("statsMethod") String statsMethod,
               @Param("confidence") String confidence,
               @Param("degradeFrom") String degradeFrom,
               @Param("degradePathJson") String degradePathJson,
               @Param("applicableCondition") String applicableCondition,
               @Param("sourceQueryRef") String sourceQueryRef,
               @Param("profileVersion") String profileVersion,
               @Param("status") String status,
               @Param("approvedBy") String approvedBy,
               @Param("approvedTime") Date approvedTime,
               @Param("taskId") String taskId,
               @Param("traceId") String traceId,
               @Param("domain") String domain,
               @Param("versionNo") String versionNo,
               @Param("isDeleted") Integer isDeleted,
               @Param("createBy") String createBy,
               @Param("updateBy") String updateBy,
               @Param("createTime") Date createTime,
               @Param("updateTime") Date updateTime);

    /** 按 id 取定义态一行。 */
    @Select("SELECT id, profile_key AS profileKey, metric_code AS metricCode, group_dims_json AS groupDimsJson, "
            + "window_from AS windowFrom, window_to AS windowTo, stats_ref AS statsRef, ci_level AS ciLevel, "
            + "stats_method AS statsMethod, confidence, degrade_from AS degradeFrom, "
            + "degrade_path_json AS degradePathJson, applicable_condition AS applicableCondition, "
            + "source_query_ref AS sourceQueryRef, profile_version AS profileVersion, status, approved_by AS approvedBy, "
            + "approved_time AS approvedTime, task_id AS taskId, trace_id AS traceId, domain, version_no AS versionNo, "
            + "create_by AS createBy, update_by AS updateBy, create_time AS createTime, update_time AS updateTime "
            + "FROM ecos_knowledge.ecos_kb_profile WHERE id = #{id} AND is_deleted = 0")
    Map<String, Object> findById(@Param("id") String id);

    /** 某 profile_key 下指定版本、指定状态的一行（用于发布时定位）。 */
    @Select("SELECT id, profile_key AS profileKey, metric_code AS metricCode, group_dims_json AS groupDimsJson, "
            + "window_from AS windowFrom, window_to AS windowTo, stats_ref AS statsRef, ci_level AS ciLevel, "
            + "stats_method AS statsMethod, confidence, degrade_from AS degradeFrom, "
            + "degrade_path_json AS degradePathJson, applicable_condition AS applicableCondition, "
            + "source_query_ref AS sourceQueryRef, profile_version AS profileVersion, status, approved_by AS approvedBy, "
            + "approved_time AS approvedTime, task_id AS taskId, trace_id AS traceId, domain, version_no AS versionNo, "
            + "create_by AS createBy, update_by AS updateBy, create_time AS createTime, update_time AS updateTime "
            + "FROM ecos_knowledge.ecos_kb_profile "
            + "WHERE id = #{id} AND profile_key = #{profileKey} AND profile_version = #{profileVersion} AND is_deleted = 0")
    Map<String, Object> findByKeyAndVersion(@Param("id") String id,
                                            @Param("profileKey") String profileKey,
                                            @Param("profileVersion") String profileVersion);

    /** 状态机跃迁 DRAFT→PUBLISHED（approved_by/approved_time 落值）。 */
    @Update("UPDATE ecos_knowledge.ecos_kb_profile "
            + "SET status = 'PUBLISHED', approved_by = #{approvedBy}, approved_time = #{approvedTime}, "
            + "update_by = #{updateBy}, update_time = #{updateTime} "
            + "WHERE id = #{id} AND status = 'DRAFT' AND is_deleted = 0")
    int markPublished(@Param("id") String id,
                      @Param("approvedBy") String approvedBy,
                      @Param("approvedTime") Date approvedTime,
                      @Param("updateBy") String updateBy,
                      @Param("updateTime") Date updateTime);

    /** 将某 profile_key 下全部旧的 PUBLISHED 行置 SUPERSEDED（发布新版本时调用）。 */
    @Update("UPDATE ecos_knowledge.ecos_kb_profile "
            + "SET status = 'SUPERSEDED', update_by = #{updateBy}, update_time = #{updateTime} "
            + "WHERE profile_key = #{profileKey} AND status = 'PUBLISHED' AND is_deleted = 0")
    int supersedePublishedByKey(@Param("profileKey") String profileKey,
                                @Param("updateBy") String updateBy,
                                @Param("updateTime") Date updateTime);

    /** 取某 profile_key 当前最大版本（用于生成下一个单调递增版本）。行不存在视为 "0.0.0"。 */
    @Select("SELECT profile_version AS profileVersion FROM ecos_knowledge.ecos_kb_profile "
            + "WHERE profile_key = #{profileKey} AND is_deleted = 0 ORDER BY profile_version DESC LIMIT 1")
    Map<String, Object> findLatestVersionByKey(@Param("profileKey") String profileKey);

    /**
     * list 查询（过滤 + 分页）。filter 语义：metricCode/dims/status/page/size 皆可选；
     * dims 过滤简化为按 group_dims_json LIKE '%key=value%'（T_APPROX 演示路径）。
     */
    @Select("<script>"
            + "SELECT id, profile_key AS profileKey, metric_code AS metricCode, group_dims_json AS groupDimsJson, "
            + "window_from AS windowFrom, window_to AS windowTo, stats_ref AS statsRef, ci_level AS ciLevel, "
            + "stats_method AS statsMethod, confidence, degrade_from AS degradeFrom, "
            + "degrade_path_json AS degradePathJson, source_query_ref AS sourceQueryRef, "
            + "profile_version AS profileVersion, status, approved_by AS approvedBy, task_id AS taskId, "
            + "trace_id AS traceId, domain, version_no AS versionNo, create_time AS createTime, update_time AS updateTime "
            + "FROM ecos_knowledge.ecos_kb_profile WHERE is_deleted = 0 "
            + "<if test='metricCode != null AND metricCode != \"\"'> AND metric_code = #{metricCode}</if> "
            + "<if test='dimsFilter != null AND dimsFilter != \"\"'> AND group_dims_json LIKE CONCAT('%', #{dimsFilter}, '%')</if> "
            + "<if test='status != null AND status != \"\"'> AND status = #{status}</if> "
            + "ORDER BY create_time DESC "
            + "LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Map<String, Object>> list(@Param("metricCode") String metricCode,
                                   @Param("dimsFilter") String dimsFilter,
                                   @Param("status") String status,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    /** count（list 端点 page total 计算）。 */
    @Select("<script>"
            + "SELECT COUNT(*) FROM ecos_knowledge.ecos_kb_profile WHERE is_deleted = 0 "
            + "<if test='metricCode != null AND metricCode != \"\"'> AND metric_code = #{metricCode}</if> "
            + "<if test='dimsFilter != null AND dimsFilter != \"\"'> AND group_dims_json LIKE CONCAT('%', #{dimsFilter}, '%')</if> "
            + "<if test='status != null AND status != \"\"'> AND status = #{status}</if>"
            + "</script>")
    long count(@Param("metricCode") String metricCode,
               @Param("dimsFilter") String dimsFilter,
               @Param("status") String status);

    /**
     * resolve：某 profile_key 下，atVersion 或最新 PUBLISHED。
     * <ul>
     *   <li>atVersion 非空 → 取该 version 行；</li>
     *   <li>atVersion 空 → 取 status='PUBLISHED' 中 profile_version 最大行。</li>
     * </ul>
     */
    @Select("<script>"
            + "SELECT id, profile_key AS profileKey, metric_code AS metricCode, group_dims_json AS groupDimsJson, "
            + "window_from AS windowFrom, window_to AS windowTo, stats_ref AS statsRef, ci_level AS ciLevel, "
            + "stats_method AS statsMethod, confidence, degrade_from AS degradeFrom, "
            + "degrade_path_json AS degradePathJson, source_query_ref AS sourceQueryRef, "
            + "profile_version AS profileVersion, status, approved_by AS approvedBy, "
            + "task_id AS taskId, trace_id AS traceId, domain, version_no AS versionNo, "
            + "create_by AS createBy, update_by AS updateBy, create_time AS createTime, update_time AS updateTime "
            + "FROM ecos_knowledge.ecos_kb_profile WHERE profile_key = #{profileKey} AND is_deleted = 0 "
            + "<choose>"
            + "<when test='atVersion != null AND atVersion != \"\"'> AND profile_version = #{atVersion} </when>"
            + "<otherwise> AND status = 'PUBLISHED' </otherwise>"
            + "</choose>"
            + " ORDER BY profile_version DESC LIMIT 1"
            + "</script>")
    Map<String, Object> findByKeyForResolve(@Param("profileKey") String profileKey, @Param("atVersion") String atVersion);

    /** 返回某 profile_key 下命中 atVersion（若为空则 status='PUBLISHED'）的全部版本行，按版本倒序（用于超 publish 时置旧为 SUPERSEDED）。 */
    @Select("<script>"
            + "SELECT id, profile_key AS profileKey, profile_version AS profileVersion, status "
            + "FROM ecos_knowledge.ecos_kb_profile WHERE profile_key = #{profileKey} AND is_deleted = 0 "
            + "<choose>"
            + "<when test='atVersion != null AND atVersion != \"\"'> AND profile_version = #{atVersion} </when>"
            + "<otherwise> AND status = 'PUBLISHED' </otherwise>"
            + "</choose> ORDER BY profile_version DESC"
            + "</script>")
    List<Map<String, Object>> listPublishedSameKey(@Param("profileKey") String profileKey, @Param("atVersion") String atVersion);
}
