package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.model.Caliber;
import com.chinacreator.gzcm.engine.ontology.model.CaliberVersion;

import java.util.List;
import java.util.Optional;

/**
 * 口径（caliber）一等实体领域服务（F03-01 / F03-03 V1 / F03-09 依赖）。
 *
 * <p>口径主权归 ontology（R-6①）：只有本引擎写口径，cognitive / workspace / kb 一律经
 * {@code GET /api/v1/ontology/calibers/{code}/current} 只读消费。
 *
 * <p>口径服务不可用（同 JVM 宿主下 = bean 异常）→ 指标发布 fail-closed，见 F03-01 异常与降级。
 */
public interface CaliberService {

    // ── 读（口径主权：跨引擎只读入口）────────────────────
    List<Caliber> listCalibers();

    Optional<Caliber> findByCode(String code);

    /** 当前生效口径版本 = 该 code 下最新一条 APPROVED 版本。 */
    Optional<CaliberVersion> findCurrentApprovedVersion(String caliberCode);

    /** 判定某口径版本是否 APPROVED（V1 门禁判据）。 */
    boolean isVersionApproved(String caliberId, String versionNo);

    /** 只读消费：取指定口径版本的公式明文（指标发布冻结快照时用）。版本不存在返回 null。 */
    String findVersionFormula(String caliberId, String versionNo);

    // ── 写（仅 ontology 可调用）────────────────────
    /** 创建口径（DRAFT/ACTIVE）。重复 code → 409。 */
    Caliber createCaliber(Caliber caliber, String operator);

    /** 新增一条版本（行不可变；重复 (caliberId,versionNo) → 409 ECOS-ONTO-041）。 */
    CaliberVersion publishCaliberVersion(CaliberVersion version, String operator);

    /** 状态迁移：DRAFT→REVIEWING→APPROVED→RETIRED。APPROVED 后内容不可变。 */
    CaliberVersion transition(String versionId, String targetStatus, String operator);

    // ── 能力 ─────────────────────────────────────────
    /** 口径服务健康；出现异常时上层应 fail-closed（500 ECOS-ONTO-042）。 */
    boolean isAvailable();
}
