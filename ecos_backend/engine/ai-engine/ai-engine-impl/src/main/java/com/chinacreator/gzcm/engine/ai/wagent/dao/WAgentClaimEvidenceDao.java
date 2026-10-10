package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentClaimEvidenceDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_claim_evidence}（V240 表③）的 JDBC 落点。
 * <p><b>复合主键 {@code (claim_id, evidence_id)}</b>，无单列 id（V240 明载）⇒ 定位方法为
 * {@link #findByClaimIdAndEvidenceId}，{@link #update} 也以两列作 WHERE 条件。</p>
 * <p>{@code relation} 值域（SUPPORTS/CONTRADICTS/DERIVES 等）未定版，DDL 不设 CHECK（R-15 纪律）；
 * 证据主权在 {@link WAgentEvidenceDao} 侧，本表只表达绑定与覆盖率（{@code cover_ratio} NUMERIC(5,4)）。
 * 引用闭合校验（断言↔证据双向存在）由服务层用两个 DAO 完成，禁跨 schema 直连。</p>
 */
@Repository
public class WAgentClaimEvidenceDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_claim_evidence";

    private static final String COLUMNS = "claim_id, evidence_id, relation, cover_ratio, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "claim_id, evidence_id, relation, cover_ratio, tenant_id, org_id, version_no, create_by, update_by) "
            + "VALUES (:claimId, :evidenceId, :relation, :coverRatio, :tenantId, :orgId, :versionNo, "
            + ":createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "relation = :relation, cover_ratio = :coverRatio, version_no = :versionNo, update_by = :updateBy, "
            + "update_time = CURRENT_TIMESTAMP WHERE claim_id = :claimId AND evidence_id = :evidenceId "
            + "AND is_deleted = 0";

    public int insert(WAgentClaimEvidenceDO relation) {
        return insertByBean(INSERT_SQL, relation);
    }

    /** 复合主键定位（替代 findById）。 */
    public Optional<WAgentClaimEvidenceDO> findByClaimIdAndEvidenceId(String claimId, String evidenceId) {
        List<WAgentClaimEvidenceDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE claim_id = ? AND evidence_id = ? AND is_deleted = 0",
                WAgentClaimEvidenceDO.class, claimId, evidenceId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** 断言的证据集（引用闭合与覆盖率计算入口）。 */
    public List<WAgentClaimEvidenceDO> findByClaimId(String claimId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE claim_id = ? AND is_deleted = 0", WAgentClaimEvidenceDO.class, claimId);
    }

    /** idx_wagent_claim_ev_evidence(evidence_id)：证据的反向引用面（失效影响分析）。 */
    public List<WAgentClaimEvidenceDO> findByEvidenceId(String evidenceId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE evidence_id = ? AND is_deleted = 0", WAgentClaimEvidenceDO.class, evidenceId);
    }

    public int update(WAgentClaimEvidenceDO relation) {
        return updateByBean(UPDATE_SQL, relation);
    }
}
