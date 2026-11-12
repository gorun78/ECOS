package com.chinacreator.gzcm.engine.ai.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.engine.ai.dataobject.AipPipelineDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

/**
 * 数据访问收口（铁律 §2.5 #1 / IR06）：{@code ecos_ai.ecos_aip_pipeline}（V329）JDBC 落点。
 *
 * <p>AI 工作台逻辑编排定义的持久化唯一通道；原 {@code AIPPipelineController} 的
 * {@code ConcurrentHashMap} 内存 store 由此取代（刷新即丢的 P0 缺陷修复）。
 * 与 wagent 域同款形态：DAO extends {@code JdbcTemplateDaoSupport}，不建 MyBatis Mapper。</p>
 *
 * <p>租户隔离：所有读路径按 {@code tenant_id} 过滤（REQ-PLT-24/25 承），
 * tenant 由服务端身份解析注入，禁取请求体自报值。</p>
 */
@Repository
public class AipPipelineDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_aip_pipeline";

    private static final String COLUMNS = "id, tenant_id, name, description, definition_json, status, "
            + "template_ref, version_no, last_executed_at, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, tenant_id, name, description, definition_json, status, template_ref, version_no, "
            + "create_by, update_by) VALUES ("
            + ":id, :tenantId, :name, :description, :definitionJson, :status, :templateRef, :versionNo, "
            + ":createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "name = :name, description = :description, definition_json = :definitionJson, "
            + "status = :status, update_by = :updateBy, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = :id AND tenant_id = :tenantId AND is_deleted = 0";

    public int insert(AipPipelineDO pipeline) {
        return insertByBean(INSERT_SQL, pipeline);
    }

    public Optional<AipPipelineDO> findById(String tenantId, String id) {
        List<AipPipelineDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND tenant_id = ? AND is_deleted = 0", AipPipelineDO.class, id, tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** 列表（tenant 隔离，最新在前）。 */
    public List<AipPipelineDO> findAll(String tenantId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE tenant_id = ? AND is_deleted = 0 ORDER BY update_time DESC, create_time DESC",
                AipPipelineDO.class, tenantId);
    }

    public int update(AipPipelineDO pipeline) {
        return updateByBean(UPDATE_SQL, pipeline);
    }

    /** 逻辑删除（IR03 只加不删 ⇒ 不物理 DELETE）。 */
    public int softDelete(String tenantId, String id, String updateBy) {
        return update("UPDATE " + TABLE + " SET is_deleted = 1, update_by = ?, "
                + "update_time = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND is_deleted = 0",
                updateBy, id, tenantId);
    }

    /** 执行回写：状态 + 最近执行时刻 + 更新时间。 */
    public int markExecuted(String tenantId, String id, String status, String updateBy) {
        return update("UPDATE " + TABLE + " SET status = ?, last_executed_at = CURRENT_TIMESTAMP, "
                + "update_by = ?, update_time = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND tenant_id = ? AND is_deleted = 0",
                status, updateBy, id, tenantId);
    }

    /**
     * 已发布候选来源行（批 3 模板来源扩面）：只取本租户已发布候选的 id/name/payload_json。
     * 属同域（ai-engine 读 {@code ecos_ai}）表，符合 ST07「引擎只读写自有 schema」。
     *
     * <p>状态字面量取 {@code WAgentEnums.CandidateStatus.PUBLISHED.value()} = 小写
     * {@code 'published'}（V238 CHECK 十值全小写）——写成大写会静默零行。</p>
     */
    public List<Map<String, Object>> queryCandidateTemplates(String tenantId) {
        return jdbcTemplate.queryForList("SELECT id, name, payload_json "
                + "FROM ecos_ai.ecos_wagent_candidate "
                + "WHERE tenant_id = ? AND status = 'published' AND is_deleted = 0 "
                + "ORDER BY update_time DESC", tenantId);
    }
}
