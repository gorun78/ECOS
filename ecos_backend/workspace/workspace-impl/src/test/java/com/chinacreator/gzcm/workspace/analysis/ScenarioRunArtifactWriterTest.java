package com.chinacreator.gzcm.workspace.analysis;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.common.service.IObjectStorageService;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;

/** 产物写通道门禁（V367 批次 F）：实体落存储 + DB 只存引用/摘要。 */
class ScenarioRunArtifactWriterTest {

    @SuppressWarnings("unchecked")
    private static ScenarioRunArtifactWriter writer(WorkspaceDataDao dao, IObjectStorageService storage) {
        ObjectProvider<IObjectStorageService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(storage);
        return new ScenarioRunArtifactWriter(dao, provider);
    }

    @Test
    @DisplayName("writeJson：putObject 到 scenario-runs/{sc}/{run}/ 并落 sha256 引用行")
    void writesObjectAndReference() {
        WorkspaceDataDao dao = mock(WorkspaceDataDao.class);
        IObjectStorageService storage = mock(IObjectStorageService.class);

        writer(dao, storage).writeJson("r1", "sc1", "EXEC_TRACE", List.of(Map.of("step", "intent")));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).putObject(key.capture(), any(byte[].class), eq("application/json"));
        assertTrue(key.getValue().startsWith("scenario-runs/sc1/r1/exec_trace-"));
        assertTrue(key.getValue().endsWith(".json"));

        verify(dao).insertRunArtifact(anyString(), eq("r1"), eq("sc1"), eq("EXEC_TRACE"),
                eq(key.getValue()), startsWith("sha256:"), anyLong());
    }

    @Test
    @DisplayName("存储未装配 ⇒ 不写引用行（禁有引用无实体）")
    void noStorageDoesNotWriteReference() {
        WorkspaceDataDao dao = mock(WorkspaceDataDao.class);
        writer(dao, null).writeJson("r1", "sc1", "ALGORITHM_RESULT", Map.of("x", 1));
        verify(dao, org.mockito.Mockito.never())
                .insertRunArtifact(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }
}
