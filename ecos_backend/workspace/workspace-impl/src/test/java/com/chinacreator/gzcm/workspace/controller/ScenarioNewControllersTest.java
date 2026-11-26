package com.chinacreator.gzcm.workspace.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.chinacreator.gzcm.common.service.IObjectStorageService;
import com.chinacreator.gzcm.workspace.analysis.ScenarioRetrospectiveService;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;

/** 新控制器 HTTP 面：复盘 JSON + 产物字节下载。 */
class ScenarioNewControllersTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<IObjectStorageService> provider(IObjectStorageService storage) {
        ObjectProvider<IObjectStorageService> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(storage);
        return p;
    }

    @Test
    @DisplayName("GET retrospective：service 结果原样返回（JSON）")
    void retrospectiveReturnsJson() throws Exception {
        ScenarioRetrospectiveService service = mock(ScenarioRetrospectiveService.class);
        when(service.build("sc1", "r1")).thenReturn(Map.of("runId", "r1", "scenarioId", "sc1",
                "run", Map.of("question", "q"), "cognitive", Map.of("unavailable", false)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ScenarioRetrospectiveController(service, mock(ScenarioReadGuard.class))).build();

        mvc.perform(get("/api/v1/workspace/scenarios/sc1/question-runs/r1/retrospective")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"runId\":\"r1\"")));
    }

    @Test
    @DisplayName("GET scenario-run-artifacts：按 storage_ref 返回实体字节")
    void artifactStreamsBytes() throws Exception {
        WorkspaceDataDao dao = mock(WorkspaceDataDao.class);
        when(dao.findRunArtifact("a1")).thenReturn(List.of(Map.of(
                "id", "a1", "artifact_type", "EXEC_TRACE", "storage_ref", "k/1.json")));
        IObjectStorageService storage = mock(IObjectStorageService.class);
        when(storage.getObject("k/1.json")).thenReturn("{\"ok\":true}".getBytes());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ScenarioRunArtifactController(dao, mock(ScenarioReadGuard.class), provider(storage))).build();

        mvc.perform(get("/api/v1/workspace/scenario-run-artifacts/a1"))
                .andExpect(status().isOk())
                .andExpect(content().bytes("{\"ok\":true}".getBytes()));
    }
}
