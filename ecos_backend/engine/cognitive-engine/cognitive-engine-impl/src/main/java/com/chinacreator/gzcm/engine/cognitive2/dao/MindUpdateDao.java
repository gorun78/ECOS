package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * R4 Mind 候选更新闸门 DAO（V292 / 详设 §8.4.2 / 差距分析 P0-6）。
 *
 * <p><b>写路径唯一</b>：W Agent（或等价受控提交通道）只允许 INSERT 出
 * {@code status='PROPOSED'}；ACCEPTED／REJECTED 终态只经
 * {@link #finalizeProposal} 受控更新落库（复核留痕四列同写）。
 * 本 DAO 不存在「直写 belief／hypothesis」的任何方法——候选在被受理前
 * 对正式资产零副作用（R4 候选更新闸门纪律，由 {@code MindUpdateGateTest} 钉死）。</p>
 *
 * <p>纪律同 {@link MindAssetDao}：表名 schema 限定、is_deleted=0 恒带、
 * 全列显式、无 PG 专有方言；MC01 主键应用侧 UUID。</p>
 */
@Repository
public class MindUpdateDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(MindUpdateDao.class);

    /** 状态词表单源（DDL CHECK 延后纪律同 V187 头注 4）。 */
    static final String STATUS_PROPOSED  = "PROPOSED";
    static final String STATUS_ACCEPTED  = "ACCEPTED";
    static final String STATUS_REJECTED  = "REJECTED";

    static final String MIND_UPDATE_TABLE = "ecos_cognitive.ecos_cognitive_mind_update";

    static final String UPDATE_COLUMNS =
            "id, mind_id, agent_run_id, target_type, target_id, change_json, evidence_refs_json, " +
            "status, reviewed_by, reviewed_at, review_reason, trace_id, " +
            "domain, version_no, is_deleted, create_by, update_by, create_time, update_time";

    /** 闸门表 schema 预检（缺表 → 503 ECOS-COG-501，同 mind 属主表先例）。 */
    public boolean mindUpdateTableExists() {
        try {
            Boolean present = jdbcTemplate.queryForObject(
                    "SELECT to_regclass(?) IS NOT NULL",
                    Boolean.class, MIND_UPDATE_TABLE);
            return Boolean.TRUE.equals(present);
        } catch (Exception e) {
            log.error("schema 预检通道不可用：to_regclass({}) 探测失败", MIND_UPDATE_TABLE, e);
            throw new CognitiveGuardException(CognitiveErrorCode.E501_SCHEMA_NOT_READY,
                    CognitiveErrorCode.MSG_SCHEMA_NOT_READY, MIND_UPDATE_TABLE);
        }
    }

    /**
     * 提交候选（唯一允许产生 PROPOSED 行的写入口径）。
     *
     * @return 落库后整行（id = 传入的应用侧 UUID）。
     */
    public Map<String, Object> insertProposed(String id, String mindId, String agentRunId,
                                              String targetType, String targetId,
                                              String changeJson, String evidenceRefsJson,
                                              String operator) {
        int n = jdbcTemplate.update(
                "INSERT INTO " + MIND_UPDATE_TABLE + " " +
                "(id, mind_id, agent_run_id, target_type, target_id, change_json, evidence_refs_json, " +
                " status, domain, version_no, is_deleted, create_by, update_by, create_time, update_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'default', '1', 0, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, mindId, agentRunId, targetType, targetId, changeJson,
                evidenceRefsJson == null ? "[]" : evidenceRefsJson,
                STATUS_PROPOSED, operator, operator);
        if (n != 1) {
            throw new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                    CognitiveErrorCode.MSG_INTERNAL, MIND_UPDATE_TABLE);
        }
        return findById(id);
    }

    /** 回读单行（is_deleted=0；未找到 → 404U）。 */
    public Map<String, Object> findById(String updateId) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT " + UPDATE_COLUMNS + " FROM " + MIND_UPDATE_TABLE +
                    " WHERE id = ? AND is_deleted = 0", updateId);
        } catch (EmptyResultDataAccessException e) {
            throw new CognitiveGuardException(CognitiveErrorCode.E404_MIND_UPDATE,
                    CognitiveErrorCode.MSG_MIND_UPDATE_NOT_FOUND);
        }
    }

    /** 按 Mind 列候选（列表倒序，受控端点_PAGE 上限由上层 limit 控制）。 */
    public List<Map<String, Object>> listByMind(String mindId, int limit) {
        int lim = Math.max(1, Math.min(limit, 100));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + UPDATE_COLUMNS + " FROM " + MIND_UPDATE_TABLE +
                " WHERE mind_id = ? AND is_deleted = 0 ORDER BY create_time DESC, id DESC LIMIT " + lim,
                mindId);
        return rows == null ? new ArrayList<>() : rows;
    }

    /**
     * 受控置终态（唯一写 ACCEPTED／REJECTED 的入口径）。
     *
     * @param newStatus ACCEPTED 或 REJECTED（调用方经词表校验后传入字面）。
     * @return 更新行数。<b>0 = 行缺失或已终态</b>（…0 由上层区分 404U / 409U）。
     */
    public int finalizeProposal(String updateId, String newStatus, String reviewedBy,
                                String reviewReason, String operator) {
        int n = jdbcTemplate.update(
                "UPDATE " + MIND_UPDATE_TABLE +
                "    SET status = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP, " +
                "        review_reason = ?, update_by = ?, update_time = CURRENT_TIMESTAMP " +
                "  WHERE id = ? AND is_deleted = 0 AND status = ?",
                newStatus, reviewedBy, reviewReason, operator, updateId, STATUS_PROPOSED);
        return n;
    }
}
