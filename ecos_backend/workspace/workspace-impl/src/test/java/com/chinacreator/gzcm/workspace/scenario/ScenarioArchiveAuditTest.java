package com.chinacreator.gzcm.workspace.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;

/**
 * scenario 归档治理审计的门（§2.9.9 G5 的 scenario 域腿；#147BE）。
 *
 * <p>钉三件事：① 归档成功（{@code ARCHIVED}）后才发治理审计，事件带
 * {@code git_repository=scenario-def} 与 {@code git_commit_sha=<真实提交号>}；
 * ② 归档未成功<b>不发</b>（免得「没归档」被读成「已发布」）；
 * ③ 归档器缺席时整条腿跳过、不抛异常。</p>
 *
 * <p>直接调包级方法 {@code archiveScenarioAfterCommit}（无活动事务时它同步执行任务），
 * 从而只测「归档结果 → 审计」这一段，不把状态机/DAO 拉进来。</p>
 *
 * @author ECOS workspace #147BE
 */
class ScenarioArchiveAuditTest {

    @SuppressWarnings("unchecked")
    private static ScenarioStatusTransitionService service(DesignAssetArchiver archiver,
                                                           WorkspaceSecurityAudit audit) {
        ScenarioService scenarioService = mock(ScenarioService.class);
        when(scenarioService.get(anyString())).thenReturn(new ScenarioVO());
        ObjectProvider<DesignAssetArchiver> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(archiver);
        return new ScenarioStatusTransitionService(
                scenarioService,
                mock(ScenarioStatusMachine.class),
                mock(CompletenessDataLoader.class),
                mock(ScenarioCompletenessService.class),
                audit,
                mock(WorkspaceDataDao.class),
                provider);
    }

    @Test
    @DisplayName("归档成功 → 发治理审计，携 scenario-def 与真实提交号")
    @SuppressWarnings("unchecked")
    void archivedEmitsGovernanceAudit() {
        DesignAssetArchiver archiver = mock(DesignAssetArchiver.class);
        when(archiver.archive(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(ArchiveOutcome.archived("deadbeef01", "scenario-def/…"));
        WorkspaceSecurityAudit audit = mock(WorkspaceSecurityAudit.class);

        service(archiver, audit).archiveScenarioAfterCommit("sc-1", ScenarioStatus.ACTIVE);

        ArgumentCaptor<Map<String, Object>> cols = ArgumentCaptor.forClass(Map.class);
        verify(audit).recordGovernance(eq("scenario.status.archive"), eq("sc-1"), eq("SUCCESS"), cols.capture());
        assertEquals("scenario-def", cols.getValue().get("git_repository"));
        assertEquals("deadbeef01", cols.getValue().get("git_commit_sha"), "G5 的核心列：真实提交号");
        assertEquals("scenario", cols.getValue().get("object_type"));
        assertEquals("sc-1", cols.getValue().get("object_id"));
    }

    @Test
    @DisplayName("归档未成功 → 不发治理审计")
    void notArchivedDoesNotEmit() {
        DesignAssetArchiver archiver = mock(DesignAssetArchiver.class);
        when(archiver.archive(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(ArchiveOutcome.notAvailable("仓库未注册"));
        WorkspaceSecurityAudit audit = mock(WorkspaceSecurityAudit.class);

        service(archiver, audit).archiveScenarioAfterCommit("sc-2", ScenarioStatus.ACTIVE);

        verify(audit, never()).recordGovernance(anyString(), anyString(), any(), any());
    }
}
