package com.chinacreator.gzcm.workspace.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.chinacreator.gzcm.workspace.scenario.ScenarioService;

/**
 * 场景运行 L3 研究循环服务单测（2026-11-25 首切片）。
 *
 * <p>覆盖四条不可让步的语义：</p>
 * <ol>
 *   <li>预算钳制：请求超硬上限被收窄，禁越界；</li>
 *   <li>接受即停：评估 accepted=true 时只落一轮并写 stop_reason=ACCEPTED；</li>
 *   <li>执行失败也留痕：异常轮必须落一行 DEGRADED + stop_reason=FAILED，不静默；</li>
 *   <li>降级透传：评估 degraded=true 时整体 evaluationDegraded=true，且候选标记常真。</li>
 * </ol>
 *
 * <p>纯 mock，不依赖 DB/Spring 上下文。</p>
 */
class ScenarioResearchRunServiceTest {

    private final WorkspaceDataDao dao = mock(WorkspaceDataDao.class);
    private final ScenarioService scenarioService = mock(ScenarioService.class);
    private final ResearchExecutorPort executor = new StubExecutor();
    private final ScenarioResearchRunService service =
            new ScenarioResearchRunService(dao, scenarioService, executor);

    ScenarioResearchRunServiceTest() {
        when(scenarioService.get(any())).thenReturn(null);
        when(dao.insertResearchRun(any())).thenReturn(1);
        when(dao.updateResearchStopReason(any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("预算钳制：请求 999 轮/99999 秒被收窄到硬上限，禁越界执行")
    void budgetIsClampedToHardLimits() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", "验证预算钳制");
        body.put("budget", Map.of("maxLoops", 999, "maxSecondsPerLoop", 99999, "maxTotalSeconds", 99999));

        Map<String, Object> out = service.run("sc-1", body);

        @SuppressWarnings("unchecked")
        Map<String, Object> budget = (Map<String, Object>) out.get("budget");
        assertEquals(5, budget.get("maxLoops"));
        assertEquals(600, budget.get("maxSecondsPerLoop"));
        assertEquals(1800, budget.get("maxTotalSeconds"));
    }

    @Test
    @DisplayName("接受即停：accepted=true 只落一轮，stop_reason=ACCEPTED，谱系自指 SOTA")
    void acceptedStopsImmediately() {
        ((StubExecutor) executor).accepted = true;

        Map<String, Object> out = service.run("sc-1", Map.of("question", "接受即停"));

        assertEquals("ACCEPTED", out.get("stopReason"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rounds = (List<Map<String, Object>>) out.get("rounds");
        assertEquals(1, rounds.size());
        verify(dao, times(1)).insertResearchRun(any());
        verify(dao, times(1)).updateResearchStopReason(any(), eq("ACCEPTED"));
    }

    @Test
    @DisplayName("执行失败也留痕：异常轮落 DEGRADED 行并写 FAILED，不一味抛给前端")
    void executionFailureIsPersisted() {
        ((StubExecutor) executor).failAtLoop = 0;

        Map<String, Object> out = service.run("sc-1", Map.of("question", "失败留痕"));

        assertEquals("FAILED", out.get("stopReason"));
        verify(dao, times(1)).insertResearchRun(any());
        verify(dao, times(1)).updateResearchStopReason(any(), eq("FAILED"));
    }

    @Test
    @DisplayName("降级透传：evaluation.degraded=true 时整体标记降级，候选标记恒真")
    void degradedEvaluationIsPropagated() {
        ((StubExecutor) executor).accepted = true;
        ((StubExecutor) executor).degraded = true;

        Map<String, Object> out = service.run("sc-1", Map.of("question", "降级透传"));

        assertEquals(Boolean.TRUE, out.get("evaluationDegraded"));
        assertEquals(Boolean.TRUE, out.get("candidateOnly"));
    }

    @Test
    @DisplayName("多轮谱系：未接受时按预算跑满，父子链逐轮相接、末轮写 MAX_LOOPS")
    void lineageChainsAcrossRounds() {
        ((StubExecutor) executor).accepted = false;

        Map<String, Object> out = service.run("sc-1",
                Map.of("question", "多轮", "budget", Map.of("maxLoops", 3)));

        assertEquals("MAX_LOOPS", out.get("stopReason"));
        ArgumentCaptor<WorkspaceDataDao.ResearchRunRow> captor =
                ArgumentCaptor.forClass(WorkspaceDataDao.ResearchRunRow.class);
        verify(dao, times(3)).insertResearchRun(captor.capture());
        List<WorkspaceDataDao.ResearchRunRow> rows = captor.getAllValues();

        assertEquals(0, rows.get(0).loopIndex());
        assertEquals(null, rows.get(0).parentRunId());
        assertEquals(rows.get(0).runId(), rows.get(1).parentRunId());
        assertEquals(rows.get(1).runId(), rows.get(2).parentRunId());
        verify(dao, times(1)).updateResearchStopReason(any(), eq("MAX_LOOPS"));
    }

    @Test
    @DisplayName("入参拒绝：question 空白 400；场景不存在 404")
    void rejectsInvalidInput() {
        assertThrows(DataBridgeException.class, () -> service.run("sc-1", Map.of("question", "  ")));

        when(scenarioService.get(any())).thenThrow(new RuntimeException("not found"));
        assertThrows(DataBridgeException.class, () -> service.run("missing", Map.of("question", "q")));
    }

    @Test
    @DisplayName("只读执行器自述：明确声明不执行代码、不联网，禁夸大能力")
    void executorDescribesHonestly() {
        Map<String, Object> desc = executor.describe();
        assertEquals(Boolean.FALSE, desc.get("codeExecution"));
        assertEquals(Boolean.FALSE, desc.get("network"));
        assertFalse(String.valueOf(desc.get("note")).isBlank());
    }

    @Test
    @DisplayName("接受门槛：仅绑定无知识证据不得收敛，否则多轮循环退化为单轮")
    void acceptanceRequiresKnowledgeEvidenceNotJustBindings() {
        ReadOnlyResearchExecutor real = new ReadOnlyResearchExecutor(dao);

        Map<String, Object> noEvidence = real.evaluate("sc-1",
                Map.of("metric", Map.of("bindingCount", 3, "docHitCount", 0)), List.of());
        assertEquals(Boolean.FALSE, noEvidence.get("accepted"));

        Map<String, Object> withEvidence = real.evaluate("sc-1",
                Map.of("metric", Map.of("bindingCount", 3, "docHitCount", 2)), List.of());
        assertEquals(Boolean.TRUE, withEvidence.get("accepted"));

        Map<String, Object> noBinding = real.evaluate("sc-1",
                Map.of("metric", Map.of("bindingCount", 0, "docHitCount", 5)), List.of());
        assertEquals(Boolean.FALSE, noBinding.get("accepted"));
    }

    /** 确定性桩执行器：只测服务编排语义，不触真实 DAO/LLM。 */
    private static class StubExecutor implements ResearchExecutorPort {
        boolean accepted = false;
        boolean degraded = false;
        int failAtLoop = -1;

        @Override
        public Map<String, Object> describe() {
            return Map.of("kind", "STUB", "codeExecution", false, "network", false, "note", "stub");
        }

        @Override
        public Map<String, Object> describeContext(String scenarioId) {
            return Map.of("bindingCount", 1);
        }

        @Override
        public List<Map<String, Object>> proposeHypotheses(String scenarioId, String question) {
            List<Map<String, Object>> out = new ArrayList<>();
            out.add(new LinkedHashMap<>(Map.of("text", "h1", "kind", "ASSUMPTION", "confirmed", true)));
            return out;
        }

        @Override
        public List<Map<String, Object>> refineHypotheses(String scenarioId, String question,
                                                          List<Map<String, Object>> previousRounds) {
            return proposeHypotheses(scenarioId, question);
        }

        @Override
        public Map<String, Object> executeRound(String scenarioId, String question,
                                                List<Map<String, Object>> hypotheses, int loopIndex) {
            if (loopIndex == failAtLoop) {
                throw new IllegalStateException("stub failure");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("trace", List.of(Map.of("step", "probe", "status", "success",
                    "detail", "stub", "elapsedMs", 1)));
            out.put("experimentSpec", Map.of("loopIndex", loopIndex));
            out.put("advice", Map.of("summary", "stub", "actions", List.of(), "risks", List.of(),
                    "confidence", "LOW"));
            out.put("metric", Map.of("bindingCount", 1));
            return out;
        }

        @Override
        public Map<String, Object> evaluate(String scenarioId, Map<String, Object> execResult,
                                            List<Map<String, Object>> previousRounds) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("accepted", accepted);
            out.put("degraded", degraded);
            out.put("evidence", "stub evidence");
            out.put("noProgress", false);
            return out;
        }
    }
}
