package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.replay.RunReplayService;
import com.chinacreator.gzcm.engine.ai.replay.RunReplayView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 运行回放端点（详细设计-06 F06-08；本册唯一新增端点，只增不改）。
 *
 * <pre>
 *   GET /api/v1/agent/runs/{traceId}/replay   — 按诊断码重建一次运行的步骤时序
 *   GET /api/agent/runs/{traceId}/replay      — 历史前缀（VersionPrefixRewriteFilter 已含 /api/agent/ → /api/v1/agent/）
 * </pre>
 *
 * <p>权限面：本端点<b>不入</b> {@code AnonymousEndpointRegistry}（匿名豁免单源），
 * 即沿用平台默认 DENY —— 未认证不可读；回放内容虽已脱敏（无消息/工具结果原文），
 * 但含工具键、模型名与裁决态，属诊断面，不得匿名。</p>
 *
 * <p>数据面：只从 {@code EVT=AgentLoop} 结构化日志行重建（{@link RunReplayService}），
 * 不新建回放表、不读 {@code AgentTracer} 内存。</p>
 */
@RestController
@RequestMapping("/api/v1/agent/runs")
public class AgentRunReplayController {

    private static final Logger log = LoggerFactory.getLogger(AgentRunReplayController.class);

    private final RunReplayService runReplayService;

    public AgentRunReplayController(RunReplayService runReplayService) {
        this.runReplayService = runReplayService;
    }

    /**
     * 重建指定诊断码的运行时序。
     *
     * @return 200=FOUND；404=该 trace 无日志行（ECOS-AI-404）；
     *         503=回放日志通道未就绪（显式不可用态，禁 200 空时间线）；400=traceId 非法（ECOS-AI-400）
     */
    @GetMapping("/{traceId}/replay")
    public ResponseEntity<ApiResponse<RunReplayView>> replay(@PathVariable("traceId") String traceId) {
        RunReplayView view;
        try {
            view = runReplayService.replay(traceId);
        } catch (IllegalArgumentException e) {
            AiError error = AiError.of(AiErrorClass.INVALID_ARGUMENT, traceId, e.getMessage());
            log.warn("[F06-08] 回放请求参数非法: trace={} reason={}", traceId, e.getMessage());
            return ResponseEntity.status(error.httpStatus())
                    .body(ApiResponse.error(error.httpStatus(), error.errorCode(), error.userMessage()));
        }
        return switch (view.status()) {
            case RunReplayView.FOUND -> ResponseEntity.ok(ApiResponse.success(view));
            case RunReplayView.NOT_FOUND -> {
                AiError error = AiError.of(AiErrorClass.NOT_FOUND, traceId, "no EVT=AgentLoop lines for trace");
                yield ResponseEntity.status(error.httpStatus())
                        .body(ApiResponse.error(error.httpStatus(), error.errorCode(), error.userMessage()));
            }
            default -> {
                // 通道未就绪：不给 ECOS-AI-* 业务码（不是业务失败，是本进程日志面缺配），
                // 用 503 + 明文提示，避免把基础设施缺配伪装成业务错误码
                log.error("[F06-08] 回放日志通道未就绪（检查 logback 的 ECOS_AGENT_LOOP 文件 appender 与 "
                        + "logging.file.path）: trace={}", traceId);
                yield ResponseEntity.status(503).body(ApiResponse.error(503,
                        "运行回放暂不可用：本进程未挂载 Agent Loop 结构化日志文件通道"
                                + "（诊断码 " + traceId + "）"));
            }
        };
    }
}
