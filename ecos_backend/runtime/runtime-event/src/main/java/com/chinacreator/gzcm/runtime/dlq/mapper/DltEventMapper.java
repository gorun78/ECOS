package com.chinacreator.gzcm.runtime.dlq.mapper;

import java.sql.Timestamp;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import com.chinacreator.gzcm.runtime.dlq.entity.DltEventRow;

/**
 * Kafka DLQ MyBatis Mapper — {@code public.ecos_runtime_event_dlq}（V242，详细设计-00 §6.2）。
 * <p>与 DqRuleMapper 同风格：注解 SQL + 显式 AS 别名（宿主 Configuration 未设
 * mapUnderscoreToCamelCase 属性，不依赖隐式驼峰映射）。
 */
@Mapper
public interface DltEventMapper {

    String COLUMNS = "id, topic, dlt_topic AS dltTopic, payload, error_message AS errorMessage, "
            + "attempts, first_seen_at AS firstSeenAt, replayed_at AS replayedAt, status, "
            + "trace_id AS traceId, create_time AS createTime, update_time AS updateTime, "
            + "version_no AS versionNo, is_deleted AS isDeleted, domain";

    @Select("SELECT " + COLUMNS + " FROM public.ecos_runtime_event_dlq WHERE id = #{id} AND is_deleted = 0")
    DltEventRow findById(@Param("id") String id);

    @SelectProvider(type = DltSqlProvider.class, method = "page")
    List<DltEventRow> page(@Param("status") String status,
                           @Param("limit") int limit,
                           @Param("offset") int offset);

    @SelectProvider(type = DltSqlProvider.class, method = "count")
    long count(@Param("status") String status);

    @Insert("INSERT INTO public.ecos_runtime_event_dlq "
            + "(id, topic, dlt_topic, payload, error_message, attempts, first_seen_at, status, "
            + "trace_id, create_time, update_time, version_no, is_deleted, domain, tenant_id) "
            + "VALUES (#{id}, #{topic}, #{dltTopic}, #{payload}, #{errorMessage}, "
            + "0, #{firstSeenAt}, 'pending', #{traceId}, NOW(), NOW(), #{versionNo}, 0, #{domain}, #{tenantId})")
    int insert(DltEventRow row);

    /** 重放标记：attempts+1、status=replayed、replayed_at=now */
    @Update("UPDATE public.ecos_runtime_event_dlq SET attempts = attempts + 1, status = 'replayed', "
            + "replayed_at = #{replayedAt}, update_time = NOW() "
            + "WHERE id = #{id} AND is_deleted = 0")
    int markReplayed(@Param("id") String id, @Param("replayedAt") Timestamp replayedAt);
}

/** DltEventMapper 动态 SQL 提供者（Provider 上下文不使用 <script>，显式条件拼接） */
final class DltSqlProvider {

    private static final String TABLE = "public.ecos_runtime_event_dlq";

    public String page(@Param("status") String status,
                       @Param("limit") int limit,
                       @Param("offset") int offset) {
        StringBuilder sql = new StringBuilder(256)
                .append("SELECT ").append(DltEventMapper.COLUMNS)
                .append(" FROM ").append(TABLE)
                .append(" WHERE is_deleted = 0")
                .append(status == null || status.isEmpty() ? "" : " AND status = #{status}")
                .append(" ORDER BY first_seen_at DESC, id")
                .append(" LIMIT #{limit} OFFSET #{offset}");
        return sql.toString();
    }

    public String count(@Param("status") String status) {
        StringBuilder sql = new StringBuilder(128)
                .append("SELECT COUNT(1) FROM ").append(TABLE)
                .append(" WHERE is_deleted = 0")
                .append(status == null || status.isEmpty() ? "" : " AND status = #{status}");
        return sql.toString();
    }
}
