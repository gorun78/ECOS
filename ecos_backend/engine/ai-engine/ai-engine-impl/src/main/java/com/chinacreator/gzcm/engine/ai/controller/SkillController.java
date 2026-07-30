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
 * Skill Controller — list/enable/disable Hermes skills via MCP.
 *
 * Endpoint: /api/v1/skills
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private static final Logger log = LoggerFactory.getLogger(SkillController.class);

    @Autowired(required = false)
    @Qualifier("ecosHermesMCPClient")
    private HermesMCPClient mcpClient;

    /** List all available skills, optionally filtered by category */
    @GetMapping
    public ApiResponse<Object> list(@RequestParam(required = false) String category) {
        if (mcpClient == null || !mcpClient.isAvailable()) {
            return ApiResponse.success("Skills service not available");
        }
        try {
            Map<String, Object> args = new HashMap<>();
            if (category != null) {
                args.put("category", category);
            }
            String result = mcpClient.callTool("skills_list", args);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("skills_list failed: {}", e.getMessage());
            return ApiResponse.internalError("Skills list failed: " + e.getMessage());
        }
    }

    /** Health check */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("available", mcpClient != null && mcpClient.isAvailable());
        return ApiResponse.success(status);
    }
}
