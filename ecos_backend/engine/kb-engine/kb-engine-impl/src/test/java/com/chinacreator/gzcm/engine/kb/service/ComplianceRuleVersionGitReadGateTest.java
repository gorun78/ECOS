package com.chinacreator.gzcm.engine.kb.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.engine.kb.dao.ComplianceRuleDao;
import com.chinacreator.gzcm.engine.kb.dto.RuleContentSource;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
import com.chinacreator.gzcm.runtime.access.git.entity.GitCommitView;

/**
 * 规则版本域 DS-4 门禁（2026-11-08）：Git 可用时「提交即版本、零库写」，
 * 历史从 {@code Snapshot compliance rule <id> v<ver>} 提交重建；归档器缺席才走库内回退。
 */
class ComplianceRuleVersionGitReadGateTest {

    private static final String RULE_ID = "rule-1";
    private static final String COMMIT = "c0ffee1234567890abcdef1234567890abcdef12";
    private static final String COMMIT2 = "deadbeef1234567890abcdef1234567890abcdef";

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

    private static GitCommitView commit(String sha, String message, long at) {
        return new GitCommitView(sha, message, "alice", "alice@ecos.local", new Date(at));
    }

    private void archiveReturns(ArchiveOutcome outcome) {
        when(archiver.archive(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any())).thenReturn(outcome);
    }

    // ── 写面：Git 事件化，零库写 ─────────────────────────────

    @Test
    void archivedSnapshotWritesGitOnlyAndUsesReplacedVersion() {
        archiveReturns(ArchiveOutcome.archived(COMMIT, null));

        ArchiveOutcome out = svc.snapshotVersion(ruleAt(3), "alice", 1730000000000L, 4);

        assertEquals(ArchiveOutcome.Status.ARCHIVED, out.status());
        verify(archiver).archive(eq(ComplianceRuleVersionService.GIT_REPO_KB),
                eq(ComplianceRuleVersionService.GIT_ASSET_TYPE_RULES), eq(RULE_ID), eq("3"),
                anyString(), anyString(), any());
        verify(dao, never()).insertVersion(anyString(), anyString(), anyInt(), anyString(),
                anyString(), anyLong(), anyString());
        verify(dao, never()).setGitRef(anyString(), anyString());
    }

    @Test
    void notAvailableSnapshotDoesNotWriteDbOrFakeSuccess() {
        archiveReturns(ArchiveOutcome.notAvailable("仓库工作树未解析"));

        ArchiveOutcome out = svc.snapshotVersion(ruleAt(3), "alice", 1730000000000L, 4);

        assertEquals(ArchiveOutcome.Status.NOT_AVAILABLE, out.status());
        verify(dao, never()).insertVersion(anyString(), anyString(), anyInt(), anyString(),
                anyString(), anyLong(), anyString());
        verify(dao, never()).setGitRef(anyString(), anyString());
    }

    @Test
    void failedSnapshotDoesNotWriteDb() {
        archiveReturns(ArchiveOutcome.failed("Git 提交失败"));

        assertEquals(ArchiveOutcome.Status.FAILED,
                svc.snapshotVersion(ruleAt(3), "alice", 1730000000000L, 4).status());
        verify(dao, never()).insertVersion(anyString(), anyString(), anyInt(), anyString(),
                anyString(), anyLong(), anyString());
    }

    @Test
    void missingChannelKeepsDbRowForLegacyFallback() {
        ArchiveOutcome out = withoutChannel().snapshotVersion(ruleAt(3), "alice", 1730000000000L, 4);

        assertEquals(ArchiveOutcome.Status.NOT_AVAILABLE, out.status());
        verify(dao).insertVersion(anyString(), eq(RULE_ID), eq(3), anyString(), anyString(),
                anyLong(), anyString());
        verify(dao, never()).setGitRef(anyString(), anyString());
    }

    // ── 读面：从 Git 提交重建历史 ────────────────────────────

