package com.chinacreator.gzcm.runtime.core.alert.mapper;

import java.sql.Timestamp;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import com.chinacreator.gzcm.runtime.core.alert.mapper.entity.AlertRecordRow;

/**
 * 告警记录 MyBatis Mapper — {@code public.ecos_runtime_alert_record}（V241，详细设计-00 §6.2）。
 * <p>
 * 状态机：{@code open}（triggerAlert 落库）→ {@code acked}（ackAlert 只 mark 不自动 close）
 * → {@code closed}（closeAlert / resolveAlert 语义映射）。
 * 分页口径（§D.5.3）：{@code occurred_at DESC}，page 包络 {@code {items,total}} 由监控端点组装。
 */
@Mapper
public interface AlertRecordMapper {

    String COLUMNS = "id, rule_code AS ruleCode, severity, status, title, detail_json AS detailJson, "
            + "source_module AS sourceModule, trace_id AS traceId, ack_by AS ackBy, ack_time AS ackTime, "
            + "close_time AS closeTime, occurred_at AS occurredAt, create_time AS createTime, "
            + "update_time AS updateTime, version_no AS versionNo, is_deleted AS isDeleted, domain";

    @Select("SELECT " + COLUMNS + " FROM public.ecos_runtime_alert_record WHERE id = #{id} AND is_deleted = 0")
    AlertRecordRow findById(@Param("id") String id);

    /** 分页查询（occurred_at DESC） */
    @SelectProvider(type = RecordSqlProvider.class, method = "page")
    List<AlertRecordRow> page(@Param("severity") String severity,
                              @Param("status") String status,
                              @Param("limit") int limit,
                              @Param("offset") int offset);

    @SelectProvider(type = RecordSqlProvider.class, method = "count")
    long count(@Param("severity") String severity,
               @Param("status") String status,
               @Param("ruleCode") String ruleCode);

    @Insert("INSERT INTO public.ecos_runtime_alert_record "
            + "(id, rule_code, severity, status, title, detail_json, source_module, trace_id, "
            + "occurred_at, create_time, update_time, version_no, is_deleted, domain, tenant_id) "
            + "VALUES (#{id}, #{ruleCode}, #{severity}, #{status}, #{title}, #{detailJson}, "
            + "#{sourceModule}, #{traceId}, #{occurredAt}, NOW(), NOW(), #{versionNo}, 0, #{domain}, #{tenantId})")
    int insert(AlertRecordRow row);

    @Update("UPDATE public.ecos_runtime_alert_record SET status = 'acked', ack_by = #{ackBy}, "
            + "ack_time = #{ackTime}, update_time = NOW() WHERE id = #{id} AND is_deleted = 0")
    int ack(@Param("id") String id, @Param("ackBy") String ackBy, @Param("ackTime") Timestamp ackTime);

    @Update("UPDATE public.ecos_runtime_alert_record SET status = 'closed', close_time = #{closeTime}, "
            + "update_time = NOW() WHERE id = #{id} AND is_deleted = 0")
    int close(@Param("id") String id, @Param("closeBy") String closeBy, @Param("closeTime") Timestamp closeTime);
}

/** AlertRecordMapper 动态 SQL 提供者（MyBatis Provider 约定：public 无参方法返回 SQL 串） */
final class RecordSqlProvider {

    private static final String TABLE = "public.ecos_runtime_alert_record";

    public String page(@org.apache.ibatis.annotations.Param("severity") String severity,
                       @org.apache.ibatis.annotations.Param("status") String status,
                       @org.apache.ibatis.annotations.Param("limit") int limit,
                       @org.apache.ibatis.annotations.Param("offset") int offset) {
        // Provider 上下文不支持 <script>，用等价的三段式 CASE 拼接（severity/status 为空即免过滤）
        StringBuilder sql = new StringBuilder(320)
                .append("SELECT ").append(AlertRecordMapper.COLUMNS)
                .append(" FROM ").append(TABLE)
                .append(" WHERE is_deleted = 0")
                .append(severity == null || severity.isEmpty() ? "" : " AND severity = #{severity}")
                .append(status == null || status.isEmpty() ? "" : " AND status = #{status}")
                .append(" ORDER BY occurred_at DESC, id")
                .append(" LIMIT #{limit} OFFSET #{offset}");
        return sql.toString();
    }

    public String count(@org.apache.ibatis.annotations.Param("severity") String severity,
                        @org.apache.ibatis.annotations.Param("status") String status,
                        @org.apache.ibatis.annotations.Param("ruleCode") String ruleCode) {
        StringBuilder sql = new StringBuilder(160)
                .append("SELECT COUNT(1) FROM ").append(TABLE)
                .append(" WHERE is_deleted = 0")
                .append(severity == null || severity.isEmpty() ? "" : " AND severity = #{severity}")
                .append(status == null || status.isEmpty() ? "" : " AND status = #{status}")
                .append(ruleCode == null || ruleCode.isEmpty() ? "" : " AND rule_code = #{ruleCode}");
        return sql.toString();
    }
}
