package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pipeline 断点调试 Controller — 调试会话生命周期管理。
 * <p>
 * 端点设计：
 * <ul>
 *   <li>POST   /api/v1/pipeline/debug/sessions                    — 开始调试会话（入参 PipelineDebugStartDTO）</li>
 *   <li>PUT    /api/v1/pipeline/debug/sessions/{sessionId}/breakpoints — 更新会话断点（重启态/运行态可改）</li>
 *   <li>GET    /api/v1/pipeline/debug/sessions/{sessionId}            — 会话状态（当前节点/已完成/变量快照）</li>
 *   <li>POST   /api/v1/pipeline/debug/sessions/{sessionId}/step                     — 单步执行下一节点</li>
 *   <li>POST   /api/v1/pipeline/debug/sessions/{sessionId}/continue                   — 继续到下一断点或结束</li>
 *   <li>POST   /api/v1/pipeline/debug/sessions/{sessionId}/stop                       — 停止会话（落库 CANCELLED）</li>
 *   <li>DELETE /api/v1/pipeline/debug/sessions/{sessionId}            — 删除会话（内存态移除）</li>
 * </ul>
 * <p>
 * SSE 日志端点见 {@link PipelineDebugLogController}（GET /logs?executionId=...）。
 *
 * @author DataBridge Datanet Team
 */
@RestController
@RequestMapping("/api/v1/pipeline/debug/sessions")
public class PipelineDebugController {

    private static final Logger log = LoggerFactory.getLogger(PipelineDebugController.class);

    private final PipelineDebugService debugService;

    public PipelineDebugController(PipelineDebugService debugService) {
        this.debugService = debugService;
    }

    // ── 开始调试会话 ─────────────────────────────────────

    @PostMapping
    public ApiResponse<PipelineDebugSessionVO> startSession(
            @RequestBody(required = false) PipelineDebugStartDTO dto) {
        PipelineDebugSessionVO vo = debugService.createSession(dto);
        log.info("[pipeline-debug] session created: id={}, definitionId={}, pipelineName={}",
                vo.getSessionId(), vo.getDefinitionId(), vo.getPipelineName());
        return ApiResponse.success("调试会话已创建", vo);
    }

    // ── 会话状态 ─────────────────────────────────────────

    @GetMapping("/{sessionId}")
    public ApiResponse<PipelineDebugSessionVO> getSession(@PathVariable String sessionId) {
        return ApiResponse.success("查询成功", debugService.getSession(sessionId));
    }

    // ── 单步执行 ─────────────────────────────────────────

    @PostMapping("/{sessionId}/step")
    public ApiResponse<PipelineDebugSessionVO> step(@PathVariable String sessionId) {
        PipelineDebugSessionVO vo = debugService.step(sessionId);
        log.info("[pipeline-debug] step: id={}, state={}", sessionId, vo.getState());
        return ApiResponse.success("单步推进", vo);
    }

    // ── 继续执行（到下一断点或结束） ─────────────────────

    @PostMapping("/{sessionId}/continue")
    public ApiResponse<PipelineDebugSessionVO> cont(@PathVariable String sessionId) {
        PipelineDebugSessionVO vo = debugService.cont(sessionId);
        log.info("[pipeline-debug] continue: id={}, state={}", sessionId, vo.getState());
        return ApiResponse.success("继续执行", vo);
    }

    // ── 停止会话 ─────────────────────────────────────────

    @PostMapping("/{sessionId}/stop")
    public ApiResponse<PipelineDebugSessionVO> stop(@PathVariable String sessionId) {
        PipelineDebugSessionVO vo = debugService.stop(sessionId);
        return ApiResponse.success("会话已停止", vo);
    }

    // ── 重置会话（保留断点） ─────────────────────────────

    @PostMapping("/{sessionId}/reset")
    public ApiResponse<PipelineDebugSessionVO> reset(@PathVariable String sessionId) {
        PipelineDebugSessionVO vo = debugService.reset(sessionId);
        return ApiResponse.success("会话已重置", vo);
    }

    // ── 删除会话 ─────────────────────────────────────────

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(@PathVariable String sessionId) {
        debugService.deleteSession(sessionId);
        return ApiResponse.success("会话已删除", null);
    }

    // ── 调试会话执行历史（与 PipelineController#listExecutions 同源） ──

    @GetMapping("/executions")
    public ApiResponse<List<PipelineExecutionVO>> listExecutions(
            @RequestParam String definitionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success("查询成功", debugService.listExecutions(definitionId, page, pageSize));
    }

    // ── 节点数据预览（数据预览 Tab 使用） ─────────────────

    @GetMapping("/{sessionId}/preview/{nodeId}")
    public ApiResponse<PipelineStepStatusVO> preview(@PathVariable String sessionId,
                                                     @PathVariable String nodeId) {
        return ApiResponse.success("查询成功", debugService.getNodePreview(sessionId, nodeId));
    }

    // ── 调试会话步骤列表（当前快照，与 state 一致） ────────

    @GetMapping("/{sessionId}/steps")
    public ApiResponse<List<NodeStepVO>> steps(@PathVariable String sessionId) {
        PipelineDebugSessionVO vo = debugService.getSession(sessionId);
        return ApiResponse.success("查询成功", vo.getSteps());
    }
}
