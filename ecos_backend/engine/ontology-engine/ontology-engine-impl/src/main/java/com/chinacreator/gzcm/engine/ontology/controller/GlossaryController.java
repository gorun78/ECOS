package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGlossarySaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGlossaryVO;
import com.chinacreator.gzcm.engine.ontology.glossary.GlossaryEntity;
import com.chinacreator.gzcm.engine.ontology.glossary.GlossaryRepository;

/**
 * 术语库 (Glossary) REST API — GlossaryRepository 持久化版本。
 *
 * <ul>
 *   <li>GET    /api/v1/ontology/glossary/terms          — 术语列表（?domain=&status=）</li>
 *   <li>POST   /api/v1/ontology/glossary/terms          — 创建术语</li>
 *   <li>PUT    /api/v1/ontology/glossary/terms/{id}     — 更新术语（含状态流转）</li>
 *   <li>DELETE /api/v1/ontology/glossary/terms/{id}     — 删除术语</li>
 * </ul>
 *
 * 状态流转: DRAFT → REVIEW → PUBLISHED → DEPRECATED
 *
 * <p>T16-3 (2026-09-13)：入参 Map → {@link OntologyGlossarySaveDTO}，
 * 返回 Map → {@link OntologyGlossaryVO}。{@code root()} 端点保留原
 * 自描述（硬编码端点路径 Map，非数据载体，豁免）。
 */
@RestController
@RequestMapping("/api/v1/ontology/glossary")
public class GlossaryController {

    private static final Logger log = LoggerFactory.getLogger(GlossaryController.class);

    private final GlossaryRepository repository;

    public GlossaryController(GlossaryRepository repository) {
        this.repository = repository;
    }

    // ═══════════════ 0. GET 根端点 ══════════════════════

    /**
     * 根端点自描述（硬编码端点路径，非业务数据载体，豁免强类型）。
     */
    @GetMapping
    public ApiResponse<java.util.Map<String, Object>> root() {
        java.util.Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("endpoint", "/api/v1/ontology/glossary");
        info.put("terms", "/api/v1/ontology/glossary/terms");
        info.put("description", "术语库管理 API");
        return ApiResponse.success(info);
    }

    // ═══════════════ 1. GET 术语列表 ═══════════════════

    /** 术语列表（强类型 VO）。 */
    @GetMapping("/terms")
    public ApiResponse<List<OntologyGlossaryVO>> listTerms(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String status) {
        List<GlossaryEntity> list = repository.findAll(domain, status);
        return ApiResponse.success(list.stream().map(this::toVO).collect(Collectors.toList()));
    }

    // ═══════════════ 2. POST 创建术语 ═══════════════════

    /** 创建术语 — 接收 {@link OntologyGlossarySaveDTO}，返回 VO。 */
    @PostMapping("/terms")
    public ApiResponse<OntologyGlossaryVO> createTerm(@RequestBody OntologyGlossarySaveDTO dto) {
        GlossaryEntity entity = new GlossaryEntity();
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setDefinition(dto.getDefinition());
        entity.setDomain(dto.getDomain());
        entity.setOwner(dto.getOwner());
        entity.setStatus("DRAFT");
        entity.setCreatedBy(dto.getCreatedBy());

        repository.insert(entity);
        log.info("Glossary term created: {} [{}]", entity.getId(), entity.getName());
        return ApiResponse.success(toVO(entity));
    }

    // ═══════════════ 3. PUT 更新术语（含状态流转） ═════

    /** 更新术语 — 接收 {@link OntologyGlossarySaveDTO}，按状态流转，返回 VO。 */
    @PutMapping("/terms/{id}")
    public ApiResponse<OntologyGlossaryVO> updateTerm(@PathVariable Long id,
                                                        @RequestBody OntologyGlossarySaveDTO dto) {
        Optional<GlossaryEntity> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return ApiResponse.notFound("术语 " + id + " 不存在");
        }

        GlossaryEntity entity = existing.get();

        // 按字段映射（null 表不动）
        if (dto.getCode() != null) entity.setCode(dto.getCode());
        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getDefinition() != null) entity.setDefinition(dto.getDefinition());
        if (dto.getDomain() != null) entity.setDomain(dto.getDomain());
        if (dto.getOwner() != null) entity.setOwner(dto.getOwner());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());

        // 状态流转 — DRAFT→REVIEW→PUBLISHED→DEPRECATED
        String newStatus = dto.getStatus();
        if (newStatus != null && !newStatus.equalsIgnoreCase(entity.getStatus())) {
            if (!isValidTransition(entity.getStatus(), newStatus)) {
                return ApiResponse.badRequest(
                    "状态流转不允许: " + entity.getStatus() + " → " + newStatus);
            }
            entity.setStatus(newStatus.toUpperCase());
        }

        repository.update(entity);
        log.info("Glossary term updated: {} → status={}", id, entity.getStatus());
        return ApiResponse.success(toVO(entity));
    }

    // ═══════════════ 4. DELETE 删除术语 ═════════════════

    /** 删除术语。 */
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

    /** 状态流转合法性判定 — DRAFT→REVIEW→PUBLISHED→DEPRECATED，DEPRECATED 可回 DRAFT。 */
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

    /** GlossaryEntity → VO 映射（时间字段 LocalDateTime → ISO 字符串）。 */
    private OntologyGlossaryVO toVO(GlossaryEntity entity) {
        OntologyGlossaryVO vo = new OntologyGlossaryVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setDefinition(entity.getDefinition());
        vo.setDomain(entity.getDomain());
        vo.setOwner(entity.getOwner());
        vo.setStatus(entity.getStatus());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        vo.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return vo;
    }
}
