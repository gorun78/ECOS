package com.chinacreator.gzcm.runtime.core.alert.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.chinacreator.gzcm.runtime.core.alert.mapper.entity.AlertRuleRow;

/**
 * 告警规则 MyBatis Mapper — {@code public.ecos_runtime_alert_rule}（V241，详细设计-00 §6.2）。
 * <p>
 * 与 DqRuleMapper 同风格：注解 SQL + 显式 AS 别名（宿主 SqlSessionFactory 的
 * mapUnderscoreToCamelCase 经 variables 配置，未落到 config，故不依赖隐式驼峰映射）。
 * 列宽/类型与 V241 DDL 严格一致（MC01 应用侧 UUID、MC02 白名单类型）。
 * 读列清单刻意精简（排除 webhook_url / tenant_id 等低频列），IR04 无 SELECT *。
 */
@Mapper
public interface AlertRuleMapper {

    String COLUMNS = "id, rule_code AS ruleCode, metric_key AS metricKey, severity, "
            + "threshold_num AS thresholdNum, window_sec AS windowSec, upgrade_min AS upgradeMin, "
            + "enabled, channel, "
            + "create_time AS createTime, update_time AS updateTime, create_by AS createBy, "
            + "update_by AS updateBy, version_no AS versionNo, is_deleted AS isDeleted, "
            + "domain";

    @Select("SELECT " + COLUMNS + " FROM public.ecos_runtime_alert_rule WHERE id = #{id} AND is_deleted = 0")
    AlertRuleRow findById(@Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM public.ecos_runtime_alert_rule WHERE rule_code = #{ruleCode} AND is_deleted = 0 LIMIT 1")
    AlertRuleRow findByRuleCode(@Param("ruleCode") String ruleCode);

    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM public.ecos_runtime_alert_rule WHERE is_deleted = 0 "
            + "<if test='enabled != null and enabled != \"\"'>"
            + " AND enabled = CASE WHEN #{enabled} = '1' OR LOWER(#{enabled}) = 'true' THEN 1 ELSE 0 END"
            + " </if>"
            + " ORDER BY create_time DESC, id"
            + "</script>")
    List<AlertRuleRow> listByFilter(@Param("enabled") String enabled);

    @Insert("INSERT INTO public.ecos_runtime_alert_rule "
            + "(id, rule_code, metric_key, severity, threshold_num, window_sec, upgrade_min, "
            + "enabled, channel, webhook_url, create_time, update_time, create_by, update_by, "
            + "version_no, is_deleted, domain, tenant_id) "
            + "VALUES (#{id}, #{ruleCode}, #{metricKey}, #{severity}, #{thresholdNum}, #{windowSec}, "
            + "#{upgradeMin}, #{enabled}, #{channel}, #{webhookUrl}, "
            + "NOW(), NOW(), #{createBy}, #{updateBy}, #{versionNo}, 0, #{domain}, #{tenantId})")
    int insert(AlertRuleRow row);

    @Update("UPDATE public.ecos_runtime_alert_rule SET rule_code = #{ruleCode}, metric_key = #{metricKey}, "
            + "severity = #{severity}, threshold_num = #{thresholdNum}, window_sec = #{windowSec}, "
            + "upgrade_min = #{upgradeMin}, enabled = #{enabled}, channel = #{channel}, "
            + "webhook_url = #{webhookUrl}, update_time = NOW(), update_by = #{updateBy}, "
            + "version_no = #{versionNo}, tenant_id = #{tenantId} "
            + "WHERE id = #{id} AND is_deleted = 0")
    int update(AlertRuleRow row);

    /** 软删（schema 只加不删，R9 — is_deleted=1） */
    @Update("UPDATE public.ecos_runtime_alert_rule SET is_deleted = 1, update_time = NOW(), "
            + "update_by = #{updateBy} WHERE id = #{id} AND is_deleted = 0")
    int softDelete(@Param("id") String id, @Param("updateBy") String updateBy);
}
