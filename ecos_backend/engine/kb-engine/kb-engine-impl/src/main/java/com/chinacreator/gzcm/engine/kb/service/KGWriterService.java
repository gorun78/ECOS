package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedEntity;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedRelation;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.access.graph.Neo4jClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;

/**
 * KG 写入服务 — 将 Extractor 产出的 ExtractedEntity / ExtractedRelation
 * 批量写入知识图谱存储（KnowledgeNode + KnowledgeEdge）。
 *
 * <p>负责字段映射、去重（实体按 label 幂等）和名称→ID 解析。</p>
 *
 * <p>Neo4j 连接池 (enterprise edition): 最大连接10, 最小空闲2, 30s健康检查, 自动重连。</p>
 */
@Service
public class KGWriterService {

    private static final Logger log = LoggerFactory.getLogger(KGWriterService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ── Neo4j (统一收敛 runtime-access) ──
    // M0 改造 (2026-09): Neo4j Driver 由 runtime-access/Neo4jConfig 统一管理 (收敛铁律 2.5)。
    // Pool 参数 (MAX_CONNECTION_POOL_SIZE 等) 默认在 Neo4jConfig 内设置为 10 / 30s,
    // 如需各 Service 不同 Pool 参数, 由 Neo4jConfig 提供 Factory + Bean name 区分 (后续增强)。
    private static final int MAX_CONNECTION_POOL_SIZE = 10; // 仅用于 log 显示
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final String TASK_TYPE_HEALTH_CHECK = "KG_NEO4J_HEALTH_CHECK";

    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEdgeMapper edgeMapper;
    private final ITaskManagementService taskManagementService;
    private final TaskSchedulerService taskSchedulerService;

    /**
     * Neo4j 客户端 — nullable for standard edition
     * M0 改造 (2026-09): 改用 @Autowired(required=false) 注入 runtime-access 统一 Neo4jClient.
     */
    @Autowired(required = false)
    private volatile Neo4jClient neo4jClient;

    private volatile boolean neo4jAvailable = false;

    public KGWriterService(KnowledgeNodeMapper nodeMapper,
                           KnowledgeEdgeMapper edgeMapper,
                           ITaskManagementService taskManagementService,
                           TaskSchedulerService taskSchedulerService) {
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.taskManagementService = taskManagementService;
        this.taskSchedulerService = taskSchedulerService;
    }

    // ── Neo4j 生命周期 ──

    /**
     * 初始化 Neo4j 连接池 (现已统一收敛 runtime-access/Neo4jConfig)。
     * M0 改造 (2026-09): 删除原 GraphDatabase.driver 创建逻辑, 改为消费 runtime-access
     * 提供的共享 Driver Bean (Pool 参数 10 / 30s / 10s acquisition / 30min lifetime 由 Neo4jConfig 统一定义)。
     */
    @PostConstruct
    public void neo4jPoolInit() {
        taskManagementService.registerParser(TASK_TYPE_HEALTH_CHECK, new Neo4jHealthCheckParser());
        taskManagementService.registerExecutor(TASK_TYPE_HEALTH_CHECK, new Neo4jHealthCheckExecutor());
        TaskDescription description = new TaskDescription();
        description.setTaskName("kg-neo4j-health-check");
        description.setTaskType(TASK_TYPE_HEALTH_CHECK);
        description.setDescription("KG Neo4j driver 每 30s ping 一次（enterprise/ultimate 有效；standard 无 driver 时 no-op）");
        description.setParameters(new HashMap<>());
        taskSchedulerService.schedulePeriodicTask(description, 30_000L, 30_000L);

        if (neo4jClient == null || !neo4jClient.isAvailable()) {
            neo4jAvailable = false;
            log.warn("⚠️  KGWriterService init: Neo4j 不可用 (standard 档 或 neo4j.uri 未配置), KG 写入走 PG fallback");
            return;
        }
        try {
            verifyConnectivity();
            neo4jAvailable = true;
            log.info("✅ KGWriterService init: 使用 runtime-access 统一 Neo4jClient (pool≈{} connections)", MAX_CONNECTION_POOL_SIZE);
        } catch (Exception e) {
            neo4jAvailable = false;
            log.warn("⚠️  KGWriterService init: Neo4j 连接验证失败, 走 PG fallback: {}", e.getMessage());
        }
    }

    /**
     * Neo4j 健康检查 — runtime-task 周期任务每 30s ping。
     * MATCH (n) RETURN count(n) LIMIT 1
     */
    public void neo4jHealthCheck() {
        if (neo4jClient == null || !neo4jClient.isAvailable()) {
            log.debug("Neo4j health check skipped — client not initialized");
            return;
        }
        try {
            executeWithRetry(() -> {
                List<Map<String, Object>> rows = neo4jClient.run("MATCH (n) RETURN count(n) AS cnt LIMIT 1", Map.of());
                if (!rows.isEmpty() && rows.get(0).get("cnt") instanceof Number cnt) {
                    log.debug("Neo4j health OK — node count: {}", cnt.longValue());
                }
                return null;
            });
            if (!neo4jAvailable) {
                neo4jAvailable = true;
                log.info("✅ Neo4j reconnected");
            }
        } catch (Exception e) {
            neo4jAvailable = false;
            log.warn("⚠️  Neo4j health check failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void neo4jPoolDestroy() {
        // M0 改造 (2026-09): Driver 是 runtime-access 管理的 Bean, 不在此 close (生命周期统一)
        log.info("KGWriterService neo4jPoolDestroy: Neo4j Driver 由 runtime-access 管理, 不在此处 close");
    }

    /**
     * Neo4j 连接连通性验证。
     */
    public boolean verifyConnectivity() {
        if (neo4jClient == null) return false;
        return neo4jClient.verifyConnectivity();
    }

    /**
     * Neo4j 可用性查询。
     */
    public boolean isNeo4jAvailable() {
        return neo4jAvailable && neo4jClient != null && neo4jClient.isAvailable();
    }

    /**
     * 获取 Neo4j 节点总数 (用于健康检查报告)。
     */
    public long getNeo4jNodeCount() {
        if (!isNeo4jAvailable()) return -1;
        return executeWithRetry(() -> {
            List<Map<String, Object>> rows = neo4jClient.run("MATCH (n) RETURN count(n) AS cnt", Map.of());
            if (!rows.isEmpty() && rows.get(0).get("cnt") instanceof Number cnt) {
                return cnt.longValue();
            }
            return 0L;
        });
    }

    // ── 带重试的执行器 ──

    /**
     * 自动重连：连接断开后3次重试, 间隔1s。
     */
    private <T> T executeWithRetry(Neo4jOperation<T> operation) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    log.warn("Neo4j operation failed (attempt {}/{}), retrying in {}ms...",
                            attempt, MAX_RETRY_ATTEMPTS, RETRY_DELAY_MS);
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Neo4j retry interrupted", ie);
                    }
                }
            }
        }
        throw new RuntimeException("Neo4j operation failed after " + MAX_RETRY_ATTEMPTS + " attempts", lastException);
    }

    @FunctionalInterface
    private interface Neo4jOperation<T> {
        T execute() throws Exception;
    }

    // ── Public API ──

    /**
     * 批量写入抽取的实体和关系。
     * 实体按名称（label）去重 — 已存在的实体更新属性，不重复创建。
     * 关系在实体写入完成后解析 source/target 实体名称 → node ID。
     *
     * @return 写入结果统计
     */
    public BatchWriteResult writeBatch(List<ExtractedEntity> entities, List<ExtractedRelation> relations) {
        int entitiesCreated = 0;
        int entitiesUpdated = 0;
        int relationsCreated = 0;
        int relationsSkipped = 0;

        // Phase 1: 写入/更新实体，同时构建 name→id 索引
        Map<String, String> nameToId = new HashMap<>();
        if (entities != null) {
            for (ExtractedEntity entity : entities) {
                WriteEntityResult result = writeEntity(entity);
                nameToId.put(entity.getName(), result.nodeId);
                if (result.isNew) {
                    entitiesCreated++;
                } else {
                    entitiesUpdated++;
                }
            }
        }

        // Phase 2: 写入关系（依赖 Phase 1 的 name→id 索引）
        if (relations != null) {
            for (ExtractedRelation rel : relations) {
                boolean created = writeRelation(rel, nameToId);
                if (created) {
                    relationsCreated++;
                } else {
                    relationsSkipped++;
                }
            }
        }

        log.info("Batch write complete: entities(new={}, updated={}), relations(created={}, skipped={})",
                entitiesCreated, entitiesUpdated, relationsCreated, relationsSkipped);

        return new BatchWriteResult(entitiesCreated, entitiesUpdated, relationsCreated, relationsSkipped);
    }

    // ── Entity write ──

    /**
     * 写入单个抽取实体 — 按 name→label 去重。
     * <ul>
     *   <li>实体不存在 → 创建新 KnowledgeNode</li>
     *   <li>实体已存在 → 更新 description 和 propertiesJson</li>
     * </ul>
     */
    public WriteEntityResult writeEntity(ExtractedEntity entity) {
        if (entity == null || entity.getName() == null || entity.getName().isBlank()) {
            log.warn("Skipping entity with null/blank name");
            return new WriteEntityResult(null, false);
        }

        String name = entity.getName().trim();
        KnowledgeNode existing = nodeMapper.findByLabel(name);

        if (existing != null) {
            // 已存在：合并更新
            boolean updated = false;
            if (entity.getType() != null && !entity.getType().equals(existing.getNodeType())) {
                existing.setNodeType(entity.getType());
                updated = true;
            }
            // 合并 properties
            String mergedProps = mergeProperties(existing.getPropertiesJson(), entity.getProperties());
            if (mergedProps != null && !mergedProps.equals(existing.getPropertiesJson())) {
                existing.setPropertiesJson(mergedProps);
                updated = true;
            }
            if (entity.getConfidence() > 0) {
                existing.setDescription("confidence=" + entity.getConfidence());
                updated = true;
            }
            if (updated) {
                existing.setUpdatedAt(LocalDateTime.now());
                nodeMapper.insert(existing); // re-insert for simplicity (or add update method)
            }
            log.debug("Entity '{}' already exists (id={}), updated={}", name, existing.getId(), updated);
            return new WriteEntityResult(existing.getId(), false);
        }

        // 新实体
        LocalDateTime now = LocalDateTime.now();
        KnowledgeNode node = new KnowledgeNode();
        node.setId(UUID.randomUUID().toString());
        node.setLabel(name);
        node.setNodeType(entity.getType() != null ? entity.getType() : "UNKNOWN");
        node.setDescription("confidence=" + entity.getConfidence());
        node.setPropertiesJson(serializeProperties(entity.getProperties()));
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        nodeMapper.insert(node);
        log.info("Created entity: '{}' (id={}, type={})", name, node.getId(), node.getNodeType());
        return new WriteEntityResult(node.getId(), true);
    }

    // ── Relation write ──

    /**
     * 写入单个抽取关系 — 需先通过 name→id 映射解析 source/target 实体。
     *
     * @param rel      抽取的关系
     * @param nameToId 实体名称 → node ID 的映射（由前置 entity 写入阶段构建）
     * @return true if created, false if skipped (missing source/target resolution)
     */
    public boolean writeRelation(ExtractedRelation rel, Map<String, String> nameToId) {
        if (rel == null) {
            return false;
        }

        String sourceId = nameToId.get(rel.getSourceEntity());
        String targetId = nameToId.get(rel.getTargetEntity());

        if (sourceId == null || targetId == null) {
            log.warn("Skipping relation '{}' → '{}' ({}) — source or target entity not found",
                    rel.getSourceEntity(), rel.getTargetEntity(), rel.getRelationType());
            return false;
        }

        KnowledgeEdge edge = new KnowledgeEdge();
        edge.setId(UUID.randomUUID().toString());
        edge.setSourceNodeId(sourceId);
        edge.setTargetNodeId(targetId);
        edge.setRelationship(rel.getRelationType() != null ? rel.getRelationType() : "RELATED_TO");
        edge.setWeight(rel.getConfidence());
        edge.setCreatedAt(LocalDateTime.now());
        edgeMapper.insert(edge);
        log.debug("Created relation: [{}] -[{}]-> [{}] (confidence={})",
                rel.getSourceEntity(), rel.getRelationType(), rel.getTargetEntity(), rel.getConfidence());
        return true;
    }

    // ── Helpers ──

    private String serializeProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize properties: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 合并已有 propertiesJson 和新抽取的 properties。
     * 新值覆盖旧值中同名字段。
     */
    private String mergeProperties(String existingJson, Map<String, Object> newProperties) {
        Map<String, Object> merged = new LinkedHashMap<>();

        // 解析已有 JSON
        if (existingJson != null && !existingJson.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> existing = OBJECT_MAPPER.readValue(existingJson, Map.class);
                merged.putAll(existing);
            } catch (Exception e) {
                log.debug("Could not parse existing properties: {}", e.getMessage());
            }
        }

        // 合并新属性（覆盖）
        if (newProperties != null) {
            merged.putAll(newProperties);
        }

        return merged.isEmpty() ? null : serializeProperties(merged);
    }

    // ── Result types ──

    public static class WriteEntityResult {
        public final String nodeId;
        public final boolean isNew;

        WriteEntityResult(String nodeId, boolean isNew) {
            this.nodeId = nodeId;
            this.isNew = isNew;
        }
    }

    public static class BatchWriteResult {
        public final int entitiesCreated;
        public final int entitiesUpdated;
        public final int relationsCreated;
        public final int relationsSkipped;

        BatchWriteResult(int entitiesCreated, int entitiesUpdated, int relationsCreated, int relationsSkipped) {
            this.entitiesCreated = entitiesCreated;
            this.entitiesUpdated = entitiesUpdated;
            this.relationsCreated = relationsCreated;
            this.relationsSkipped = relationsSkipped;
        }

        public int totalEntities() { return entitiesCreated + entitiesUpdated; }
        public int totalRelations() { return relationsCreated + relationsSkipped; }

        @Override
        public String toString() {
            return String.format("BatchWriteResult{entities(new=%d, updated=%d), relations(created=%d, skipped=%d)}",
                    entitiesCreated, entitiesUpdated, relationsCreated, relationsSkipped);
        }
    }

    private final class Neo4jHealthCheckParser implements ITaskParser {
        @Override
        public TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException {
            validate(taskDescription);
            TaskExecutionPlan plan = new TaskExecutionPlan();
            plan.setTaskId(taskDescription.getTaskId());
            TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
            step.setStepId("step-1");
            step.setStepName("Neo4j 健康检查");
            step.setStepType(TASK_TYPE_HEALTH_CHECK);
            step.setExecutor(TASK_TYPE_HEALTH_CHECK);
            step.setConfig(taskDescription.getParameters() == null
                    ? new HashMap<>() : new HashMap<>(taskDescription.getParameters()));
            List<TaskExecutionPlan.ExecutionStep> steps = new ArrayList<>();
            steps.add(step);
            plan.setSteps(steps);
            return plan;
        }

        @Override
        public boolean supports(String taskType) {
            return TASK_TYPE_HEALTH_CHECK.equalsIgnoreCase(taskType);
        }

        @Override
        public void validate(TaskDescription taskDescription) throws TaskParseException {
            if (taskDescription == null
                    || taskDescription.getTaskId() == null
                    || taskDescription.getTaskId().isEmpty()) {
                throw new TaskParseException("neo4j health check task id is required");
            }
            if (!supports(taskDescription.getTaskType())) {
                throw new TaskParseException("unsupported neo4j health check task type: "
                        + taskDescription.getTaskType());
            }
        }
    }

    private final class Neo4jHealthCheckExecutor implements ITaskExecutor {
        @Override
        public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback)
                throws TaskExecutionException {
            try {
                neo4jHealthCheck();
                return String.format("{\"taskType\":\"%s\",\"completedAt\":\"%s\"}",
                        TASK_TYPE_HEALTH_CHECK, java.time.Instant.now().toString());
            } catch (Exception ex) {
                log.error("Neo4j 健康检查 runtime-task 执行失败: {}", ex.getMessage(), ex);
                throw new TaskExecutionException("neo4j health check failed", ex);
            }
        }

        @Override
        public void cancel(String taskId) { }

        @Override
        public void pause(String taskId) { }

        @Override
        public void resume(String taskId) { }

        @Override
        public TaskStatus getStatus(String taskId) {
            return null;
        }
    }
}
