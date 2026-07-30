package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.services.agent.runtime.mcp.HermesMCPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Cron Job Controller — CRUD operations backed by Hermes MCP cronjob_* tools.
 *
 * Endpoint: /api/v1/cron-jobs
 */
@RestController
@RequestMapping("/api/v1/cron-jobs")
public class CronJobController {

    private static final Logger log = LoggerFactory.getLogger(CronJobController.class);

    @Autowired(required = false)
    @Qualifier("ecosHermesMCPClient")
    private HermesMCPClient mcpClient;

    /** List all cron jobs */
    @GetMapping
    public ApiResponse<Object> list() {
        if (mcpClient == null || !mcpClient.isAvailable()) {
            return ApiResponse.success("[]");
        }
        try {
            String result = mcpClient.callTool("cronjob_list", null);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("cronjob_list failed: {}", e.getMessage());
            return ApiResponse.badRequest("Cron job list failed: " + e.getMessage());
        }
    }

    /** Create a new cron job */
    @PostMapping
    public ApiResponse<Object> create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String schedule = body.get("schedule");
        String prompt = body.get("prompt");
        if (name == null || schedule == null || prompt == null) {
            return ApiResponse.badRequest("name, schedule, prompt required");
        }
        try {
            Map<String, Object> args = new HashMap<>();
            args.put("name", name);
            args.put("schedule", schedule);
            args.put("prompt", prompt);
            String result = mcpClient.callTool("cronjob_create", args);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("cronjob_create failed: {}", e.getMessage());
            return ApiResponse.badRequest("Create failed: " + e.getMessage());
        }
    }

    /** Pause a cron job */
    @PostMapping("/{jobId}/pause")
    public ApiResponse<Object> pause(@PathVariable String jobId) {
        try {
            Map<String, Object> args = new HashMap<>();
            args.put("job_id", jobId);
            String result = mcpClient.callTool("cronjob_pause", args);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("cronjob_pause failed: {}", e.getMessage());
            return ApiResponse.badRequest("Pause failed: " + e.getMessage());
        }
    }
}
