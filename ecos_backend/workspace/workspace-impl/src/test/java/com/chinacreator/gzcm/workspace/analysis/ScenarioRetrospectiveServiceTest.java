package com.chinacreator.gzcm.workspace.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.chinacreator.gzcm.workspace.scenario.CognitiveEngineUnavailableException;
import com.chinacreator.gzcm.workspace.scenario.DcchengClient;

/** 复盘组合门禁（V367 批次 C）：运行段与认知段独立降级。 */
class ScenarioRetrospectiveServiceTest {

    private static WorkspaceDataDao daoWithRun() {
        WorkspaceDataDao dao = mock(WorkspaceDataDao.class);
        when(dao.findQuestionRun("r1")).thenReturn(List.of(Map.of(
                "id", "r1", "scenario_id", "sc1", "question", "q",
                "algorithm_status", "SUCCEEDED",
                "algorithm_result", Map.of("metrics", Map.of("posterior", List.of(0.8, 0.2))),
                "executor_type", "AI_AGENT", "executor_id", "agent-1")));
        when(dao.listQuestionRuns("sc1", 20)).thenReturn(List.of(
                Map.of("id", "r0", "question", "q", "mental_model", "FIRST_PRINCIPLES",
                        "status", "COMPLETED", "create_time", "2026-11-25")));
        return dao;
    }

    @Test
    @DisplayName("认知可用：run/algorithm/cognitive/previous 四段齐全")
    void buildsFullRetrospective() {
        DcchengClient cognitive = mock(DcchengClient.class);
        when(cognitive.mentalReviewByRun("r1")).thenReturn(Map.of("runId", "r1",
                "invalidations", List.of(Map.of("hypothesis_id", "h1")), "summary", Map.of("invalidations", 1)));

        Map<String, Object> out = new ScenarioRetrospectiveService(daoWithRun(), cognitive).build("sc1", "r1");

        assertEquals("r1", out.get("runId"));
        assertTrue(out.get("run") instanceof Map);
        assertTrue(out.get("algorithm") instanceof Map);
        assertTrue(out.get("cognitive") instanceof Map);
        assertEquals("r0", ((Map<?, ?>) out.get("previous")).get("runId"));
    }

    @Test
    @DisplayName("认知不可用：cognitive 段标记 unavailable，运行段照常返回")
    void cognitiveUnavailableDegradesLocally() {
        DcchengClient cognitive = mock(DcchengClient.class);
        when(cognitive.mentalReviewByRun(anyString()))
                .thenThrow(new CognitiveEngineUnavailableException("down"));

        Map<String, Object> out = new ScenarioRetrospectiveService(daoWithRun(), cognitive).build("sc1", "r1");

        Map<?, ?> cog = (Map<?, ?>) out.get("cognitive");
        assertEquals(true, cog.get("unavailable"));
        assertTrue(out.get("run") instanceof Map, "运行段不得因认知故障丢失");
    }
}
