package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentGoalDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2；架构铁律 §2.5 #1）：{@code ecos_ai.ecos_wagent_goal}（V227）的 JDBC 落点。
 * <p>模式同 {@code AiToolRegistryDao}：表名 schema 限定（DR01/ST07）、COLUMNS 显式（禁 SELECT *，列序变更
 * 会静默错位）、SQL 全参数化；基类 = {@link JdbcTemplateDaoSupport}，禁裸 JdbcTemplate、禁新建基类。</p>
 * <p>词表单源 = {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.GoalIntent}（应用层先校验，
 * DDL CHECK 为第二段防线）。RLS/CLS 过滤在 security-engine REST 侧，本层不写策略（ST04/ST05）。</p>
 * <p>INSERT 省略 DB 默认列（domain/create_time/update_time/is_deleted）——显式传 null 会违 NOT NULL，
 * 默认值只在列缺省时生效；tenant_id/version_no 无默认，由调用方赋值。</p>
 * <p>表未实跑（§14.4）⇒ 运行期读侧先探活（{@code AiToolRegistryDao.tableReady} 同法），禁静默当空表。</p>
 */
@Repository
public class WAgentGoalDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_goal";

    /** 显式列清单（V227 全列）。 */
    private static final String COLUMNS = "id, name, goal_type, scope_json, time_range_text, as_of_date, "
            + "metric_id, metric_version, caliber_version, success_criteria_text, owner_id, due_date, status, "
            + "slots_source_json, inferred_count, supersedes_goal_id, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, name, goal_type, scope_json, time_range_text, as_of_date, metric_id, metric_version, "
            + "caliber_version, success_criteria_text, owner_id, due_date, status, slots_source_json, "
            + "inferred_count, supersedes_goal_id, tenant_id, org_id, version_no, create_by, update_by) VALUES ("
            + ":id, :name, :goalType, :scopeJson, :timeRangeText, :asOfDate, :metricId, :metricVersion, "
            + ":caliberVersion, :successCriteriaText, :ownerId, :dueDate, :status, :slotsSourceJson, "
            + ":inferredCount, :supersedesGoalId, :tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "name = :name, goal_type = :goalType, scope_json = :scopeJson, time_range_text = :timeRangeText, "
            + "as_of_date = :asOfDate, metric_id = :metricId, metric_version = :metricVersion, "
            + "caliber_version = :caliberVersion, success_criteria_text = :successCriteriaText, "
            + "owner_id = :ownerId, due_date = :dueDate, status = :status, slots_source_json = :slotsSourceJson, "
            + "inferred_count = :inferredCount, supersedes_goal_id = :supersedesGoalId, "
            + "version_no = :versionNo, update_by = :updateBy, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = :id AND is_deleted = 0";

    /** 新增 Goal（id/tenant_id/version_no 由应用侧写入：MC01 应用 UUID、DR07 版本位）。 */
    public int insert(WAgentGoalDO goal) {
        return insertByBean(INSERT_SQL, goal);
    }

    public Optional<WAgentGoalDO> findById(String id) {
        return first(queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentGoalDO.class, id));
    }

    /** idx_wagent_goal_tenant(tenant_id, status)。 */
    public List<WAgentGoalDO> findByTenantAndStatus(String tenantId, String status) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE tenant_id = ? AND status = ? AND is_deleted = 0 ORDER BY create_time DESC",
                WAgentGoalDO.class, tenantId, status);
    }

    /** idx_wagent_goal_owner(owner_id)。 */
    public List<WAgentGoalDO> findByOwnerId(String ownerId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE owner_id = ? AND is_deleted = 0 ORDER BY create_time DESC", WAgentGoalDO.class, ownerId);
    }

    /** Goal 版本链：前代 Goal 的直接后继（supersedes_goal_id 逻辑引用同表，ST09 无 FK）。 */
    public List<WAgentGoalDO> findBySupersedesGoalId(String supersedesGoalId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE supersedes_goal_id = ? AND is_deleted = 0 ORDER BY version_no DESC",
                WAgentGoalDO.class, supersedesGoalId);
    }

    public int update(WAgentGoalDO goal) {
        return updateByBean(UPDATE_SQL, goal);
    }

    private static Optional<WAgentGoalDO> first(List<WAgentGoalDO> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
