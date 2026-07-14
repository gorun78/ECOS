package com.chinacreator.gzcm.engine.cognitive.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.hermes.model.ProfileConfig;
import com.chinacreator.gzcm.sysman.hermes.service.IAgentProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * - try-catch 包装，返回 ApiResponse
 * </p>
 */
@RestController
@RequestMapping("/api/v1/agent/profiles")
public class AgentProfileController {

    private static final Logger log = LoggerFactory.getLogger(AgentProfileController.class);

    @Autowired(required = false)
    private IAgentProfileService agentProfileService;

    /**
     * 查询所有 profile 列表
     */
    @GetMapping
    public ApiResponse<List<ProfileConfig>> listAll() {
        try {
            if (agentProfileService == null) {
                return ApiResponse.internalError("Agent Profile 服务未就绪");
            }
            List<ProfileConfig> list = agentProfileService.listAll();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询所有 Profile 失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 按子系统查询 profile 列表
     */
    @GetMapping("/{subsystem}")
    public ApiResponse<List<ProfileConfig>> listBySubsystem(@PathVariable String subsystem) {
        try {
            if (agentProfileService == null) {
                return ApiResponse.internalError("Agent Profile 服务未就绪");
            }
            List<ProfileConfig> list = agentProfileService.listBySubsystem(subsystem);
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("按子系统查询 Profile 失败: subsystem={}", subsystem, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 按 ID 查询 profile 详情
     */
    @GetMapping("/detail/{id}")
    public ApiResponse<?> getById(@PathVariable String id) {
        try {
            if (agentProfileService == null) {
                return ApiResponse.internalError("Agent Profile 服务未就绪");
            }
            ProfileConfig config = agentProfileService.getById(id);
            if (config == null) {
                return ApiResponse.notFound("Profile 不存在: " + id);
            }
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("查询 Profile 详情失败: id={}", id, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 创建 profile
     */
    @PostMapping
    public ApiResponse<?> create(@RequestBody ProfileConfig config) {
        try {
            if (agentProfileService == null) {
                return ApiResponse.internalError("Agent Profile 服务未就绪");
            }
            if (config == null) {
                return ApiResponse.badRequest("请求体不能为空");
            }
            ProfileConfig created = agentProfileService.create(config);
            return ApiResponse.success(created);
        } catch (Exception e) {
            log.error("创建 Profile 失败", e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新 profile
     */
    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody ProfileConfig config) {
        try {
            if (agentProfileService == null) {
                return ApiResponse.internalError("Agent Profile 服务未就绪");
            }
            if (config == null) {
                return ApiResponse.badRequest("请求体不能为空");
            }
            config.setId(id);
            ProfileConfig updated = agentProfileService.update(config);
            return ApiResponse.success(updated);
        } catch (Exception e) {
            log.error("更新 Profile 失败: id={}", id, e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除 profile
     */
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        try {
            if (agentProfileService == null) {
                return ApiResponse.internalError("Agent Profile 服务未就绪");
            }
            agentProfileService.delete(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", true);
            return ApiResponse.success(m);
        } catch (Exception e) {
            log.error("删除 Profile 失败: id={}", id, e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    /**
     * 启用/禁用 profile
     * <p>请求体示例: {"enabled": true}</p>
     */
    @PatchMapping("/{id}/toggle")
    public ApiResponse<?> toggleEnabled(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            if (agentProfileService == null) {
                return ApiResponse.internalError("Agent Profile 服务未就绪");
            }
            boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
            agentProfileService.toggleEnabled(id, enabled);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("enabled", enabled);
            return ApiResponse.success(m);
        } catch (Exception e) {
            log.error("切换 Profile 启用状态失败: id={}", id, e);
            return ApiResponse.internalError("操作失败: " + e.getMessage());
        }
    }

    /**
     * 测试连接 — 校验 LLM 配置连通性
     */
    @PostMapping("/{id}/test")
    public ApiResponse<?> testConnection(@PathVariable String id) {
        try {
            if (agentProfileService == null) {
                return ApiResponse.internalError("Agent Profile 服务未就绪");
            }
            agentProfileService.testConnection(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", true);
            m.put("message", "连接测试通过");
            return ApiResponse.success(m);
        } catch (Exception e) {
            log.error("测试连接失败: id={}", id, e);
            return ApiResponse.internalError("连接测试失败: " + e.getMessage());
        }
    }
}
