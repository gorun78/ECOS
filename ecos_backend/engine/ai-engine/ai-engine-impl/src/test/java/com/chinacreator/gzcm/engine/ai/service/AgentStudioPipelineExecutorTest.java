package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.service.AgentStudioService.PipelineExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-3：Pipeline 六类节点的真实执行入口级用例（trigger/condition/tool 失败面）。
 *
 * <p>llm/ontology/approval 属外部依赖（llm-gateway / buszhi 属主端点），本类只锁
 * 进程内可断的三面：trigger 手动标记、condition 表达式求值、tool 在
 * {@code ToolExecutorService} 未装配时必须如实失败（不得再有 mock 成功/随机失败注入）。</p>
 */
@DisplayName("P0-3 Pipeline 真实执行器：trigger/condition 真实执行 + 失败如实传播")
class AgentStudioPipelineExecutorTest {

    @Test
    @DisplayName("trigger 手动触发 + condition 表达式求值 → completed，节点级 success 且输出含真实求值结果")
    void triggerAndConditionExecuteForReal() {
        AgentStudioService service = new AgentStudioService();
        Map<String, Object> params = Map.of(
                "status", "ok",
                "nodes", List.of(
                        Map.of("id", "trigger-1", "type", "trigger", "label", "定时触发",
                                "config", Map.of("cronExpr", "0 0 * * *", "timezone", "Asia/Shanghai")),
                        Map.of("id", "cond-1", "type", "condition", "label", "状态判断",
                                "config", Map.of("conditionExpr", "$.status == \"ok\""))));

        PipelineExecution finalExec = awaitTerminal(service,
                service.startPipeline("pip_p0_real", params).getExecutionId());

        assertEquals("completed", finalExec.getStatus());
        assertEquals("success", finalExec.getNodeTraces().get("trigger-1").getStatus());
        assertEquals("success", finalExec.getNodeTraces().get("cond-1").getStatus());
        assertTrue(finalExec.getNodeTraces().get("cond-1").getOutputJson().contains("\"result\":true"),
                "condition 节点输出必须带真实求值结果: "
                        + finalExec.getNodeTraces().get("cond-1").getOutputJson());
    }

    @Test
    @DisplayName("condition 求值为 false → 走 else 分支且节点仍 success（分支是结果不是失败）")
    void conditionFalseGoesElseBranch() {
        AgentStudioService service = new AgentStudioService();
        Map<String, Object> params = Map.of(
                "status", "blocked",
                "nodes", List.of(Map.of("id", "cond-1", "type", "condition", "label", "c",
                        "config", Map.of("conditionExpr", "status == \"ok\"",
                                "thenBranch", "放行", "elseBranch", "拒绝"))));

        PipelineExecution finalExec = awaitTerminal(service,
                service.startPipeline("pip_p0_else", params).getExecutionId());

        assertEquals("completed", finalExec.getStatus());
        String output = finalExec.getNodeTraces().get("cond-1").getOutputJson();
        assertTrue(output.contains("\"result\":false"), "求值结果必须落 trace 输出: " + output);
        assertTrue(output.contains("\"branch\":\"拒绝\""), "else 分支标记必须落 trace 输出: " + output);
    }

    @Test
    @DisplayName("ToolExecutorService 未装配 → tool 节点如实 failed，后继 skipped，overall failed")
    void toolFailureIsHonestAndSkipsDownstream() {
        AgentStudioService service = new AgentStudioService();
        Map<String, Object> params = Map.of(
                "nodes", List.of(
                        Map.of("id", "tool-1", "type", "tool", "label", "查库",
                                "config", Map.of("toolName", "query_db", "parameters", "{\"sql\":\"select 1\"}")),
                        Map.of("id", "cond-2", "type", "condition", "label", "c2",
                                "config", Map.of("conditionExpr", "x == 1"))));

        PipelineExecution finalExec = awaitTerminal(service,
                service.startPipeline("pip_p0_toolfail", params).getExecutionId());

        assertEquals("failed", finalExec.getStatus());
        assertEquals("failed", finalExec.getNodeTraces().get("tool-1").getStatus());
        assertNotNull(finalExec.getNodeTraces().get("tool-1").getErrorMessage(),
                "失败节点必须带真实错误原因");
        assertEquals("skipped", finalExec.getNodeTraces().get("cond-2").getStatus(),
                "前序失败后后继必须 skipped（不得伪成功）");
    }

    @Test
    @DisplayName("无节点图时回退单 trigger 节点 → completed（兼容旧调用方，无 mock 失败注入）")
    void legacyFallbackWithoutNodeGraph() {
        AgentStudioService service = new AgentStudioService();
        PipelineExecution finalExec = awaitTerminal(service,
                service.startPipeline("pip_p0_legacy", Map.of("key", "v")).getExecutionId());
        assertEquals("completed", finalExec.getStatus());
        assertEquals("success", finalExec.getNodeTraces().get("trigger-0").getStatus());
    }

    /** 轮询直到终态（execution 为 daemon 线程池异步执行）。 */
    private static PipelineExecution awaitTerminal(AgentStudioService service, String executionId) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            PipelineExecution exec = service.getPipelineStatus(executionId);
            if (exec != null && ("completed".equals(exec.getStatus()) || "failed".equals(exec.getStatus()))) {
                return exec;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Pipeline 未在 5s 内到达终态: " + executionId);
    }
}
