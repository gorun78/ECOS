package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentMissionDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_mission}（V228）的 JDBC 落点。
 * <p>模式同 {@code AiToolRegistryDao}：schema 限定表名、显式 COLUMNS、全参数化 SQL，基类
 * {@link JdbcTemplateDaoSupport}。Playbook 内容走 Git 引用（{@code playbook_git_ref}），DB 不存正文（§6.3）。</p>
 * <p>INSERT 省略 DB 默认列（domain/create_time/update_time/is_deleted）；RLS/CLS 在 security-engine 侧（ST04/05）。</p>
 */
@Repository
public class WAgentMissionDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_mission";

    private static final String COLUMNS = "id, goal_id, name, stage, owner_id, due_date, playbook_id, "
            + "playbook_git_ref, playbook_version, is_temporary, blocked_reason_text, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, goal_id, name, stage, owner_id, due_date, playbook_id, playbook_git_ref, playbook_version, "
            + "is_temporary, blocked_reason_text, tenant_id, org_id, version_no, create_by, update_by) VALUES ("
            + ":id, :goalId, :name, :stage, :ownerId, :dueDate, :playbookId, :playbookGitRef, :playbookVersion, "
            + ":isTemporary, :blockedReasonText, :tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "goal_id = :goalId, name = :name, stage = :stage, owner_id = :ownerId, due_date = :dueDate, "
            + "playbook_id = :playbookId, playbook_git_ref = :playbookGitRef, playbook_version = :playbookVersion, "
            + "is_temporary = :isTemporary, blocked_reason_text = :blockedReasonText, "
            + "version_no = :versionNo, update_by = :updateBy, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = :id AND is_deleted = 0";

    public int insert(WAgentMissionDO mission) {
        return insertByBean(INSERT_SQL, mission);
    }

    public Optional<WAgentMissionDO> findById(String id) {
        List<WAgentMissionDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentMissionDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_mission_goal(goal_id)。 */
    public List<WAgentMissionDO> findByGoalId(String goalId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE goal_id = ? AND is_deleted = 0 ORDER BY create_time ASC", WAgentMissionDO.class, goalId);
    }

    /** idx_wagent_mission_tenant(tenant_id, stage)。 */
    public List<WAgentMissionDO> findByTenantAndStage(String tenantId, String stage) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE tenant_id = ? AND stage = ? AND is_deleted = 0 ORDER BY create_time DESC",
                WAgentMissionDO.class, tenantId, stage);
    }

    public int update(WAgentMissionDO mission) {
        return updateByBean(UPDATE_SQL, mission);
    }
}
