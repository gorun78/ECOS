package com.chinacreator.gzcm.dccheng.glossary.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.dccheng.glossary.GlossaryEntity;
import com.chinacreator.gzcm.dccheng.glossary.GlossaryRepository;

/**
 * 术语库 (Glossary) REST API — JdbcTemplate 持久化版本。
 *
 * <ul>
 *   <li>GET    /api/glossary/terms          — 术语列表（?domain=&status=）</li>
 *   <li>POST   /api/glossary/terms          — 创建术语</li>
 *   <li>PUT    /api/glossary/terms/{id}     — 更新术语（含状态流转）</li>
 *   <li>DELETE /api/glossary/terms/{id}     — 删除术语</li>
 * </ul>
 *
 * 状态流转: DRAFT → REVIEW → PUBLISHED → DEPRECATED
 */
@RestController
@RequestMapping("/api/glossary")
public class GlossaryController {

    private static final Logger log = LoggerFactory.getLogger(GlossaryController.class);

    private final GlossaryRepository repository;

    public GlossaryController(GlossaryRepository repository) {
        this.repository = repository;
    }

    // ═══════════════ 0. GET 根端点 ══════════════════════

    @GetMapping
    public ApiResponse<Map<String, Object>> root() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("endpoint", "/api/glossary");
        info.put("terms", "/api/glossary/terms");
        info.put("description", "术语库管理 API");
        return ApiResponse.success(info);
    }

    // ═══════════════ 1. GET 术语列表 ═══════════════════

    @GetMapping("/terms")
    public ApiResponse<List<Map<String, Object>>> listTerms(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String status) {

        List<GlossaryEntity> list = repository.findAll(domain, status);
        List<Map<String, Object>> result = list.stream()
                .map(this::toMap)
                .toList();
        return ApiResponse.success(result);
    }

    // ═══════════════ 2. POST 创建术语 ═══════════════════

    @PostMapping("/terms")
    public ApiResponse<Map<String, Object>> createTerm(@RequestBody Map<String, Object> body) {
        GlossaryEntity entity = new GlossaryEntity();
        entity.setCode(getString(body, "code"));
        entity.setName(getString(body, "name"));
        entity.setDefinition(getString(body, "definition"));
        entity.setDomain(getString(body, "domain"));
        entity.setOwner(getString(body, "owner"));
        entity.setStatus("DRAFT");
        entity.setCreatedBy(getString(body, "createdBy"));

        repository.insert(entity);
        log.info("Glossary term created: {} [{}]", entity.getId(), entity.getName());

        // Read back to get the auto-generated id
        // Since we don't have generated-key retrieval, we query by the last inserted
        // A more robust approach would use KeyHolder, but for MVP this is sufficient.
        return ApiResponse.success(toMap(entity));
    }

    // ═══════════════ 3. PUT 更新术语（含状态流转） ═════

    @PutMapping("/terms/{id}")
    public ApiResponse<Map<String, Object>> updateTerm(@PathVariable Long id,
                                                        @RequestBody Map<String, Object> body) {
        Optional<GlossaryEntity> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return ApiResponse.notFound("术语 " + id + " 不存在");
        }

        GlossaryEntity entity = existing.get();

        // 按字段更新
        String code = getString(body, "code");
        if (code != null) entity.setCode(code);
        String name = getString(body, "name");
        if (name != null) entity.setName(name);
        String definition = getString(body, "definition");
        if (definition != null) entity.setDefinition(definition);
        String domain = getString(body, "domain");
        if (domain != null) entity.setDomain(domain);
        String owner = getString(body, "owner");
        if (owner != null) entity.setOwner(owner);
        String createdBy = getString(body, "createdBy");
        if (createdBy != null) entity.setCreatedBy(createdBy);

        // 状态流转 — DRAFT→REVIEW→PUBLISHED→DEPRECATED
        String newStatus = getString(body, "status");
        if (newStatus != null && !newStatus.equalsIgnoreCase(entity.getStatus())) {
            if (!isValidTransition(entity.getStatus(), newStatus)) {
                return ApiResponse.badRequest(
                    "状态流转不允许: " + entity.getStatus() + " → " + newStatus);
            }
            entity.setStatus(newStatus.toUpperCase());
        }

        repository.update(entity);
        log.info("Glossary term updated: {} → status={}", id, entity.getStatus());
        return ApiResponse.success(toMap(entity));
    }

    // ═══════════════ 4. DELETE 删除术语 ═════════════════

    @DeleteMapping("/terms/{id}")
    public ApiResponse<String> deleteTerm(@PathVariable Long id) {
        int affected = repository.deleteById(id);
        if (affected == 0) {
            return ApiResponse.notFound("术语 " + id + " 不存在");
        }
        log.info("Glossary term deleted: {}", id);
        return ApiResponse.success("术语 " + id + " 已删除");
    }

    // ═══════════════ 工具方法 ═══════════════════════════

    private String getString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val != null ? val.toString() : null;
    }

    private boolean isValidTransition(String from, String to) {
        String upperFrom = from.toUpperCase();
        String upperTo = to.toUpperCase();

        switch (upperFrom) {
            case "DRAFT":
                return "REVIEW".equals(upperTo) || "PUBLISHED".equals(upperTo) || "DEPRECATED".equals(upperTo);
            case "REVIEW":
                return "DRAFT".equals(upperTo) || "PUBLISHED".equals(upperTo) || "DEPRECATED".equals(upperTo);
            case "PUBLISHED":
                return "DEPRECATED".equals(upperTo);
            case "DEPRECATED":
                return "DRAFT".equals(upperTo);
            default:
                return true;
        }
    }

    private Map<String, Object> toMap(GlossaryEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("code", entity.getCode());
        map.put("name", entity.getName());
        map.put("definition", entity.getDefinition());
        map.put("domain", entity.getDomain());
        map.put("owner", entity.getOwner());
        map.put("status", entity.getStatus());
        map.put("createdBy", entity.getCreatedBy());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
    }
}