    @Test
    void historyIsRebuiltFromSnapshotCommitsNewestFirst() {
        when(archiver.listCommits(eq(ComplianceRuleVersionService.GIT_REPO_KB), anyInt()))
                .thenReturn(List.of(
                        commit(COMMIT, "Snapshot compliance rule rule-1 v4", 2000L),
                        commit(COMMIT2, "Snapshot compliance rule rule-1 v3", 1000L)));
        when(archiver.readAtRef(eq(ComplianceRuleVersionService.GIT_REPO_KB),
                eq(ComplianceRuleVersionService.GIT_ASSET_TYPE_RULES), eq(RULE_ID), eq("4"), eq(COMMIT)))
                .thenReturn(Optional.of("{\"v\":4}"));
        when(archiver.readAtRef(eq(ComplianceRuleVersionService.GIT_REPO_KB),
                eq(ComplianceRuleVersionService.GIT_ASSET_TYPE_RULES), eq(RULE_ID), eq("3"), eq(COMMIT2)))
                .thenReturn(Optional.of("{\"v\":3}"));

        List<Map<String, Object>> rows = svc.getVersions(RULE_ID);

        assertEquals(2, rows.size());
        assertEquals(4, rows.get(0).get("version_number"));
        assertEquals("{\"v\":4}", rows.get(0).get("snapshot"));
        assertEquals("git-" + COMMIT, rows.get(0).get("id"));
        assertEquals(RuleContentSource.GIT.name(), rows.get(0).get("content_source"));
        assertEquals(2000L, rows.get(0).get("changed_at"));
        assertEquals(3, rows.get(1).get("version_number"));
        verify(dao, never()).getVersions(anyString());
    }

    @Test
    void missingContentAtCommitMarksFallbackWithReason() {
        when(archiver.listCommits(eq(ComplianceRuleVersionService.GIT_REPO_KB), anyInt()))
                .thenReturn(List.of(commit(COMMIT, "Snapshot compliance rule rule-1 v3", 1000L)));
        when(archiver.readAtRef(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        Map<String, Object> row = svc.getVersions(RULE_ID).get(0);

        assertNull(row.get("snapshot"));
        assertEquals(RuleContentSource.DB_FALLBACK.name(), row.get("content_source"));
        assertNotNull(row.get("content_reason"));
        assertTrue(String.valueOf(row.get("content_reason")).contains(COMMIT));
    }

    @Test
    void filtersOtherRulesAndNonSnapshotCommits() {
        when(archiver.listCommits(eq(ComplianceRuleVersionService.GIT_REPO_KB), anyInt()))
                .thenReturn(List.of(
                        commit("aaa", "Snapshot compliance rule other-rule v9", 3000L),
                        commit("bbb", "chore: unrelated", 2500L),
                        commit(COMMIT, "Snapshot compliance rule rule-1 v2", 1000L)));
        when(archiver.readAtRef(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of("{}"));

        List<Map<String, Object>> rows = svc.getVersions(RULE_ID);

        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).get("version_number"));
        verify(archiver, times(1)).readAtRef(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void withoutChannelReadsDbRowsAsBefore() {
        java.util.Map<String, Object> r = new java.util.HashMap<>();
        r.put("id", "ver-2");
        r.put("rule_id", RULE_ID);
        r.put("version_number", 2);
        r.put("snapshot", "{\"from\":\"db\"}");
        r.put("changed_by", "alice");
        r.put("changed_at", 1730000000000L);
        r.put("change_note", "Updated to version 3");
        r.put("git_ref", COMMIT);
        when(dao.getVersions(RULE_ID)).thenReturn(new java.util.ArrayList<>(List.of(r)));

        Map<String, Object> out = withoutChannel().getVersions(RULE_ID).get(0);

        assertEquals(RuleContentSource.DB_IN_USE.name(), out.get("content_source"));
        assertEquals("{\"from\":\"db\"}", out.get("snapshot"));
        verify(archiver, never()).listCommits(anyString(), anyInt());
    }
}
