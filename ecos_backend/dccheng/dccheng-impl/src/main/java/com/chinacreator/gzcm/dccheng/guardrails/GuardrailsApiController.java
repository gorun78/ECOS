package com.chinacreator.gzcm.dccheng.guardrails;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 护栏策略 API Controller — 策略 CRUD / 编译 / 预览。
 *
 * <p>当前为占位实现：使用 {@link CopyOnWriteArrayList} 在 Controller 内存中存储策略，
 * 不依赖数据库。进程重启后数据丢失，后续接入持久化层后替换存储实现。</p>
 *
 * <h3>6 个端点：</h3>
 * <ol>
 *   <li>GET    /api/v1/guardrails/policies             — 策略列表</li>
 *   <li>POST   /api/v1/guardrails/policies             — 创建策略</li>
 *   <li>PUT    /api/v1/guardrails/policies/{id}        — 更新策略</li>
 *   <li>DELETE /api/v1/guardrails/policies/{id}        — 删除策略</li>
 *   <li>POST   /api/v1/guardrails/policies/{id}/compile — 编译策略（占位）</li>
 *   <li>GET    /api/v1/guardrails/policies/{id}/preview — 预览策略效果（空数组占位）</li>
 * </ol>
 *
 * @author dccheng
 */
@RestController
@RequestMapping("/api/v1/guardrails")
public class GuardrailsApiController {

    private static final Logger log = LoggerFactory.getLogger(GuardrailsApiController.class);

    /** 内存策略存储（占位）。 */
    private final CopyOnWriteArrayList<Map<String, Object>> policies = new CopyOnWriteArrayList<>();

    /** 自增策略 ID 生成器。 */
    private final AtomicLong idSeq = new AtomicLong(0);

    // ════════════════════════════════════════════════════
    // 1. GET /api/v1/guardrails/policies — 策略列表
    // ════════════════════════════════════════════════════
    @GetMapping("/policies")
    public ApiResponse<List<Map<String, Object>>> listPolicies() {
        return ApiResponse.success(new ArrayList<>(policies));
    }

    // ════════════════════════════════════════════════════
    // 2. POST /api/v1/guardrails/policies — 创建策略
    // ════════════════════════════════════════════════════
    @PostMapping("/policies")
    public ApiResponse<Map<String, Object>> createPolicy(@RequestBody Map<String, Object> body) {
        if (body == null) {
            return ApiResponse.badRequest("请求体不能为空");
        }
        String name = (String) body.getOrDefault("name", "");
        if (name == null || name.isEmpty()) {
            return ApiResponse.badRequest("缺少必填字段 name");
        }
        String id = String.valueOf(idSeq.incrementAndGet());
        long now = Instant.now().toEpochMilli();

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("id", id);
        policy.put("name", name);
        policy.put("type", body.getOrDefault("type", "block"));
        policy.put("severity", body.getOrDefault("severity", "medium"));
        policy.put("isEnabled", body.getOrDefault("isEnabled", true));
        policy.put("parameters", body.getOrDefault("parameters", Collections.emptyMap()));
        policy.put("createdAt", now);
        policy.put("updatedAt", now);

        policies.add(policy);
        log.info("Guardrails policy created: id={} name={}", id, name);
        return ApiResponse.success(policy);
    }

    // ════════════════════════════════════════════════════
    // 3. PUT /api/v1/guardrails/policies/{id} — 更新策略
    // ════════════════════════════════════════════════════
    @PutMapping("/policies/{id}")
    public ApiResponse<Map<String, Object>> updatePolicy(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        if (body == null) {
            return ApiResponse.badRequest("请求体不能为空");
        }
        for (int i = 0; i < policies.size(); i++) {
            Map<String, Object> existing = policies.get(i);
            if (id.equals(String.valueOf(existing.get("id")))) {
                Map<String, Object> updated = new LinkedHashMap<>(existing);
                if (body.containsKey("name")) updated.put("name", body.get("name"));
                if (body.containsKey("type")) updated.put("type", body.get("type"));
                if (body.containsKey("severity")) updated.put("severity", body.get("severity"));
                if (body.containsKey("isEnabled")) updated.put("isEnabled", body.get("isEnabled"));
                if (body.containsKey("parameters")) updated.put("parameters", body.get("parameters"));
                updated.put("updatedAt", Instant.now().toEpochMilli());
                policies.set(i, updated);
                log.info("Guardrails policy updated: id={}", id);
                return ApiResponse.success(updated);
            }
        }
        return ApiResponse.notFound("策略 " + id + " 不存在");
    }

    // ════════════════════════════════════════════════════
    // 4. DELETE /api/v1/guardrails/policies/{id} — 删除策略
    // ════════════════════════════════════════════════════
    @DeleteMapping("/policies/{id}")
    public ApiResponse<Map<String, Object>> deletePolicy(@PathVariable String id) {
        for (Map<String, Object> p : policies) {
            if (id.equals(String.valueOf(p.get("id")))) {
                policies.remove(p);
                log.info("Guardrails policy deleted: id={}", id);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", id);
                result.put("deleted", true);
                return ApiResponse.success(result);
            }
        }
        return ApiResponse.notFound("策略 " + id + " 不存在");
    }

    // ════════════════════════════════════════════════════
    // 5. POST /api/v1/guardrails/policies/{id}/compile — 编译策略
    // ════════════════════════════════════════════════════
    @PostMapping("/policies/{id}/compile")
    public ApiResponse<Map<String, Object>> compilePolicy(@PathVariable String id) {
        if (!policyExists(id)) {
            return ApiResponse.notFound("策略 " + id + " 不存在");
        }
        log.info("Guardrails policy compiled (placeholder): id={}", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", "compiled");
        result.put("message", "compiled successfully");
        return ApiResponse.success("compiled successfully", result);
    }

    // ════════════════════════════════════════════════════
    // 6. GET /api/v1/guardrails/policies/{id}/preview — 预览策略效果
    // ════════════════════════════════════════════════════
    @GetMapping("/policies/{id}/preview")
    public ApiResponse<List<Object>> previewPolicy(@PathVariable String id) {
        if (!policyExists(id)) {
            return ApiResponse.notFound("策略 " + id + " 不存在");
        }
        // 占位：返回空数组
        return ApiResponse.success(Collections.emptyList());
    }

    // ───────────────────────────────────────────
    // 辅助方法
    // ───────────────────────────────────────────
    private boolean policyExists(String id) {
        for (Map<String, Object> p : policies) {
            if (id.equals(String.valueOf(p.get("id")))) {
                return true;
            }
        }
        return false;
    }
}
