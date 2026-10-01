package com.chinacreator.gzcm.gateway.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * B6b 数据访问收口（架构铁律 §2.5 / IR06）：{@code DigitalTwinService}（P3-4/A8）内联的
 * {@code ecos_device_telemetry} 异步持久化裸 SQL 落点。
 *
 * <p>{@code JdbcTemplateDaoSupport} 基类以 {@code @Autowired} 字段持有 JdbcTemplate
 * （gateway 主 Bean 即 {@code TenantAwareJdbcTemplate}，恒在）；沿用 A8 「JdbcTemplate
 * 可选」口径，{@link #optional()} 提供降级判定，启用者守卫与重构前 {@code jdbc != null}
 * 语义一致。</p>
 */
@Repository
public class DeviceTelemetryDao extends JdbcTemplateDaoSupport {

    /** JDBC 是否可用（缺 JdbcTemplate 注入时降级不持久化）。 */
    public boolean optional() {
        return jdbcTemplate != null;
    }

    /** 异步双写一条设备遥测（P3-4/A8）。 */
    public void insertTelemetry(String deviceId, double value) {
        jdbcTemplate.update(
            "INSERT INTO ecos_device_telemetry (device_id, value, recorded_at) VALUES (?, ?, ?)",
            deviceId, value, Timestamp.from(Instant.now()));
    }
}
