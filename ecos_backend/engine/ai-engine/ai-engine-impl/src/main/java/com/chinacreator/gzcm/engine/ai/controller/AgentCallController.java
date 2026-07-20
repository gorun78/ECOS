package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.hermes.HermesEngine;
import com.chinacreator.gzcm.runtime.hermes.metrics.AgentMetrics;
import com.chinacreator.gzcm.runtime.hermes.scheduler.AgentResult;
import com.chinacreator.gzcm.sysman.hermes.service.IAgentProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 调用与统计控制器
 * <p>
 * 提供 Agent 聊天执行入口、工具注册/执行、子系统/全局统计查询。
 * 遵循 Sys-Man 已有 Controller 模式（参考 AbacController）。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/agent-call")
public class AgentCallController {

    private static final Logger log = LoggerFactory.getLogger(AgentCallController.class);

    @Autowired(required = false)
    private HermesEngine hermesEngine;

    @Autowired(required = false)
    private IAgentProfileService agentProfileService;

    /**
     * Agent 聊天 — 同步执行
     * <p>
     * 请求体:
     * <pre>
     * {
     *   "subsystem": "sysman",
     *   "profileName": "default",
     *   "message": "查询所有活跃用户",
     *   "stream": false
     * }
     * </pre>
     * </p>
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        try {
            if (hermesEngine == null) {
                return ApiResponse.internalError("Hermes 引擎未就绪");
            }

            String subsystem = (String) body.get("subsystem");
            String profileName = (String) body.get("profileName");
            String message = (String) body.get("message");

            if (subsystem == null || subsystem.trim().isEmpty()) {
                return ApiResponse.badRequest("subsystem 不能为空");
            }
            if (profileName == null || profileName.trim().isEmpty()) {
                return ApiResponse.badRequest("profileName 不能为空");
            }
            if (message == null || message.trim().isEmpty()) {
                return ApiResponse.badRequest("message 不能为空");
            }

            AgentResult result = hermesEngine.execute(subsystem, profileName, message);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("sessionId", result.getSessionId());
            resp.put("success", result.isSuccess());
            resp.put("content", result.getContent());
            resp.put("tokensInput", result.getTokensInput());
            resp.put("tokensOutput", result.getTokensOutput());
            resp.put("durationMs", result.getDurationMs());
            if (!result.isSuccess()) {
                resp.put("errorMsg", result.getErrorMsg());
            }

            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("Agent 聊天执行失败", e);
            return ApiResponse.internalError("Agent 执行失败: " + e.getMessage());
        }
    }

    /**
     * 获取全局统计信息
     */
    @GetMapping("/stats")
    public ApiResponse<?> getGlobalStats() {
        try {
            if (agentProfileService == null) {
                return ApiResponse.internalError("Agent Profile 服务未就绪");
            }
            Map<String, Object> stats = agentProfileService.getGlobalStats();
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取全局统计失败", e);
            return ApiResponse.internalError("获取统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定子系统的统计信息
     */
    @GetMapping("/stats/{subsystem}")
    public ApiResponse<?> getSubsystemStats(@PathVariable String subsystem) {
        try {
            if (agentProfileService == null) {
                return ApiResponse.internalError("Agent Profile 服务未就绪");
            }
            Map<String, Object> stats = agentProfileService.getSubsystemStats(subsystem);
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取子系统统计失败: subsystem={}", subsystem, e);
            return ApiResponse.internalError("获取统计失败: " + e.getMessage());
        }
    }
}
