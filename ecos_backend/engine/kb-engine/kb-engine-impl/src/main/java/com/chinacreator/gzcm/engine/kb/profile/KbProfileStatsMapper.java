package com.chinacreator.gzcm.engine.kb.profile;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 历史画像·数值统计事实 Mapper（表 {@code ecos_dw.ecos_kb_profile_stats}，V176）。
 * <p>
 * R-8 ②：数值事实落业务域 {@code ecos_dw}，本模块是 **可读**（resolve 时取数值）方；
 * **写**方 = data-engine 写通道（ADR-14）。kb 侧为了在 self-contained 单测中能直接驱动该路径，
 * 本类同时提供 insert（供 {@code ProfileStatsWriter} 在 dev / 单测态直接代写；生产上 data-engine
 * 代管时可关闭——生产 impl 见 {@code KbProfileStatsWriterDirectImpl}）。
 *
 * @author ECOS KB Team
 */
@Mapper
public interface KbProfileStatsMapper {

    @Insert("INSERT INTO ecos_dw.ecos_kb_profile_stats ("
            + "id, profile_id, profile_key, sample_count, missing_rate, p10, p50, p90, mean_value, "
            + "ci_low, ci_high, window_from, window_to, stats_batch_id, trace_id, domain, version_no, "
            + "is_deleted, create_by, update_by, create_time, update_time) "
            + "VALUES (#{id}, #{profileId}, #{profileKey}, #{sampleCount}, #{missingRate}, "
            + "#{p10}, #{p50}, #{p90}, #{meanValue}, #{ciLow}, #{ciHigh}, #{windowFrom}, #{windowTo}, "
            + "#{statsBatchId}, #{traceId}, #{domain}, '1', 0, #{createBy}, #{updateBy}, #{createTime}, #{updateTime})")
    int insert(@Param("id") String id,
               @Param("profileId") String profileId,
               @Param("profileKey") String profileKey,
               @Param("sampleCount") Integer sampleCount,
               @Param("missingRate") BigDecimal missingRate,
               @Param("p10") BigDecimal p10,
               @Param("p50") BigDecimal p50,
               @Param("p90") BigDecimal p90,
               @Param("meanValue") BigDecimal meanValue,
               @Param("ciLow") BigDecimal ciLow,
               @Param("ciHigh") BigDecimal ciHigh,
               @Param("windowFrom") String windowFrom,
               @Param("windowTo") String windowTo,
               @Param("statsBatchId") String statsBatchId,
               @Param("traceId") String traceId,
               @Param("domain") String domain,
               @Param("createBy") String createBy,
               @Param("updateBy") String updateBy,
               @Param("createTime") Date createTime,
               @Param("updateTime") Date updateTime);

    /** 按 stats_ref（= 本表 id）取单数值事实行，供 resolve 组装。 */
    @Select("SELECT id, profile_id AS profileId, profile_key AS profileKey, sample_count AS sampleCount, "
            + "missing_rate AS missingRate, p10, p50, p90, mean_value AS meanValue, ci_low AS ciLow, "
            + "ci_high AS ciHigh, window_from AS windowFrom, window_to AS windowTo, "
            + "stats_batch_id AS statsBatchId, trace_id AS traceId "
            + "FROM ecos_dw.ecos_kb_profile_stats WHERE id = #{statsRef} AND is_deleted = 0")
    Map<String, Object> findById(@Param("statsRef") String statsRef);

    /** 兼容批量取回（想多键组装）。 */
    @Select("<script>"
            + "SELECT id, profile_id AS profileId, profile_key AS profileKey, sample_count AS sampleCount, "
            + "missing_rate AS missingRate, p10, p50, p90, mean_value AS meanValue, ci_low AS ciLow, "
            + "ci_high AS ciHigh, window_from AS windowFrom, window_to AS windowTo, "
            + "stats_batch_id AS statsBatchId, trace_id AS traceId "
            + "FROM ecos_dw.ecos_kb_profile_stats WHERE id IN "
            + "<foreach item='item' index='index' collection='statsRefs' open='(' separator=',' close=')'>#{item}</foreach> "
            + "AND is_deleted = 0"
            + "</script>")
    List<Map<String, Object>> findByIds(@Param("statsRefs") List<String> statsRefs);
}
