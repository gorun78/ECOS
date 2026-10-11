package com.chinacreator.gzcm.runtime.core.monitor.server.warn.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import com.chinacreator.gzcm.runtime.core.monitor.warn.bean.WarnLogBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;

/**
 * PMO-59 P4a / R2-B4c 数据访问收口 — 告警落库通道（{@code ecos_warn_log}, DDL V131）。
 *
 * <p>SQL 文本自原 {@code WarnLogServiceImpl}（..monitor.service.impl..）逐字节下沉；
 * 落库异常由调用方（{@code WarnLogServiceImpl}）负责降级 WARN 不抛出。</p>
 *
 * <p>仅在有 {@link JdbcTemplate} 的容器（gateway / 各 service）内生效；无数据源栈则本 Bean 不装配，
 * {@link com.chinacreator.gzcm.runtime.core.monitor.service.impl.WarnLogServiceImpl} 退化为纯内存态。</p>
 *
 * <p>装配口径（2026-10-11 修订）：本 DAO 无条件装配。原 {@code @ConditionalOnBean(JdbcTemplate.class)}
 * 在 {@code @ComponentScan} 期求值不可靠（自动配置的 JdbcTemplate 在扫描之后才注册），
 * 曾致 workspace 宿主 Bean 缺口；全部宿主均带 DataSource，JdbcTemplate 由
 * {@code JdbcTemplateAutoConfiguration}（或 gateway {@code JdbcConfig} 显式 Bean）稳定提供。</p>
 *
 * <p>架构铁律 §2.5#1 / IR06 收口（R2-B4c）：本类乃数据访问收口点，继承 runtime-core
 * {@link JdbcTemplateDaoSupport}，仅以继承的 {@code update} / {@code queryList} 通道访问；
 * 与 sibling DAO（{@code AlertDao} / {@code AuditRetryDao} / {@code DltDao}）同构。</p>
 */
@Repository
public class WarnLogDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(WarnLogDao.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 告警持久化幂等（ON CONFLICT log_id DO NOTHING）— SQL 逐字保真自原 {@code WarnLogServiceImpl}。 */
    public static final String INSERT_SQL =
        "INSERT INTO ecos_warn_log (id, log_id, warn_type, warn_level, warn_objid, warn_objname, "
            + "warn_message, fault_context, review_tag, warn_hand, warn_result, ishanded, warn_time, "
            + "create_by, update_by) VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?,?,?,?,?) "
            + "ON CONFLICT (log_id) DO NOTHING";

    public static final String UPDATE_RESULT_SQL =
        "UPDATE ecos_warn_log SET warn_result = ?, update_time = NOW() WHERE log_id = ? AND is_deleted = 0";

    public static final String UPDATE_HANDED_SQL =
        "UPDATE ecos_warn_log SET ishanded = '1', update_time = NOW() WHERE log_id = ? AND is_deleted = 0";

    /** 告警写入（INSERT ... ON CONFLICT DO NOTHING）。 */
    public void insert(WarnLogBean row) {
        update(INSERT_SQL,
            "warn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
            row.getLog_id(),
            row.getWarn_type(),
            row.getWarn_level() == null ? "WARN" : row.getWarn_level(),
            row.getWarn_objid(),
            row.getWarn_objname(),
            row.getWarn_message(),
            toJson(row.getFault_context()),
            row.getReview_tag(),
            row.getWarn_hand(),
            row.getWarn_result(),
            row.getIshanded() == null ? "0" : row.getIshanded(),
            row.getWarn_time() == null
                ? new java.sql.Timestamp(System.currentTimeMillis()) : row.getWarn_time(),
            "system", "system");
    }

    /** 单条参数化 UPDATE；sql 通常是 {@link #UPDATE_RESULT_SQL} 或 {@link #UPDATE_HANDED_SQL}。
     * 类型化收口通道：委托继承自 {@link JdbcTemplateDaoSupport} 的
     * {@code protected int update(String, Object...)}（返回受影响行数），签名与父类一致以便合法覆写并对外可见。 */
    public int update(String sql, Object... args) {
        return super.update(sql, args);
    }

    /** 故障上下文序列化（非法/空值置 null；异常降级 WARN 不抛出）。 */
    private String toJson(Map<String, Object> faultContext) {
        if (faultContext == null || faultContext.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(faultContext);
        } catch (Exception e) {
            log.warn("告警 faultContext 序列化失败 (ignored, 该列置空): err={}", e.getMessage());
            return null;
        }
    }
}
