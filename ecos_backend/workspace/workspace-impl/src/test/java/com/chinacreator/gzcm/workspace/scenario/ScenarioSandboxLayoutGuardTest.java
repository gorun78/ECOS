package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 画布写入口白名单断言（遗留 P1-7）：saveLayout 必须过 SandboxWriteGuard，未知 runMode 拒绝。 */
class ScenarioSandboxLayoutGuardTest {

    private final WorkspaceDataDao dao = mock(WorkspaceDataDao.class);
    private final ScenarioSandboxLayoutService service =
            new ScenarioSandboxLayoutService(dao, new ObjectMapper(), new SandboxWriteGuard());

    private SandboxSaveDTO dto(String runMode) {
        SandboxSaveDTO d = new SandboxSaveDTO();
        d.setNodes(List.of());
        d.setEdges(List.of());
        d.setViewport(java.util.Map.of("zoom", 1.0));
        d.setExpectedVersion(0);
        d.setRunMode(runMode);
        return d;
    }

    private void stubInsertPath() {
        when(dao.layoutRows(anyString())).thenReturn(List.of());
        when(dao.updateLayoutVersion(anyString(), anyString(), anyString(), anyInt())).thenReturn(0);
        when(dao.layoutRowExists(anyString())).thenReturn(null);
    }

    @Test
    void unknownRunModeIsRejectedBeforeAnyWrite() {
        assertThrows(InvalidRunModeException.class, () -> service.saveLayout("s1", dto("SANDBX")));
        verify(dao, never()).updateLayoutVersion(anyString(), anyString(), anyString(), anyInt());
        verify(dao, never()).insertLayout(anyString(), anyString(), anyString());
    }

    @Test
    void sandboxModePassesWhitelistedLayoutTable() {
        stubInsertPath();
        SandboxLayoutVO vo = service.saveLayout("s1", dto("SANDBOX"));
        assertEquals("s1", vo.getScenarioId());
        verify(dao).insertLayout(eq("s1"), anyString(), anyString());
    }

    @Test
    void missingRunModeDefaultsToFormalAndStillAsserts() {
        stubInsertPath();
        SandboxLayoutVO vo = service.saveLayout("s1", dto(null));
        assertEquals("s1", vo.getScenarioId());
        verify(dao).insertLayout(eq("s1"), anyString(), anyString());
    }
}
