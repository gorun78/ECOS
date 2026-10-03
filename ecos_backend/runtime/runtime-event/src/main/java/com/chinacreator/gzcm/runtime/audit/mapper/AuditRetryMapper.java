package com.chinacreator.gzcm.runtime.audit.mapper;

import java.sql.Timestamp;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.chinacreator.gzcm.runtime.audit.entity.AuditRetryRow;

/**
 * 审计兜底重试 MyBatis Mapper — {@code public.ecos_runtime_audit_retry}（V243，详细设计-00 §6.2 / C.5.4）。
 * <p>
 * C.5.4 状态机：{@code pending}（publish 同步失败落行 attempts=0，next_retry_at=now+60s）
 * → 到期重投 attempts+1 → 达 3 次置 {@code alerted}（critical 告警，ack 必须人工）。
 */
@Mapper
public interface AuditRetryMapper {

    String COLUMNS = "id, event_type AS eventType, payload, attempts, next_retry_at AS nextRetryAt, "
            + "last_error AS lastError, status, trace_id AS traceId, create_time AS createTime, "
            + "update_time AS updateTime, version_no AS versionNo, is_deleted AS isDeleted, domain";

    @Select("SELECT " + COLUMNS + " FROM public.ecos_runtime_audit_retry WHERE id = #{id} AND is_deleted = 0")
    AuditRetryRow findById(@Param("id") String id);

    /** 到期行（status=pending 且 next_retry_at <= now） */
    @Select("SELECT " + COLUMNS + " FROM public.ecos_runtime_audit_retry "
            + "WHERE is_deleted = 0 AND status = 'pending' AND next_retry_at <= #{now} "
            + "ORDER BY next_retry_at ASC LIMIT #{limit}")
    List<AuditRetryRow> listDue(@Param("now") Timestamp now, @Param("limit") int limit);

    @Insert("INSERT INTO public.ecos_runtime_audit_retry "
            + "(id, event_type, payload, attempts, next_retry_at, last_error, status, trace_id, "
            + "create_time, update_time, version_no, is_deleted, domain, tenant_id) "
            + "VALUES (#{id}, #{eventType}, #{payload}, 0, #{nextRetryAt}, #{lastError}, 'pending', "
            + "#{traceId}, NOW(), NOW(), #{versionNo}, 0, #{domain}, #{tenantId})")
    int insert(AuditRetryRow row);

    /** 重投成功：attempts+1、status=replayed */
    @Update("UPDATE public.ecos_runtime_audit_retry SET attempts = attempts + 1, status = 'replayed', "
            + "update_time = NOW() WHERE id = #{id} AND is_deleted = 0")
    int markReplayed(@Param("id") String id);

    /** 重投失败：attempts+1、next_retry_at=入参（指数/线性退避）、last_error=入参；attempts 语义 = 已尝试次数 */
    @Update("UPDATE public.ecos_runtime_audit_retry SET attempts = attempts + 1, next_retry_at = #{nextRetryAt}, "
            + "last_error = #{lastError}, update_time = NOW() WHERE id = #{id} AND is_deleted = 0")
    int markRetryFailed(@Param("id") String id,
                        @Param("nextRetryAt") Timestamp nextRetryAt,
                        @Param("lastError") String lastError);

    /** 达上限：status=alerted */
    @Update("UPDATE public.ecos_runtime_audit_retry SET status = 'alerted', update_time = NOW() "
            + "WHERE id = #{id} AND is_deleted = 0")
    int markAlerted(@Param("id") String id);
}
