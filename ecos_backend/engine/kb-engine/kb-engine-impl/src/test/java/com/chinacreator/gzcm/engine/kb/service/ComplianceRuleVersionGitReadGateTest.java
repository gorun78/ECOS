package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dao.ComplianceRuleDao;
import com.chinacreator.gzcm.engine.kb.dto.RuleContentSource;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #147V 验收 — 规则版本历史读切 Git（DS-4 rule 域；§2.9.5②「历史只从 Git 取」+ 铁律 #8）。
 *
 * <p>四条契约，逐条可红：</p>
 * <ol>
 *   <li><b>引用持久化</b>：归档 {@code ARCHIVED} 才回填 {@code git_ref}，且回填的是真实 commitId；
 *       {@code NOT_AVAILABLE}/{@code FAILED} 一律留空（禁伪成功）；回填自身失败不得报成功。</li>
 *   <li><b>归档号与行号同值</b>：对象库路径按版本号定位，写入腿必须用版本行的
 *       {@code version_number}（被替换的那一版），不得用 {@code newVersion}——错一位则按行读回必空。</li>
 *   <li><b>读面三态</b>：{@code GIT}（读到，内容置换）／{@code DB_FALLBACK}（读不到，退回 DB 且带原因）／
 *       {@code DB_IN_USE}（未归档或通道未装配）。</li>
 *   <li><b>零 Git 往返</b>：未归档行不去敲对象库；Git 读次数 = 带 ref 行数（禁列表放大）。</li>
 * </ol>
 *
 * <p>全部走 Mockito，不启 Spring 上下文。</p>
 */
class ComplianceRuleVersionGitReadGateTest {

    private static final String RULE_ID = "rule-1";
    private static final String COMMIT = "c0ffee1234567890abcdef1234567890abcdef12";

    private ComplianceRuleDao dao;
    private DesignAssetArchiver archiver;
    private ObjectProvider<DesignAssetArchiver> provider;
    private ComplianceRuleVersionService svc;

    @BeforeEach
    void setUp() {
        dao = mock(ComplianceRuleDao.class);
        archiver = mock(DesignAssetArchiver.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(archiver);
        svc = new ComplianceRuleVersionService(dao, provider);
    }

    /** 归档通道未装配的现状态（部署期未注册仓库/禁用 = 合法，不是失败）。 */
    private ComplianceRuleVersionService withoutChannel() {
        ObjectProvider<DesignAssetArchiver> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);
        return new ComplianceRuleVersionService(dao, empty);
    }

    private static ComplianceRule ruleAt(int version) {
        ComplianceRule r = new ComplianceRule();
        r.setId(RULE_ID);
        r.setName("合规规则");
        r.setVersion(version);
        return r;
    }

