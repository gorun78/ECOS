package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyConfigDefaultVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyConfigItem;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyConfigRefreshVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyConfigSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyConfigVO;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 本体配置管理 Controller — T16-3。
 *
 * <p>入参/返回改强类型（DTO/VO）：
 * <ul>
 *   <li>{@code GET} 返回 {@link OntologyConfigVO}</li>
 *   <li>{@code GET /defaults} 返回 {@link OntologyConfigDefaultVO}</li>
 *   <li>{@code PUT} 入参 {@link OntologyConfigSaveDTO}</li>
 *   <li>{@code POST /refresh} 返回 {@link OntologyConfigRefreshVO}</li>
 * </ul>
 *
 * <p>说明：{@link SysConfigService} 在 sysman-impl 横切底座（不动），
 * 返回 raw {@code Map<String,Object>} PG 行；本 Controller 层从 Map
 * 重建 VO（白名单字段，未知列忽略）；{@code updateBatch} 在 Controller
 * 层组装 {@code List<Map<String,String>>} 透传 Service（C1 兼容路径）。
 */
@RestController
@RequestMapping("/api/v1/engine/ontology/settings")
public class OntologyConfigController {

    private static final Logger log = LoggerFactory.getLogger(OntologyConfigController.class);
    private static final AtomicBoolean defaultsLoaded = new AtomicBoolean(false);

    private static final String[][] DEFAULTS = {
        {"ontology.graph.backend", "pg", "ontology-engine", "enum", "图存储后端 pg/neo4j"},
        {"ontology.graph.neo4j.uri", "bolt://localhost:7687", "ontology-engine", "string", "Neo4j连接URI"},
        {"ontology.graph.neo4j.user", "neo4j", "ontology-engine", "string", "Neo4j用户名"},
        {"ontology.graph.neo4j.password", "neo4j123", "ontology-engine", "password", "Neo4j密码"},
        {"ontology.cache.ttl_seconds", "300", "ontology-engine", "int", "缓存TTL秒数"},
        {"ontology.version.max_versions", "10", "ontology-engine", "int", "最大版本数"},
        {"ontology.version.auto_version", "true", "ontology-engine", "bool", "自动版本化"},
        {"ontology.version.require_approval", "true", "ontology-engine", "bool", "版本发布需审批"},
        {"ontology.import.batch_size", "1000", "ontology-engine", "int", "导入批次大小"},
        {"ontology.export.format", "jsonld", "ontology-engine", "enum", "导出格式 jsonld/owl/ttl"},
        {"ontology.proposal.auto_publish", "false", "ontology-engine", "bool", "提案自动发布"},
        {"ontology.workflow.engine", "buszhi", "ontology-engine", "string", "工作流引擎"},
    };

    private final SysConfigService sysConfigService;

    public OntologyConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @PostConstruct
    public void init() {
        if (defaultsLoaded.compareAndSet(false, true)) {
            loadDefaults();
        }
    }

    /** GET / — 全部本体配置项强类型列表。 */
    @GetMapping
    public ApiResponse<List<OntologyConfigVO>> getAll() {
        return ApiResponse.success(toVOList(sysConfigService.listByGroup("ontology-engine")));
    }

    /** GET /defaults — 本体默认配置（key → value）。 */
    @GetMapping("/defaults")
    public ApiResponse<OntologyConfigDefaultVO> getDefaults() {
        OntologyConfigDefaultVO vo = new OntologyConfigDefaultVO();
        for (String[] row : DEFAULTS) {
            vo.getDefaults().put(row[0], row[1]);
        }
        return ApiResponse.success(vo);
    }

    /** GET /{group} — 按分组查询配置项强类型列表。 */
    @GetMapping("/{group}")
    public ApiResponse<List<OntologyConfigVO>> getByGroup(@PathVariable String group) {
        String fullGroup = "ontology-" + group;
        return ApiResponse.success(toVOList(sysConfigService.listByGroup(fullGroup)));
    }

    /**
     * PUT / — 批量更新配置。
     *
     * <p>入参 {@link OntologyConfigSaveDTO}（{@code items: List<Item>}）。
     * Controller 层组装 Service 期望的 {@code List<Map<String,String>>}
     * 透传，避免改 sysman-impl 横切底座方法签名（C1 兼容）。
     */
    @PutMapping
    public ApiResponse<Map<String, Object>> batchUpdate(@RequestBody OntologyConfigSaveDTO dto) {
        List<Map<String, String>> updates = new ArrayList<>();
        if (dto.getItems() != null) {
            for (OntologyConfigItem item : dto.getItems()) {
                if (item == null || item.getConfigKey() == null) {
                    continue;
                }
                Map<String, String> m = new LinkedHashMap<>();
                m.put("config_key", item.getConfigKey());
                m.put("config_value", item.getConfigValue());
                updates.add(m);
            }
        }
        int count = sysConfigService.updateBatch(updates);
        return ApiResponse.success(Map.of("updated", count));
    }

    /** POST /refresh — 刷新配置缓存，返回刷新后的缓存规模。 */
    @PostMapping("/refresh")
    public ApiResponse<OntologyConfigRefreshVO> refresh() {
        sysConfigService.refreshCache();
        OntologyConfigRefreshVO vo = new OntologyConfigRefreshVO();
        vo.setCacheSize(sysConfigService.cacheSize());
        return ApiResponse.success(vo);
    }

    private void loadDefaults() {
        try {
            for (String[] row : DEFAULTS) {
                sysConfigService.upsertValue(row[0], row[1], row[2], row[3], row[4]);
            }
            log.info("Ontology engine defaults loaded ({} items)", DEFAULTS.length);
        } catch (Exception e) {
            log.warn("Failed to load ontology defaults: {}", e.getMessage());
        }
    }

    /** 将 Map 行列表转换为强类型 VO 列表（白名单字段，未知列忽略）。 */
    private List<OntologyConfigVO> toVOList(List<Map<String, Object>> rows) {
        if (rows == null) {
            return new ArrayList<>();
        }
        return rows.stream().map(this::toVO).collect(Collectors.toList());
    }

    private OntologyConfigVO toVO(Map<String, Object> row) {
        OntologyConfigVO vo = new OntologyConfigVO();
        if (row == null) {
            return vo;
        }
        vo.setId(objToString(row.get("id")));
        vo.setConfigKey(objToString(row.get("config_key")));
        vo.setConfigValue(objToString(row.get("config_value")));
        vo.setConfigGroup(objToString(row.get("config_group")));
        vo.setConfigType(objToString(row.get("config_type")));
        vo.setConfigLabel(objToString(row.get("config_label")));
        vo.setDescription(objToString(row.get("description")));
        vo.setSortOrder(objToInt(row.get("sort_order")));
        vo.setStatus(objToString(row.get("status")));
        vo.setEdition(objToString(row.get("edition")));
        vo.setCreatedAt(objToString(row.get("created_at")));
        vo.setUpdatedAt(objToString(row.get("updated_at")));
        return vo;
    }

    private static String objToString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Integer objToInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
