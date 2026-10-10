package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunEventDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_run_event}（V235）的 JDBC 落点。
 * <p><b>append-only</b>：只提供 insert/find，<b>禁</b> update/delete（V235 声明的应用层纪律；
 * 表 X-6 不分区，冷数据由 runtime-monitor 归档）。</p>
 * <p>{@code event_type} 词表单源 = {@code EventTypes.Agent}（common-api），本表不复制词表、DDL 不设 CHECK
 * （V235 声明纪律）；{@code payload_ref_text} 引用型载荷，禁正文（§6.3）；审计事件不落本表，
 * 一律 Kafka {@code ecos.audit}（ST06/X-20）。</p>
 * <p>SSE 通道（§5.2 RUN_EVENTS_SSE）续传读法：{@link #findByRunIdSince} 按 occur_time 正序回放。</p>
 */
@Repository
public class WAgentRunEventDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_run_event";

    private static final String COLUMNS = "id, run_id, step_id, event_type, payload_ref_text, occur_time, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, run_id, step_id, event_type, payload_ref_text, occur_time, tenant_id, org_id, version_no, "
            + "create_by, update_by) VALUES (:id, :runId, :stepId, :eventType, :payloadRefText, :occurTime, "
            + ":tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    /** 追加事件（append-only 唯一写面）。 */
    public int insert(WAgentRunEventDO event) {
        return insertByBean(INSERT_SQL, event);
    }

    public Optional<WAgentRunEventDO> findById(String id) {
        List<WAgentRunEventDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentRunEventDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_event_run(run_id, occur_time)：正序回放。 */
    public List<WAgentRunEventDO> findByRunId(String runId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE run_id = ? AND is_deleted = 0 ORDER BY occur_time ASC, create_time ASC",
                WAgentRunEventDO.class, runId);
    }

    /** 续传读法：仅取 since 之后的事件（含边界由调用方按 last-event-id 语义决定）。 */
    public List<WAgentRunEventDO> findByRunIdSince(String runId, LocalDateTime since) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE run_id = ? AND occur_time >= ? AND is_deleted = 0 ORDER BY occur_time ASC",
                WAgentRunEventDO.class, runId, since);
    }

    public List<WAgentRunEventDO> findByStepId(String stepId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE step_id = ? AND is_deleted = 0 ORDER BY occur_time ASC", WAgentRunEventDO.class, stepId);
    }

    /** append-only：本类无 update/delete 方法——需要更正事实时追加补偿事件，禁改写历史。 */
}
