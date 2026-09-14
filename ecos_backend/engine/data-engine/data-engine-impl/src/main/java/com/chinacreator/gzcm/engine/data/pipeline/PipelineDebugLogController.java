package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Pipeline 调试日志 Controller — 按 executionId 取节点级执行日志。
 * <p>
 * 与实时 SSE 的差异：调试执行是同步阻塞的（继续/单步在内存态完成），
 * 没有持续推送语义，因此端点按 executionId 一次性返回全量日志（按 seq 升序）。
 * 前端拿到 executionId 后增量拉取（T3-前端 SSE 端点设计中的兜底链路）。
 *
 * @author DataBridge Datanet Team
 */
@RestController
@RequestMapping("/api/v1/pipeline/debug/executions")
public class PipelineDebugLogController {

    private final PipelineDebugService debugService;

    public PipelineDebugLogController(PipelineDebugService debugService) {
        this.debugService = debugService;
    }

    /**
     * GET /api/v1/pipeline/debug/executions/{executionId}/logs
     * 返回该执行记录的完整节点级日志序列（seq 升序）。
     */
    @GetMapping("/{executionId}/logs")
    public ApiResponse<List<PipelineDebugLogLineVO>> getLogs(@PathVariable String executionId) {
        List<PipelineDebugService.NodeLog> lines = debugService.getLogs(executionId);
        List<PipelineDebugLogLineVO> vos = lines.stream().map(l -> {
            PipelineDebugLogLineVO vo = new PipelineDebugLogLineVO();
            vo.setSeq(l.seq());
            vo.setNodeId(l.nodeId());
            vo.setLevel(l.level());
            vo.setMessage(l.message());
            vo.setAtMs(l.atMs());
            return vo;
        }).collect(Collectors.toList());
        return ApiResponse.success("查询成功", vos);
    }

    /** 取已有 executionId — 调试会话的 executionId 与 PipelineController#executeDefinition 的 taskId 同源。 */
    @GetMapping("/{executionId}")
    public ApiResponse<List<PipelineDebugService.NodeLog>> exec(@PathVariable String executionId) {
        return ApiResponse.success("查询成功", debugService.getLogs(executionId));
    }
}
