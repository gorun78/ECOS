package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentSkillDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_skill}（V237）的 JDBC 落点。
 * <p>铁律 §3.1：DB 只存在用版本元数据，技能全文走 Git 归档（{@code content_git_ref}）；
 * Git 通道单源在 gitService（本任务不触碰），本 DAO 只读写目录行。</p>
 * <p>与遗留 {@code com.chinacreator.gzcm.engine.ai.dao.SkillDao}（{@code ecos_skill}）不同表不同域：
 * 存量收编动作待 R-41，本类禁读写旧表。</p>
 */
@Repository
public class WAgentSkillDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_skill";

    private static final String COLUMNS = "id, name, version, summary, content_git_ref, content_version, "
            + "bound_playbooks_json, owner, status, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, name, version, summary, content_git_ref, content_version, bound_playbooks_json, owner, status, "
            + "tenant_id, org_id, version_no, create_by, update_by) VALUES ("
            + ":id, :name, :version, :summary, :contentGitRef, :contentVersion, :boundPlaybooksJson, :owner, "
            + ":status, :tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "name = :name, version = :version, summary = :summary, content_git_ref = :contentGitRef, "
            + "content_version = :contentVersion, bound_playbooks_json = :boundPlaybooksJson, owner = :owner, "
            + "status = :status, version_no = :versionNo, update_by = :updateBy, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = :id AND is_deleted = 0";

    public int insert(WAgentSkillDO skill) {
        return insertByBean(INSERT_SQL, skill);
    }

    public Optional<WAgentSkillDO> findById(String id) {
        List<WAgentSkillDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentSkillDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** uniq_wagent_skill_name_ver(name, version, is_deleted)。 */
    public Optional<WAgentSkillDO> findByNameAndVersion(String name, String version) {
        List<WAgentSkillDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE name = ? AND version = ? AND is_deleted = 0", WAgentSkillDO.class, name, version);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_skill_status(status)。 */
    public List<WAgentSkillDO> findByStatus(String status) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE status = ? AND is_deleted = 0 ORDER BY name ASC, version DESC",
                WAgentSkillDO.class, status);
    }

    public int update(WAgentSkillDO skill) {
        return updateByBean(UPDATE_SQL, skill);
    }
}
