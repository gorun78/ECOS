package com.chinacreator.gzcm.engine.kb.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.engine.kb.dao.ComplianceRuleDao;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.engine.kb.support.KbAuditEventPublisher;
import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;

/**
 * 规则版本发布治理审计的门（§2.9.9 G5 的 rule 域腿；#147BC）。
 *
 * <p>钉三件事：① <b>归档成功且 git_ref 已落</b>之后才发治理审计，事件带
 * {@code git_repository=kb-asset-tree} 与 {@code git_commit_sha=<真实提交号>}（这正是 G5
 * 「其余发布域的 git_commit_sha」缺的那一列）；② <b>归档未成功不发</b>——否则「没归档」
 * 会被读成「已发布」；③ 发布器缺席/投递抛错<b>不阻断版本入库</b>（审计是证据腿）。</p>
 *
 * @author ECOS KB #147BC
 */
class ComplianceRuleVersionAuditTest {

    private static ComplianceRule rule(String id, int version) {
        ComplianceRule r = new ComplianceRule();
        r.setId(id);
        r.setVersion(version);
        return r;
    }

    @SuppressWarnings("unchecked")
    private static ComplianceRuleVersionService service(DesignAssetArchiver archiver,
                                                       KbAuditEventPublisher publisher) throws Exception {
        ObjectProvider<DesignAssetArchiver> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(archiver);
        ComplianceRuleVersionService svc =
                new ComplianceRuleVersionService(mock(ComplianceRuleDao.class), provider);
        java.lang.reflect.Field f = ComplianceRuleVersionService.class.getDeclaredField("auditPublisher");
        f.setAccessible(true);
        f.set(svc, publisher);
        return svc;
    }

    @Test
    @DisplayName("归档成功 → 发治理审计，事件携 git_repository/git_commit_sha/content_hash")
    @SuppressWarnings("unchecked")
    void archivedVersionEmitsGovernanceAudit() throws Exception {
        DesignAssetArchiver archiver = mock(DesignAssetArchiver.class);
        when(archiver.archive(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(ArchiveOutcome.archived("c0ffee123", "kb-asset-tree/v3"));
        KbAuditEventPublisher publisher = mock(KbAuditEventPublisher.class);
        ComplianceRuleVersionService svc = service(archiver, publisher);

        ArchiveOutcome outcome = svc.snapshotVersion(rule("r-1", 3), "approver-1", 1_700_000_000_000L, 4);
        assertTrue(outcome.isArchived(), "前提：归档成功");

        ArgumentCaptor<Map<String, Object>> cols = ArgumentCaptor.forClass(Map.class);
        verify(publisher).audit(eq("rule.version.publish"), any(), eq("kb-rule-version"),
                eq("SUCCESS"), cols.capture());
        Map<String, Object> columns = cols.getValue();
        assertEquals("kb-asset-tree", columns.get("git_repository"));
        assertEquals("c0ffee123", columns.get("git_commit_sha"), "G5 的核心列：真实提交号");
        assertNotNull(columns.get("content_hash"), "内容摘要应随审计落库");
        assertEquals("rule_version", columns.get("object_type"));
        assertEquals("r-1", columns.get("object_id"));
    }

    @Test
    @DisplayName("归档未成功（NOT_AVAILABLE）→ 不发治理审计（免得「没归档」被读成「已发布」）")
    void notArchivedDoesNotEmit() throws Exception {
        DesignAssetArchiver archiver = mock(DesignAssetArchiver.class);
        when(archiver.archive(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(ArchiveOutcome.notAvailable("仓库未注册"));
        KbAuditEventPublisher publisher = mock(KbAuditEventPublisher.class);
        ComplianceRuleVersionService svc = service(archiver, publisher);

        svc.snapshotVersion(rule("r-2", 1), "approver-2", 1_700_000_000_000L, 2);

        verify(publisher, never()).audit(anyString(), any(), anyString(), anyString(), any());
    }
}