    /** 版本行 Map（列名 snake_case，与 queryForList 行一致；git_ref 由 V305 引入）。 */
    private static Map<String, Object> row(int versionNumber, String gitRef, String dbSnapshot) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", "ver-" + versionNumber);
        m.put("rule_id", RULE_ID);
        m.put("version_number", versionNumber);
        m.put("snapshot", dbSnapshot);
        m.put("changed_by", "alice");
        m.put("changed_at", 1730000000000L);
        m.put("change_note", "Updated to version " + (versionNumber + 1));
        m.put("git_ref", gitRef);
        return m;
    }

    private ArchiveOutcome snapshot(ComplianceRule existing, int newVersion) {
        return svc.snapshotVersion(existing, "alice", 1730000000000L, newVersion);
    }

    // ── 契约 1：引用持久化（禁伪成功）────────────────────────────

    @Test
    void archivedLegPersistsRealCommitId() {
        when(archiver.archive(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any())).thenReturn(ArchiveOutcome.archived(COMMIT, "rules/" + RULE_ID + "/3.json"));

        ArchiveOutcome out = snapshot(ruleAt(3), 4);

        assertEquals(ArchiveOutcome.Status.ARCHIVED, out.status());
        verify(dao).setGitRef(anyString(), eq(COMMIT));
    }

    @Test
    void notAvailableLegWritesNoRefButStillStoresVersionRow() {
        when(archiver.archive(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any())).thenReturn(ArchiveOutcome.notAvailable("仓库工作树未解析"));

        ArchiveOutcome out = snapshot(ruleAt(3), 4);

        assertEquals(ArchiveOutcome.Status.NOT_AVAILABLE, out.status());
        verify(dao, never()).setGitRef(anyString(), anyString());
        // 归档未成不得阻断版本入库（可用性优先）——否则版本历史直接断档
        verify(dao).insertVersion(anyString(), eq(RULE_ID), eq(3), anyString(), anyString(),
                anyLong(), anyString());
    }

    @Test
    void failedLegWritesNoRef() {
        when(archiver.archive(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any())).thenReturn(ArchiveOutcome.failed("Git 提交失败"));

        assertEquals(ArchiveOutcome.Status.FAILED, snapshot(ruleAt(3), 4).status());
        verify(dao, never()).setGitRef(anyString(), anyString());
    }

    @Test
    void refBackfillFailureIsReportedAsFailedNotAsArchived() {
        when(archiver.archive(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any())).thenReturn(ArchiveOutcome.archived(COMMIT, "rules/" + RULE_ID + "/3.json"));
        when(dao.setGitRef(anyString(), anyString())).thenThrow(new RuntimeException("db down"));

        ArchiveOutcome out = snapshot(ruleAt(3), 4);

        // 对象库有提交、索引列没落住 = 半成功，如实降级为 FAILED 并带上提交号；禁按 ARCHIVED 报上去
        assertEquals(ArchiveOutcome.Status.FAILED, out.status());
        assertNotNull(out.reason());
        assertTrue(out.reason().contains(COMMIT), "原因须带已产生的提交号，供对账： " + out.reason());
    }

    // ── 契约 2：归档号与行号同值（能变红的定位腿）─────────────────

    @Test
    void archiveLegUsesTheRowVersionNumberNotTheNextVersion() {
        when(archiver.archive(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any())).thenReturn(ArchiveOutcome.archived(COMMIT, null));

        snapshot(ruleAt(3), 4);

        // 版本行存的是被替换的 v3（insertVersion 的 version_number=3），对象库路径必须同为 v3；
        // 写成 v4 会让「按行号 + ref 读回」落空并把 v3 内容冒充成 v4
        verify(archiver).archive(eq(ComplianceRuleVersionService.GIT_REPO_KB),
                eq(ComplianceRuleVersionService.GIT_ASSET_TYPE_RULES), eq(RULE_ID), eq("3"),
                anyString(), anyString(), any());
        verify(dao).insertVersion(anyString(), eq(RULE_ID), eq(3), anyString(), anyString(),
                anyLong(), anyString());
    }

    @Test
    void missingArchiverKeepsRowWithoutRefAndDoesNotThrow() {
        ArchiveOutcome out = withoutChannel().snapshotVersion(ruleAt(3), "alice", 1730000000000L, 4);

        assertEquals(ArchiveOutcome.Status.NOT_AVAILABLE, out.status());
        verify(dao, never()).setGitRef(anyString(), anyString());
    }

    // ── 契约 3：读面三态 ────────────────────────────────────────

    @Test
    void refBearingRowIsReadFromGitAndStamped() {
        Map<String, Object> r = row(3, COMMIT, "{\"from\":\"db\"}");
        when(dao.getVersions(RULE_ID)).thenReturn(new ArrayList<>(List.of(r)));
        when(archiver.readAtRef(ComplianceRuleVersionService.GIT_REPO_KB,
                ComplianceRuleVersionService.GIT_ASSET_TYPE_RULES, RULE_ID, "3", COMMIT))
                .thenReturn(Optional.of("{\"from\":\"git\"}"));

        Map<String, Object> out = svc.getVersions(RULE_ID).get(0);

        assertEquals("{\"from\":\"git\"}", out.get("snapshot"), "已归档行内容必须换成 Git 读回值");
        assertEquals(RuleContentSource.GIT.name(), out.get("content_source"));
        assertNull(out.get("content_reason"), "GIT 态不带退回原因");
    }

    @Test
    void refBearingRowWithGitMissFallsBackToDbWithReason() {
        Map<String, Object> r = row(3, COMMIT, "{\"from\":\"db\"}");
        when(dao.getVersions(RULE_ID)).thenReturn(new ArrayList<>(List.of(r)));
        when(archiver.readAtRef(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        Map<String, Object> out = svc.getVersions(RULE_ID).get(0);

        assertEquals("{\"from\":\"db\"}", out.get("snapshot"));
        assertEquals(RuleContentSource.DB_FALLBACK.name(), out.get("content_source"));
        String reason = (String) out.get("content_reason");
        assertNotNull(reason, "DB_FALLBACK 必须给原因，禁静默把 DB 快照当 Git 历史");
        assertTrue(reason.contains(COMMIT), "原因须含 ref 供对账: " + reason);
    }

    @Test
    void unarchivedRowStaysDbInUseAndNeverTouchesGit() {
        Map<String, Object> r = row(2, null, "{\"from\":\"db\"}");
        when(dao.getVersions(RULE_ID)).thenReturn(new ArrayList<>(List.of(r)));

        Map<String, Object> out = svc.getVersions(RULE_ID).get(0);

        assertEquals(RuleContentSource.DB_IN_USE.name(), out.get("content_source"));
        assertEquals("{\"from\":\"db\"}", out.get("snapshot"));
        assertNull(out.get("content_reason"));
        verify(archiver, never()).readAtRef(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void absentChannelIsNeverClaimedAsGitSourced() {
        Map<String, Object> r = row(3, COMMIT, "{\"from\":\"db\"}");
        when(dao.getVersions(RULE_ID)).thenReturn(new ArrayList<>(List.of(r)));

        Map<String, Object> out = withoutChannel().getVersions(RULE_ID).get(0);

        // ref 在、通道没装配 ≠ 「内容来自 Git」；本腿防的是把「有引用」读成「已切 Git」
        assertEquals(RuleContentSource.DB_IN_USE.name(), out.get("content_source"));
        assertEquals("{\"from\":\"db\"}", out.get("snapshot"));
    }

    @Test
    void rowWithoutVersionNumberFallsBackInsteadOfReadingLatest() {
        Map<String, Object> r = row(3, COMMIT, "{\"from\":\"db\"}");
        r.put("version_number", null);
        when(dao.getVersions(RULE_ID)).thenReturn(new ArrayList<>(List.of(r)));

        Map<String, Object> out = svc.getVersions(RULE_ID).get(0);

        // readAtRef 对空 versionNo 退到 "latest"，会读回别的版本冒充本行 ⇒ 定位键缺失时禁止去读
        verify(archiver, never()).readAtRef(anyString(), anyString(), anyString(), anyString(), anyString());
        assertEquals(RuleContentSource.DB_FALLBACK.name(), out.get("content_source"));
        assertNotNull(out.get("content_reason"));
    }

    @Test
    void blankRefStringIsTreatedAsUnarchived() {
        Map<String, Object> r = row(3, "   ", "{\"from\":\"db\"}");
        when(dao.getVersions(RULE_ID)).thenReturn(new ArrayList<>(List.of(r)));

        Map<String, Object> out = svc.getVersions(RULE_ID).get(0);

        assertEquals(RuleContentSource.DB_IN_USE.name(), out.get("content_source"));
        verify(archiver, never()).readAtRef(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    // ── 契约 4：Git 读次数 = 带 ref 行数（禁列表放大）─────────────

    @Test
    void gitReadCountEqualsRefBearingRowsOnly() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row(1, null, "{\"v\":1}"),
                row(2, COMMIT, "{\"v\":2}"),
                row(3, "", "{\"v\":3}"),
                row(4, COMMIT, "{\"v\":4}")));
        when(dao.getVersions(RULE_ID)).thenReturn(rows);
        when(archiver.readAtRef(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of("{\"from\":\"git\"}"));

        List<Map<String, Object>> out = svc.getVersions(RULE_ID);

        assertEquals(4, out.size(), "行数不得被读面改变");
        verify(archiver, times(2)).readAtRef(anyString(), anyString(), anyString(), anyString(), anyString());
        // 带 ref 的两行（v2/v4）走 Git，未归档/空 ref 的两行（v1/v3）零 Git 往返
        assertEquals(RuleContentSource.GIT.name(), out.get(1).get("content_source"));
        assertEquals(RuleContentSource.GIT.name(), out.get(3).get("content_source"));
        assertEquals(RuleContentSource.DB_IN_USE.name(), out.get(0).get("content_source"));
        assertEquals(RuleContentSource.DB_IN_USE.name(), out.get(2).get("content_source"));
    }

    @Test
    void readFaceKeepsExistingKeysUntouched() {
        Map<String, Object> r = row(3, null, "{\"from\":\"db\"}");
        when(dao.getVersions(RULE_ID)).thenReturn(new ArrayList<>(List.of(r)));

        Map<String, Object> out = svc.getVersions(RULE_ID).get(0);

        // 只增键不改既有键（API 只增不改红线）
        for (String key : List.of("id", "rule_id", "version_number", "snapshot",
                "changed_by", "changed_at", "change_note", "git_ref")) {
            assertTrue(out.containsKey(key), "既有键不得丢: " + key);
        }
        assertFalse(out.isEmpty());
        verify(dao).getVersions(RULE_ID);
    }
}
