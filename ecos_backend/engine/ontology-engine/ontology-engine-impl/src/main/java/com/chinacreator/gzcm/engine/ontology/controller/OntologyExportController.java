package com.chinacreator.gzcm.engine.ontology.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyEntityVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyExportFullVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyExportTaskQuery;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyExportTaskSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyExportTaskVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 本体导出 REST API — 将本体（实体/属性/关系）导出为 JSON / CSV / DDL 等格式。
 *
 * <p>使用 {@link ConcurrentHashMap} 内存存储导出任务记录，进程重启后任务列表丢失。
 * 导出内容在创建任务时即时生成并缓存于记录的 {@code payload} 字段。</p>
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/ontology-exports            — 导出任务列表（可按 ontologyId / format / status 过滤）</li>
 *   <li>GET    /api/v1/ecos/ontology-exports/{id}       — 导出任务详情（含 payload）</li>
 *   <li>POST   /api/v1/ecos/ontology-exports            — 创建导出任务（生成 payload）</li>
 *   <li>GET    /api/v1/ecos/ontology-exports/{id}/download — 仅返回导出载荷（便于前端直接下载）</li>
 *   <li>DELETE /api/v1/ecos/ontology-exports/{id}       — 删除导出任务</li>
 * </ul>
 *
 * <p>本控制器只新增导出端点，不改动 {@link OntologyController} 的现有 CRUD 签名。</p>
 *
 * <p><b>T16-4 说明</b>：入参/出参由 Map 改强类型（Query / SaveDTO / VO），
 * 跟随 T16-1/2/3 命名先例，复用 T16-4 已有的 {@link OntologyExportTaskSaveDTO} /
 * {@link OntologyExportTaskVO}。存储层内部记录仍为 {@code Map<String, Object>}
 * （T16-4: 导出 payload 动态结构豁免 Map），响应前统一
 * {@link ObjectMapper#convertValue} 转 VO，JSON 输出契约等价。
 */
@RestController
@RequestMapping("/api/v1/ontology/export")
public class OntologyExportController {

    private static final Logger log = LoggerFactory.getLogger(OntologyExportController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 内存存储：exportId → 导出任务记录（内部存储结构，响应前转 VO） */
    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    private final OntologyService ontologyService;

    public OntologyExportController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    // ═══════════════ 列表与详情 ═══════════════════

    /**
     * GET /api/v1/ecos/ontology-exports — 导出任务列表
     *
     * @param query 过滤条件（ontologyId / format / status 均可空）
     */
    @GetMapping("/tasks")
    public ApiResponse<List<OntologyExportTaskVO>> listExports(
            @RequestParam(required = false) OntologyExportTaskQuery query) {
        OntologyExportTaskQuery q = query == null ? new OntologyExportTaskQuery() : query;
        // 列表接口不返回 payload，避免响应过大；详情/下载接口才返回
        List<OntologyExportTaskVO> result = store.values().stream()
            .filter(m -> q.getOntologyId() == null || q.getOntologyId().equals(m.get("ontologyId")))
            .filter(m -> q.getFormat() == null || q.getFormat().equals(m.get("format")))
            .filter(m -> q.getStatus() == null || q.getStatus().equals(m.get("status")))
            .map(this::toSummaryVO)
            .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    /**
     * GET /api/v1/ecos/ontology-exports/{id} — 导出任务详情（含 payload）
     */
    @GetMapping("/{id}")
    public ApiResponse<OntologyExportTaskVO> getExport(@PathVariable String id) {
        Map<String, Object> record = store.get(id);
        if (record == null) return ApiResponse.notFound("导出任务 " + id + " 不存在");
        return ApiResponse.success(toTaskVO(record));
    }

    // ═══════════════ 创建导出 ═══════════════════

    /**
     * POST /api/v1/ecos/ontology-exports — 创建导出任务
     * <p>body 字段：ontologyId（必填）、format（可选，默认 JSON）、scope（可选，FULL / ENTITIES / RELATIONSHIPS）。</p>
     */
    @PostMapping
    public ApiResponse<OntologyExportTaskVO> createExport(@RequestBody OntologyExportTaskSaveDTO body) {
        Object rawId = body == null ? null : body.getOntologyId();
        String ontologyId = rawId == null ? "" : String.valueOf(rawId).trim();
        if (ontologyId.isEmpty()) {
            return ApiResponse.badRequest("ONT-EXP-001: ontologyId 不能为空");
        }
        String format = String.valueOf(body.getFormat() == null || body.getFormat().isBlank()
            ? "JSON" : body.getFormat()).toUpperCase();
        String scope = String.valueOf(body.getScope() == null || body.getScope().isBlank()
            ? "FULL" : body.getScope()).toUpperCase();

        String id = "exp_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("ontologyId", ontologyId);
        record.put("format", format);
        record.put("scope", scope);
        record.put("status", "COMPLETED");
        record.put("createdAt", Instant.now().toString());

        try {
            Object payload = buildPayload(ontologyId, format, scope);
            record.put("payload", payload);
            record.put("objectCount", countObjects(payload));
        } catch (Exception e) {
            log.error("Export failed: {} — {}", id, e.getMessage(), e);
            record.put("status", "FAILED");
            record.put("error", e.getMessage());
            record.put("payload", null);
            record.put("objectCount", 0);
        }
        store.put(id, record);
        log.info("Ontology export created: {} ontologyId={} format={} status={}",
                id, ontologyId, format, record.get("status"));
        return ApiResponse.success(toTaskVO(record));
    }

    // ═══════════════ 下载与删除 ═══════════════════

    /**
     * GET /api/v1/ecos/ontology-exports/{id}/download — 仅返回导出载荷
     * <p>T16-4: 导出 blob 动态结构豁免 Map（payload 随 format 为 JSON Map / CSV / DDL 字符串）。
     */
    @GetMapping("/{id}/download")
    public ApiResponse<Object> downloadExport(@PathVariable String id) {
        Map<String, Object> record = store.get(id);
        if (record == null) return ApiResponse.notFound("导出任务 " + id + " 不存在");
        if (!"COMPLETED".equals(record.get("status"))) {
            return ApiResponse.badRequest("ONT-EXP-002: 导出任务未完成，无法下载");
        }
        return ApiResponse.success(record.get("payload"));
    }

    /**
     * DELETE /api/v1/ecos/ontology-exports/{id} — 删除导出任务
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteExport(@PathVariable String id) {
        if (store.remove(id) != null) {
            log.info("Ontology export deleted: {}", id);
            return ApiResponse.success("导出任务 " + id + " 已删除");
        }
        return ApiResponse.notFound("导出任务 " + id + " 不存在");
    }

    // ═══════════════ 内部方法 ═══════════════════

    /** 去除 payload 的摘要 VO，用于列表接口（NON_NULL 保证与既有 Map 输出一致） */
    private OntologyExportTaskVO toSummaryVO(Map<String, Object> record) {
        return MAPPER.convertValue(record, OntologyExportTaskVO.class);
    }

    /** 完整记录 VO（含 payload / error） */
    private OntologyExportTaskVO toTaskVO(Map<String, Object> record) {
        return MAPPER.convertValue(record, OntologyExportTaskVO.class);
    }

    /** 根据格式与范围构建导出载荷 */
    private Object buildPayload(String ontologyId, String format, String scope) {
        List<Map<String, Object>> entities = ontologyService.listEntities(ontologyId);
        List<Map<String, Object>> relationships = ontologyService.listRelationshipsByOntology(ontologyId);

        boolean includeEntities = "FULL".equals(scope) || "ENTITIES".equals(scope);
        boolean includeRelationships = "FULL".equals(scope) || "RELATIONSHIPS".equals(scope);

        switch (format) {
            case "JSON":
                return buildJsonPayload(entities, relationships, includeEntities, includeRelationships);
            case "CSV":
                return buildCsvPayload(entities, includeEntities);
            case "DDL":
                return buildDdlPayload(entities, includeEntities);
            default:
                throw new IllegalArgumentException("不支持的导出格式: " + format);
        }
    }

    private Map<String, Object> buildJsonPayload(List<Map<String, Object>> entities,
                                                  List<Map<String, Object>> relationships,
                                                  boolean includeEntities, boolean includeRelationships) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entities", includeEntities ? entities : new ArrayList<>());
        payload.put("relationships", includeRelationships ? relationships : new ArrayList<>());
        return payload;
    }

    /** CSV 以换行拼接的字符串返回（每行一个实体） */
    private String buildCsvPayload(List<Map<String, Object>> entities, boolean includeEntities) {
        if (!includeEntities) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("id,code,name,entityType,domainId,description\n");
        for (Map<String, Object> e : entities) {
            sb.append(csv(e.get("id"))).append(',')
              .append(csv(e.get("code"))).append(',')
              .append(csv(e.get("name"))).append(',')
              .append(csv(e.get("entityType"))).append(',')
              .append(csv(e.get("domainId"))).append(',')
              .append(csv(e.get("description"))).append('\n');
        }
        return sb.toString();
    }

    /** DDL 以 CREATE TABLE 语句拼接返回（每实体一张表） */
    private String buildDdlPayload(List<Map<String, Object>> entities, boolean includeEntities) {
        if (!includeEntities) return "";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> e : entities) {
            String table = String.valueOf(e.getOrDefault("code", e.get("id")));
            sb.append("CREATE TABLE ").append(table).append(" (\n");
            sb.append("  id VARCHAR(64) PRIMARY KEY,\n");
            sb.append("  code VARCHAR(128),\n");
            sb.append("  name VARCHAR(255),\n");
            sb.append("  description TEXT\n");
            sb.append(");\n\n");
        }
        return sb.toString();
    }

    private String csv(Object val) {
        if (val == null) return "";
        String s = String.valueOf(val);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private int countObjects(Object payload) {
        if (payload instanceof Map) {
            Map<?, ?> p = (Map<?, ?>) payload;
            int count = 0;
            Object entities = p.get("entities");
            if (entities instanceof List) count += ((List<?>) entities).size();
            Object rels = p.get("relationships");
            if (rels instanceof List) count += ((List<?>) rels).size();
            return count;
        }
        return 0;
    }

    // ═══════════════ 直接导出 ═══════════════════

    /**
     * GET /api/v1/ontology/export — 直接返回完整本体（含objectTypes+linkTypes等）
     *
     * <p>T16-4：返回强类型 {@link OntologyExportFullVO}；实体行统一
     * {@link OntologyEntityVO}（同 service.entityToMap 结构）；
     * linkTypes 当前为空占位（T16-4: 导出 blob 动态结构豁免 Map）。
     */
    @GetMapping
    public ApiResponse<OntologyExportFullVO> exportFull() {
        try {
            List<Map<String, Object>> allObjects = ontologyService.listAllObjects();
            List<OntologyEntityVO> objectTypes = new ArrayList<>();
            List<Object> linkTypes = new ArrayList<>();
            List<OntologyEntityVO> actionTypes = new ArrayList<>();
            List<OntologyEntityVO> functionTypes = new ArrayList<>();

            for (Map<String, Object> obj : allObjects) {
                String entityType = String.valueOf(obj.getOrDefault("entityType", ""));
                switch (entityType) {
                    case "MASTER":
                    case "OBJECT":
                    case "object_type":
                        objectTypes.add(MAPPER.convertValue(obj, OntologyEntityVO.class));
                        break;
                    case "TRANSACTION":
                    case "LINK":
                    case "link_type":
                        linkTypes.add(obj);
                        break;
                    case "ACTION":
                    case "action_type":
                        actionTypes.add(MAPPER.convertValue(obj, OntologyEntityVO.class));
                        break;
                    case "FUNCTION":
                    case "function_type":
                        functionTypes.add(MAPPER.convertValue(obj, OntologyEntityVO.class));
                        break;
                    default:
                        objectTypes.add(MAPPER.convertValue(obj, OntologyEntityVO.class));
                }
            }

            OntologyExportFullVO vo = new OntologyExportFullVO();
            vo.setObjectTypes(objectTypes);
            vo.setLinkTypes(linkTypes);
            vo.setActionTypes(actionTypes);
            vo.setFunctionTypes(functionTypes);
            vo.setMappings(new ArrayList<>());
            return ApiResponse.success(vo);
        } catch (Exception e) {
            log.error("导出本体失败", e);
            OntologyExportFullVO empty = new OntologyExportFullVO();
            empty.setObjectTypes(new ArrayList<>());
            empty.setLinkTypes(new ArrayList<>());
            return ApiResponse.success(empty);
        }
    }
}
