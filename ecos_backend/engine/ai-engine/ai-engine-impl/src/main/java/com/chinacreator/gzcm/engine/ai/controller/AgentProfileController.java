package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.llm.model.ProfileConfig;
import com.chinacreator.gzcm.sysman.llm.service.IAgentProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Profile 配置 CRUD 控制器
 * <p>
 * 遵循 Sys-Man 已有 Controller 模式（参考 AbacController）:
 * - @Autowired(required=false)
 * - 未预期异常经 {@code AiErrorAdvice} 分类为 {@code ECOS-AI-*} 响应，控制器层禁兜底吞异常（F06-15）
 * </p>
 */
@RestController
@RequestMapping("/api/v1/agent/profiles")
public class AgentProfileController {

    @Autowired(required = false)
    private IAgentProfileService agentProfileService;

    /**
     * 查询所有 profile 列表。失败经 {@code AiErrorAdvice} 分类为 {@code ECOS-AI-*}。
     */
    @GetMapping
    public ApiResponse<List<ProfileConfig>> listAll() {
        if (agentProfileService == null) {
            return AiResponseSupport.dependencyUnavailable("Agent Profile 服务");
        }
        List<ProfileConfig> list = agentProfileService.listAll();
        return ApiResponse.success(list);
    }

    /**
     * 按子系统查询 profile 列表。失败经 {@code AiErrorAdvice} 分类为 {@code ECOS-AI-*}。
     */
    @GetMapping("/{subsystem}")
    public ApiResponse<List<ProfileConfig>> listBySubsystem(@PathVariable String subsystem) {
        if (agentProfileService == null) {
            return AiResponseSupport.dependencyUnavailable("Agent Profile 服务");
        }
        List<ProfileConfig> list = agentProfileService.listBySubsystem(subsystem);
        return ApiResponse.success(list);
    }

    /**
     * 按 ID 查询 profile 详情。失败经 {@code AiErrorAdvice} 分类为 {@code ECOS-AI-*}。
     */
    @GetMapping("/detail/{id}")
    public ApiResponse<?> getById(@PathVariable String id) {
        if (agentProfileService == null) {
            return AiResponseSupport.dependencyUnavailable("Agent Profile 服务");
        }
        ProfileConfig config = agentProfileService.getById(id);
        if (config == null) {
            return ApiResponse.notFound("Profile 不存在: " + id);
        }
        return ApiResponse.success(config);
    }

    /**
     * 创建 profile。失败经 {@code AiErrorAdvice} 分类为 {@code ECOS-AI-*}。
     */
    @PostMapping
    public ApiResponse<?> create(@RequestBody ProfileConfig config) {
        if (agentProfileService == null) {
            return AiResponseSupport.dependencyUnavailable("Agent Profile 服务");
        }
        if (config == null) {
            return ApiResponse.badRequest("请求体不能为空");
        }
        ProfileConfig created = agentProfileService.create(config);
        return ApiResponse.success(created);
    }

    /**
     * 更新 profile。失败经 {@code AiErrorAdvice} 分类为 {@code ECOS-AI-*}。
     */
    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody ProfileConfig config) {
        if (agentProfileService == null) {
            return AiResponseSupport.dependencyUnavailable("Agent Profile 服务");
        }
        if (config == null) {
            return ApiResponse.badRequest("请求体不能为空");
        }
        config.setId(id);
        ProfileConfig updated = agentProfileService.update(config);
        return ApiResponse.success(updated);
    }

    /**
     * 删除 profile。失败经 {@code AiErrorAdvice} 分类为 {@code ECOS-AI-*}。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        if (agentProfileService == null) {
            return AiResponseSupport.dependencyUnavailable("Agent Profile 服务");
        }
        agentProfileService.delete(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        return ApiResponse.success(m);
    }

    /**
     * 启用/禁用 profile。失败经 {@code AiErrorAdvice} 分类为 {@code ECOS-AI-*}。
     * <p>请求体示例: {"enabled": true}</p>
     */
    @PatchMapping("/{id}/toggle")
    public ApiResponse<?> toggleEnabled(@PathVariable String id, @RequestBody Map<String, Object> body) {
        if (agentProfileService == null) {
            return AiResponseSupport.dependencyUnavailable("Agent Profile 服务");
        }
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        agentProfileService.toggleEnabled(id, enabled);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("enabled", enabled);
        return ApiResponse.success(m);
    }

    /**
     * 测试连接 — 校验 LLM 配置连通性。
     * <p>失败经 {@code AiErrorAdvice} 分类为 {@code ECOS-AI-*}（禁再回落 {@code internalError} 掩蔽）。</p>
     */
    @PostMapping("/{id}/test")
    public ApiResponse<?> testConnection(@PathVariable String id) {
        if (agentProfileService == null) {
            return AiResponseSupport.dependencyUnavailable("Agent Profile 服务");
        }
        agentProfileService.testConnection(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("message", "连接测试通过");
        return ApiResponse.success(m);
    }
}
