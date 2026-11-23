package com.chinacreator.gzcm.workspace.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintService;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintVO;
import com.chinacreator.gzcm.workspace.scenario.CompletenessDataLoader;
import com.chinacreator.gzcm.workspace.scenario.ScenarioCompletenessService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioVO;
import com.chinacreator.gzcm.workspace.scenario.WorkspaceSecurityAudit;

/**
 * 场景定义发布归档腿的门（场景工作台简化 v0.3，§3.5 / §7.4）。
 *
 * <p>钉四件事：① 归档成功才发 {@code scenario_definition_archive} 审计，事件带
 * {@code commitId} 与 {@code contentHash}；② 归档未成功（仓库未注册）= 发
 * {@code scenario_definition_archive_missed} 而不是伪成功；③ 归档器缺席（provider 返回 null）
 * 不抛异常、如实记 missed；④ 归档参数落点正确（仓 {@code scenario-def}、目录 {@code scenarios}、
 * 版本号、提交信息带 contentHash）。</p>
 */
class ScenarioDefinitionArchiveTest {

    @SuppressWarnings("unchecked")
    private static ScenarioDefinitionService service(DesignAssetArchiver archiver,
                                                     WorkspaceSecurityAudit audit) {
        ScenarioService scenarioService = mock(ScenarioService.class);
        ScenarioVO vo = new ScenarioVO();
        vo.setId("sc-1");
        when(scenarioService.get(anyString())).thenReturn(vo);
        ObjectProvider<DesignAssetArchiver> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(archiver);
        return new ScenarioDefinitionService(
                scenarioService,
                mock(ScenarioBlueprintService.class),
                mock(ScenarioCompletenessService.class),
                mock(CompletenessDataLoader.class),
                audit,
                provider);
    }

    private static ScenarioBlueprintVO released() {
        ScenarioBlueprintVO vo = new ScenarioBlueprintVO();
        vo.setId("bp-1");
        vo.setScenarioId("sc-1");
        vo.setVersion(7);
        vo.setStatus("RELEASED");
        vo.setGoalStatement("目标");
        vo.setSuccessCriteria(List.of("s1"));
        vo.setArtifactDigest("artifact-sha-1");
        vo.setContextDigest("ctx-sha-1");
        return vo;
    }

    @Test
    @DisplayName("归档成功 → 发 archive 审计，带真实 commitId 与 contentHash")
    @SuppressWarnings("unchecked")
    void archivedEmitsAuditWithCommit() {
        DesignAssetArchiver archiver = mock(DesignAssetArchiver.class);
        when(archiver.archive(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(ArchiveOutcome.archived("commit-abc123", null));
        WorkspaceSecurityAudit audit = mock(WorkspaceSecurityAudit.class);

        service(archiver, audit)
                .archiveReleasedDefinitionAfterCommit("sc-1", "7", "hash-xyz", released(), "u-admin");

        ArgumentCaptor<Map<String, Object>> cols = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq("scenario_definition_archive"), eq("sc-1"), cols.capture());
        assertEquals("commit-abc123", cols.getValue().get("commitId"));
        assertEquals("hash-xyz", cols.getValue().get("contentHash"));
        assertEquals("7", cols.getValue().get("version"));
    }

    @Test
    @DisplayName("归档参数：仓 scenario-def / 目录 scenarios / 版本号 / 提交信息带 contentHash")
    void archiveUsesOwnerChannel() {
        DesignAssetArchiver archiver = mock(DesignAssetArchiver.class);
        when(archiver.archive(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(ArchiveOutcome.archived("c1", null));

        service(archiver, mock(WorkspaceSecurityAudit.class))
                .archiveReleasedDefinitionAfterCommit("sc-1", "7", "hash-xyz", released(), "u-admin");

        ArgumentCaptor<String> repo = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ver = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(archiver).archive(repo.capture(), type.capture(), eq("sc-1"), ver.capture(),
                content.capture(), message.capture(), eq("u-admin"));
        assertEquals("scenario-def", repo.getValue());
        assertEquals("scenarios", type.getValue());
        assertEquals("7", ver.getValue());
        assertTrue(message.getValue().contains("contentHash=hash-xyz"), "提交信息须可反查内容摘要");
        assertTrue(content.getValue().contains("\"version\":\"7\""), "快照须带版本");
        assertTrue(content.getValue().contains("hash-xyz"), "快照须带 contentHash");
        assertNotNull(content.getValue());
    }

    @Test
    @DisplayName("归档未成功（仓库未注册）→ 发 missed 审计而非伪成功")
    void notAvailableEmitsMissed() {
        DesignAssetArchiver archiver = mock(DesignAssetArchiver.class);
        when(archiver.archive(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(ArchiveOutcome.notAvailable("仓库未注册: scenario-def"));
        WorkspaceSecurityAudit audit = mock(WorkspaceSecurityAudit.class);

        service(archiver, audit)
                .archiveReleasedDefinitionAfterCommit("sc-1", "7", "h", released(), "u-admin");

        ArgumentCaptor<Map<String, Object>> cols = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq("scenario_definition_archive_missed"), eq("sc-1"), cols.capture());
        assertEquals("NOT_AVAILABLE", cols.getValue().get("status"));
        assertTrue(String.valueOf(cols.getValue().get("reason")).contains("仓库未注册"));
    }

    @Test
    @DisplayName("归档器缺席 → 不抛异常，如实记 missed（禁伪成功）")
    @SuppressWarnings("unchecked")
    void absentArchiverDoesNotThrow() {
        WorkspaceSecurityAudit audit = mock(WorkspaceSecurityAudit.class);

        service(null, audit).archiveReleasedDefinitionAfterCommit("sc-1", "7", "h", released(), "u-admin");

        ArgumentCaptor<Map<String, Object>> cols = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq("scenario_definition_archive_missed"), eq("sc-1"), cols.capture());
        assertEquals("NOT_AVAILABLE", cols.getValue().get("status"));
    }
}
