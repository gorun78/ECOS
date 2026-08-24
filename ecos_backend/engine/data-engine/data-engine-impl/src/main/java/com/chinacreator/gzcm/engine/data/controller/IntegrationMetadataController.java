package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.PipelineTaskService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.service.DataLineageService;
import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IntegrationMetadataController — 联邦物理元数据兼容端点（T2: 替换 CeosCompatController 内存 mock）。
 *
 * <p>路径与原 CeosCompatController 完全一致，前端无需改动：</p>
 * <ul>
 *   <li>GET  /api/integration/metadata        — 连接（查 td_datasource） + 同步任务（查 PipelineTaskService） + 血缘 + 模拟状态</li>
 *   <li>GET  /api/integration/logs            — 审计日志（当前返回空列表，无内存 mock）</li>
 *   <li>POST /api/integration/metadata/drift  — Schema 漂移 / SLA 断流 / 重置（返回成功提示，不再维护内存模拟态）</li>
 * </ul>
 *
 * <p>字段映射参照前端 mapDsToConn：datasourceId→id, datasourceName→name,
 * datasourceType→type, connectionConfig 解析 host/port/database。</p>
 */
@RestController
@RequestMapping("/api/integration")
public class IntegrationMetadataController {

    private static final Logger log = LoggerFactory.getLogger(IntegrationMetadataController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSourceService dataSourceService;
    private final PipelineTaskService pipelineTaskService;
    private final DataLineageService lineageService;

    public IntegrationMetadataController(DataSourceService dataSourceService,
                                         PipelineTaskService pipelineTaskService,
                                         DataLineageService lineageService) {
        this.dataSourceService = dataSourceService;
        this.pipelineTaskService = pipelineTaskService;
        this.lineageService = lineageService;
    }

    // ════════════════════════════════════════════
    // API: GET /api/integration/metadata
    // ════════════════════════════════════════════
    @GetMapping("/metadata")
    public ApiResponse getIntegrationMetadata() {
        try {
            // connections: 查 td_datasource 真实表
            List<Map<String, Object>> connections = new ArrayList<>();
            List<DataSourceEntity> dataSources = dataSourceService.listAll();
            if (dataSources != null) {
                for (DataSourceEntity ds : dataSources) {
                    connections.add(mapDsToConn(ds));
                }
            }

            // syncTasks: 查 PipelineTaskService 真实任务列表
            List<Map<String, Object>> syncTasks = new ArrayList<>();
            try {
                Map<String, Object> taskPage = pipelineTaskService.listTasks(1, 100);
                if (taskPage != null) {
                    Object listObj = taskPage.get("list");
                    if (listObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> list = (List<Map<String, Object>>) listObj;
                        for (Map<String, Object> task : list) {
                            syncTasks.add(mapTaskToSync(task));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("查询同步任务列表失败: {}", e.getMessage());
            }

            // lineage: 调 DataLineageService 真实血缘（列出已有节点/边作为概览）
            Map<String, Object> lineage = new LinkedHashMap<>();
            try {
                List<Map<String, Object>> nodes = lineageService.listNodes();
                List<Map<String, Object>> edges = lineageService.listEdges();
                // 兼容前端 nodes/links 字段名
                lineage.put("nodes", nodes != null ? nodes : new ArrayList<>());
                lineage.put("links", edges != null ? edges : new ArrayList<>());
            } catch (Exception e) {
                log.warn("查询血缘概览失败: {}", e.getMessage());
                lineage.put("nodes", new ArrayList<>());
                lineage.put("links", new ArrayList<>());
            }

            // simulationState: 不再维护内存模拟态，始终返回健康
            Map<String, Object> simState = new LinkedHashMap<>();
            simState.put("isSchemaDriftActive", false);
            simState.put("isSlaBreachActive", false);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connections", connections);
            result.put("syncTasks", syncTasks);
            result.put("lineage", lineage);
            result.put("simulationState", simState);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to retrieve integration metadata", e);
            return ApiResponse.error(500, "Failed to retrieve metadata: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════
    // API: GET /api/integration/logs
    // ════════════════════════════════════════════
    @GetMapping("/logs")
    public ApiResponse getIntegrationLogs() {
        // 无内存 mock 审计日志；返回空列表保持契约。
        // 若将来接入 ecos_audit_log，可在此处查询并映射为 severity/event/details。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logs", new ArrayList<>());
        return ApiResponse.success(result);
    }

    // ════════════════════════════════════════════
    // API: POST /api/integration/metadata/drift
    // ════════════════════════════════════════════
    @PostMapping("/metadata/drift")
    public ApiResponse triggerDrift(@RequestBody Map<String, Object> body) {
        String type = body == null ? "reset" : (String) body.getOrDefault("type", "reset");
        // 不再维护内存模拟态；返回成功提示以保持前端契约。
        String message;
        switch (type) {
            case "drift" -> message = "Schema 漂移检测请求已受理（实时检测由数据质量引擎处理）。";
            case "sla" -> message = "SLA 断流告警请求已受理（实时监控由调度引擎处理）。";
            default -> message = "重置成功，全网状态恢复至正常稳定状态。";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        return ApiResponse.success(result);
    }

    // ════════════════════════════════════════════
    // 字段映射（参照前端 mapDsToConn）
    // ════════════════════════════════════════════

    /**
     * 将 DataSourceEntity 映射为前端 connection 结构。
     * datasourceId→id, datasourceName→name, datasourceType→type,
     * connectionConfig(JSON) 解析 host/port/database/username。
     */
    private Map<String, Object> mapDsToConn(DataSourceEntity ds) {
        Map<String, Object> conn = new LinkedHashMap<>();
        conn.put("id", ds.getDatasourceId());
        conn.put("name", ds.getDatasourceName());
        conn.put("type", ds.getDatasourceType());
        // status: 映射为前端期望的连通状态
        String status = ds.getStatus();
        if (status == null) {
            status = "unknown";
        } else if ("active".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status)) {
            status = "connected";
        } else if ("error".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)) {
            status = "error";
        }
        conn.put("status", status);

        // config: 解析 connectionConfig JSON 提取 host/port/database/username
        Map<String, Object> config = new LinkedHashMap<>();
        String cfg = ds.getConnectionConfig();
        if (cfg != null && !cfg.isBlank()) {
            try {
                Map<String, Object> parsed = MAPPER.readValue(cfg, new TypeReference<Map<String, Object>>() {});
                // 兼容多种字段名
                copyFirst(parsed, config, "host", "host");
                copyFirst(parsed, config, "port", "port");
                copyFirst(parsed, config, "database", "database", "dbName", "databaseName");
                copyFirst(parsed, config, "username", "username", "user");
                // 若有 jdbcUrl，也透传
                if (parsed.containsKey("jdbcUrl")) {
                    config.put("jdbcUrl", parsed.get("jdbcUrl"));
                }
            } catch (Exception e) {
                log.debug("解析 connectionConfig 失败 (ds={}): {}", ds.getDatasourceId(), e.getMessage());
                config.put("raw", cfg);
            }
        }
        if (ds.getLastTestTime() != null) {
            config.put("lastTested", ds.getLastTestTime().toString());
        }
        conn.put("config", config);

        // tablesAvailable: 真实表结构需查 catalog，此处返回空列表避免 mock
        conn.put("tablesAvailable", new ArrayList<>());
        return conn;
    }

    /** 从 parsed 中按候选键取第一个非空值放入 config 的 targetKey。 */
    private void copyFirst(Map<String, Object> parsed, Map<String, Object> config,
                           String targetKey, String... candidateKeys) {
        for (String key : candidateKeys) {
            Object val = parsed.get(key);
            if (val != null) {
                config.put(targetKey, val);
                return;
            }
        }
    }

    /**
     * 将 PipelineTask 行映射为前端 syncTask 结构。
     */
    private Map<String, Object> mapTaskToSync(Map<String, Object> task) {
        Map<String, Object> sync = new LinkedHashMap<>();
        sync.put("id", task.getOrDefault("id", ""));
        sync.put("name", task.getOrDefault("name", ""));
        sync.put("engine", "ECOS Pipeline 2.0");
        Object cron = task.get("cron_expression");
        sync.put("schedule", cron != null ? cron : "");
        // status: 映射 DRAFT/RUNNING 等为前端期望
        Object statusObj = task.get("status");
        String status = statusObj != null ? statusObj.toString().toLowerCase() : "unknown";
        sync.put("status", status);
        sync.put("slaMinutes", 0);
        sync.put("actualDelayMinutes", 0);
        Object updatedAt = task.get("updated_at");
        if (updatedAt != null) {
            sync.put("lastRunTime", updatedAt.toString());
        }
        return sync;
    }
}
