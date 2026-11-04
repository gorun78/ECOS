package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dao.ComplianceRuleDao;
import com.chinacreator.gzcm.engine.kb.dto.RuleContentSource;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ComplianceRuleVersionService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceRuleVersionService.class);

    /** 注册表 repositoryId（V300 种子；§2.9.7 全域接入的 kb 属主）。 */
    static final String GIT_REPO_KB = "kb-asset-tree";

    /** 归档资产类型目录（对象库路径 {@code rules/{ruleId}/{versionNo}.json} 的首段）。 */
    static final String GIT_ASSET_TYPE_RULES = "rules";

    /** 版本行 Map 键（DAO 列为 snake_case，读面增键同名，禁两套拼写）。 */
    private static final String KEY_SNAPSHOT = "snapshot";
    private static final String KEY_GIT_REF = "git_ref";
    private static final String KEY_VERSION_NUMBER = "version_number";
    private static final String KEY_CONTENT_SOURCE = "content_source";
    private static final String KEY_CONTENT_REASON = "content_reason";

    private final ComplianceRuleDao complianceRuleDao;

    /** 设计资产归档器（#147E）；未装配时归档腿显式 NOT_APPLICABLE，不影响版本入库。 */
    private final ObjectProvider<DesignAssetArchiver> archiverProvider;

    /** 治理审计发布器（§2.9.9 G5 rule 域收口腿）；未装配时本地 log 兜底，不阻断版本入库。 */
    @Autowired(required = false)
    private com.chinacreator.gzcm.engine.kb.support.KbAuditEventPublisher auditPublisher;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /** 测试/非 Spring 装配（归档腿缺省）。 */
    public ComplianceRuleVersionService(ComplianceRuleDao complianceRuleDao) {
        this(complianceRuleDao, null);
    }

    @Autowired
    public ComplianceRuleVersionService(ComplianceRuleDao complianceRuleDao,
                                        ObjectProvider<DesignAssetArchiver> archiverProvider) {
        this.complianceRuleDao = complianceRuleDao;
        this.archiverProvider = archiverProvider;
    }

    /**
     * 版本快照：将当前版本写入 sys_rule_version，并归档到 {@code kb-asset-tree}（#147E）。
     *
     * <p>归档失败不阻断版本入库（可用性优先），但结果如实落日志/返回值：
     * {@code NOT_AVAILABLE}（未注册/工作树未解析）与 {@code FAILED} 分态记录，
     * 禁按「已归档」理解（§2.9.6① 三腿分别记录）。</p>
     *
     * <p>#147V 补两条（原缺口）：</p>
     * <ul>
     *   <li><b>归档号与行号同值</b>：版本行的 {@code version_number} 存的是被替换的那一版
     *       （{@code existing.getVersion()}），而对象库路径按 {@code rules/{id}/{ver}.json} 定位。
     *       原实现把归档腿写成 {@code newVersion} ⇒ 写入路径与行号错一位，按行号读回必然拿不到
     *       本行内容（且会把上一版内容冒充成新版）。现统一用行内版本号。</li>
     *   <li><b>引用持久化</b>：归档成功（{@link ArchiveOutcome#isArchived()}）才回填
     *       {@code git_ref}；未成功一律留空（禁伪成功），读侧据此分态而非猜。</li>
     * </ul>
     */
    public ArchiveOutcome snapshotVersion(ComplianceRule existing, String approvedBy, long now, int newVersion) {
        String versionId = UUID.randomUUID().toString();
        String json = toJson(existing);
        int archivedVersion = existing.getVersion();
        complianceRuleDao.insertVersion(
            versionId,
            existing.getId(),
            archivedVersion,
            json,
            approvedBy,
            now,
            "Updated to version " + newVersion
        );
        ArchiveOutcome outcome = archive(existing, json, archivedVersion, approvedBy);
        if (!outcome.isArchived()) {
            if (outcome.status() != ArchiveOutcome.Status.NOT_AVAILABLE) {
                log.warn("kb 规则版本未归档，git_ref 留空（读侧将标 DB_IN_USE）ruleId={} version={} status={} reason={}",
                        existing.getId(), archivedVersion, outcome.status(), outcome.reason());
            }
            return outcome;
        }
        try {
            complianceRuleDao.setGitRef(versionId, outcome.commitId());
        } catch (RuntimeException e) {
            // 对象库已有提交、索引列却没落住 = 半成功，按失败如实上报（与归档器「打标签失败但已有 commit」同法）
            log.error("kb 规则版本 git_ref 回填失败（提交已产生）versionId={} commitId={} ruleId={} version={}",
                    versionId, outcome.commitId(), existing.getId(), archivedVersion, e);
            return ArchiveOutcome.failed("git_ref 回填失败（提交号 " + outcome.commitId()
                    + " 已产生，索引未落住）: " + e.getMessage());
        }
        log.info("kb 规则版本归档引用已落 versionId={} ruleId={} version={} git_ref={}",
                versionId, existing.getId(), archivedVersion, outcome.commitId());
        auditRuleVersionPublish(existing, archivedVersion, outcome.commitId(), approvedBy, json);
        return outcome;
    }

    /** 归档规则快照（KB 属主的 {@code kb-asset-tree} 通道）；{@code versionNo} 与版本行的 {@code version_number} 同值。 */
    private ArchiveOutcome archive(ComplianceRule rule, String json, int versionNo, String approvedBy) {
        // VG-5（#147BL）：统一经 OptionalArchiver——归档器缺席即 not_available + WARN，
        // 不再各域各写一段「取不到就跳过」（跳过没有返回值，调用链上看不见）。
        ArchiveOutcome outcome = com.chinacreator.gzcm.runtime.access.git.archive.OptionalArchiver.of(archiverProvider)
                .archive(GIT_REPO_KB, GIT_ASSET_TYPE_RULES, rule.getId(), String.valueOf(versionNo), json,
                        "Snapshot compliance rule " + rule.getId() + " v" + versionNo, approvedBy);
        if (outcome.status() == ArchiveOutcome.Status.FAILED) {
            log.warn("kb 规则归档失败 ruleId={} reason={}", rule.getId(), outcome.reason());
        }
        return outcome;
    }

    /**
     * 查询规则版本历史（#147V：已归档行按 {@code git_ref} 从对象库读回）。
     *
     * <p>三面分态（{@link RuleContentSource}）：{@code git_ref} 非空且读回成功 ⇒ {@code GIT}
     * 且 {@code snapshot} 换成 Git 内容；非空但读不到 ⇒ {@code DB_FALLBACK} + 原因，内容退回 DB 列；
     * 空或归档器未装配 ⇒ {@code DB_IN_USE}，且<b>零 Git 往返</b>（未归档行不去敲对象库）。
     * 门面只增键不改既有键，{@code snapshot} 语义保持「返回原文、不解析」。</p>
     */
    public List<Map<String, Object>> getVersions(String ruleId) {
        List<Map<String, Object>> rows = complianceRuleDao.getVersions(ruleId);
        if (rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        for (Map<String, Object> row : rows) {
            applyContentSource(row, ruleId);
        }
        return rows;
    }

    /** 单行内容来源判定与内容置换（谓词单源，禁在别处复制）。 */
    private void applyContentSource(Map<String, Object> row, String ruleId) {
        String ref = trimToNull(row.get(KEY_GIT_REF));
        DesignAssetArchiver archiver = ref == null ? null : availableArchiver();
        if (ref == null || archiver == null) {
            row.put(KEY_CONTENT_SOURCE, RuleContentSource.DB_IN_USE.name());
            return;
        }
        String versionNo = trimToNull(row.get(KEY_VERSION_NUMBER));
        if (versionNo == null) {
            // 行缺定位键 = 对象库路径无从构造；此处不得让 readAtRef 退到 "latest"（会读回别的版本冒充本行）
            row.put(KEY_CONTENT_SOURCE, RuleContentSource.DB_FALLBACK.name());
            row.put(KEY_CONTENT_REASON, "版本行缺 version_number，无法定位对象库路径（ref=" + ref + "）");
            log.warn("kb 规则版本行缺 version_number，退回 DB 快照 ruleId={} ref={}", ruleId, ref);
            return;
        }
        Optional<String> content = archiver.readAtRef(GIT_REPO_KB, GIT_ASSET_TYPE_RULES, ruleId, versionNo, ref);
        if (content.isPresent()) {
            row.put(KEY_SNAPSHOT, content.get());
            row.put(KEY_CONTENT_SOURCE, RuleContentSource.GIT.name());
            return;
        }
        row.put(KEY_CONTENT_SOURCE, RuleContentSource.DB_FALLBACK.name());
        row.put(KEY_CONTENT_REASON, "按 git ref 读回为空（ref=" + ref + " path=" + GIT_ASSET_TYPE_RULES
                + "/" + ruleId + "/" + versionNo + ".json），退回权威库快照列");
        log.warn("kb 规则历史版本按 git ref 读回失败，退回 DB 快照（来源已标注）ruleId={} version={} ref={}",
                ruleId, versionNo, ref);
    }

    /** 归档器可用性判定（未装配 = 合法现状态，不冒充 Git 来源）。 */
    private DesignAssetArchiver availableArchiver() {
        return archiverProvider != null ? archiverProvider.getIfAvailable() : null;
    }

    private static String trimToNull(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    // ── 简易JSON序列化 ────────────────────────────

    private String toJson(ComplianceRule rule) {
        try {
            return MAPPER.writeValueAsString(rule);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ComplianceRule to JSON: id={}", rule.getId(), e);
            return "{}";
        }
    }

    /**
     * 规则版本发布的治理审计（§2.9.9 G5「其余发布域的 `git_commit_sha`」的 rule 域腿）。
     *
     * <p>只在<b>归档成功且 git_ref 已落</b>之后调用：审计事件携带
     * {@code git_repository=kb-asset-tree} / {@code git_commit_sha=实际提交号} /
     * {@code content_hash}，使发布证据可被 {@code PublishAuditLookup} 关联；归档没成就不发，
     * 免得「没归档」被读成「已发布」。</p>
     *
     * <p>投递失败按 kb 先例降级 {@code log.error}、不阻断版本入库（审计是证据腿，不是业务前置）。</p>
     */
    private void auditRuleVersionPublish(ComplianceRule rule, int versionNo, String commitId,
                                        String approvedBy, String json) {
        if (auditPublisher == null) {
            log.warn("[AUDIT-LOCAL-FALLBACK] 治理审计发布器未装配（rule.version.publish 未投递）"
                    + " ruleId={} version={} git_ref={}", rule.getId(), versionNo, commitId);
            return;
        }
        try {
            com.chinacreator.gzcm.common.audit.AuditEventContract contract =
                    new com.chinacreator.gzcm.common.audit.AuditEventContract(
                            "rule-ver-" + rule.getId() + "-" + versionNo,
                            null,
                            approvedBy,
                            List.of(),
                            "knowledge",
                            "rule_version",
                            rule.getId(),
                            com.chinacreator.gzcm.common.audit.AuditLevel.L3_HIGH_RISK.name(),
                            GIT_REPO_KB,
                            commitId,
                            null,
                            sha256Hex(json),
                            null,
                            null,
                            null,
                            null,
                            null,
                            "APPLIED",
                            "PUBLISH_ARCHIVED",
                            java.time.Instant.now(),
                            UUID.randomUUID().toString());
            auditPublisher.audit("rule.version.publish",
                    Map.of("ruleId", rule.getId(), "version", versionNo),
                    "kb-rule-version", "SUCCESS", contract.toColumns());
        } catch (RuntimeException e) {
            log.error("规则版本发布审计投递失败（版本已入库、git_ref 已落；证据见本地兜底 log）"
                    + " ruleId={} version={} reason={}", rule.getId(), versionNo, e.getMessage());
        }
    }

    /** 内容摘要（审计 content_hash）；无内容返回 {@code null}（不伪造空串）。 */
    private static String sha256Hex(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return "sha256:" + sb;
        } catch (java.security.NoSuchAlgorithmException e) {
            return null;
        }
    }
}
