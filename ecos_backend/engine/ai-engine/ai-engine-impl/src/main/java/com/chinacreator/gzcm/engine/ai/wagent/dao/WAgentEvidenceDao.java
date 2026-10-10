package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentEvidenceDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_evidence}（V240 表①）的 JDBC 落点。
 * <p>引用型证据：只存引用与哈希（{@code snapshot_ref_json}/{@code locator_text}/{@code content_hash}），
 * <b>无内容字段</b>（§6.3 禁业务明细副本）。{@code source_engine} 值名录 = {@link
 * com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog#contractEngineCodesForDdlCheck()}。</p>
 * <p>写方唯一 = ai-engine.wagent；与认知域 {@code ecos_cognitive_evidence}（V127）互不互写（ST09）。
 * {@code classify_level} 密级过滤由 security-engine CLS 侧执行，本层不写策略（ST04/05）。</p>
 */
@Repository
public class WAgentEvidenceDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_evidence";

    private static final String COLUMNS = "id, evidence_type, source_engine, source_object_type, "
            + "source_object_id, source_version, snapshot_ref_json, locator_text, content_hash, classify_level, "
            + "is_valid, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, evidence_type, source_engine, source_object_type, source_object_id, source_version, "
            + "snapshot_ref_json, locator_text, content_hash, classify_level, is_valid, tenant_id, org_id, "
            + "version_no, create_by, update_by) VALUES ("
            + ":id, :evidenceType, :sourceEngine, :sourceObjectType, :sourceObjectId, :sourceVersion, "
            + ":snapshotRefJson, :locatorText, :contentHash, :classifyLevel, :isValid, :tenantId, :orgId, "
            + ":versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "evidence_type = :evidenceType, source_engine = :sourceEngine, "
            + "source_object_type = :sourceObjectType, source_object_id = :sourceObjectId, "
            + "source_version = :sourceVersion, snapshot_ref_json = :snapshotRefJson, "
            + "locator_text = :locatorText, content_hash = :contentHash, classify_level = :classifyLevel, "
            + "is_valid = :isValid, version_no = :versionNo, update_by = :updateBy, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = :id AND is_deleted = 0";

    public int insert(WAgentEvidenceDO evidence) {
        return insertByBean(INSERT_SQL, evidence);
    }

    public Optional<WAgentEvidenceDO> findById(String id) {
        List<WAgentEvidenceDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentEvidenceDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_evidence_src(source_engine, source_object_type)：来源引擎目录面。 */
    public List<WAgentEvidenceDO> findBySource(String sourceEngine, String sourceObjectType) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE source_engine = ? AND source_object_type = ? AND is_deleted = 0 "
                + "ORDER BY create_time DESC", WAgentEvidenceDO.class, sourceEngine, sourceObjectType);
    }

    /** 同源对象版本反查（引用闭合校验：断言引用的对象版本是否存在）。 */
    public List<WAgentEvidenceDO> findBySourceObjectId(String sourceObjectId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE source_object_id = ? AND is_deleted = 0 ORDER BY create_time DESC",
                WAgentEvidenceDO.class, sourceObjectId);
    }

    /** 有效性位翻转（证据失效标记走 is_valid，禁物理删除以保引用闭合可追溯）。 */
    public int updateValid(String id, Integer isValid, String updateBy) {
        return update("UPDATE " + TABLE + " SET is_valid = ?, update_by = ?, update_time = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND is_deleted = 0", isValid, updateBy, id);
    }

    public int update(WAgentEvidenceDO evidence) {
        return updateByBean(UPDATE_SQL, evidence);
    }
}
