package com.chinacreator.gzcm.workspace.service;

import com.chinacreator.gzcm.runtime.access.graph.Neo4jClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;

/**
 * A2 — Object 实例 → KG 节点同步引擎。
 *
 * <p>在 ObjectController 的 CRUD 操作中异步将对象实例同步到 Neo4j 知识图谱。
 * 所有 Neo4j 写入均为异步执行，异常仅记录日志，不抛给主业务流程。</p>
 *
 * <p>卷07 F07-17 底座收口：异步通道由 {@code CompletableFuture.runAsync}（common pool）
 * 改为 workspace 命名执行器 {@code workspaceTaskExecutor}
 * （{@code WorkspaceTaskExecutorConfig}，禁 common pool、禁裸线程）。runtime-task 仅提供
 * DB 持久化任务计划门面、无通用一次性异步执行门面，故按 F07-17 兜底条款走命名池。</p>
 */
@Service
public class ObjectKgSyncService {

    private static final Logger log = LoggerFactory.getLogger(ObjectKgSyncService.class);

    // M0 改造 (2026-09): Neo4j Driver 由 runtime-access/Neo4jConfig 统一管理 (收敛铁律 2.5)。
    @Autowired(required = false)
    private Neo4jClient neo4jClient;

    /** workspace 命名异步执行器（F07-17 底座收口，构造器注入避免新增字段注入违规）。 */
    private final TaskExecutor taskExecutor;

    public ObjectKgSyncService(@Qualifier("workspaceTaskExecutor") TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @PostConstruct
    void init() {
        if (neo4jClient == null || !neo4jClient.isAvailable()) {
            log.warn("ObjectKgSyncService init: Neo4j 不可用, Object→KG 同步禁用 (no-op)");
            return;
        }
        log.info("ObjectKgSyncService init: 使用 runtime-access 统一 Neo4jClient");
    }

    @PreDestroy
    void close() {
        // Driver 是 runtime-access 管理的 Bean, 不在此 close
        log.info("ObjectKgSyncService close: Neo4j 客户端由 runtime-access 管理, 不在此处 close");
    }

    /**
     * 对象属性 → Neo4j 可存储值（D92，2026-11-14 验证批）。
     *
     * <p><b>缺陷形态</b>：PG 行里 {@code created_at} 是 {@link java.sql.Timestamp}，
     * Neo4j 驱动不认识该类型，整条写入抛
     * {@code Unable to convert java.sql.Timestamp to Neo4j Value}。
     * 该消息被本类 catch 吞成 WARN，于是<b>每一条对象</b>的图谱同步都失败，
     * 知识图谱里始终没有节点，而主流程看起来完全正常。</p>
     *
     * <p>修法：只做「驱动不识类型 → 字符串/数字」的必要降型，其余原样透传；
     * 时间统一转 ISO-8601 字符串（图谱侧按字符串存储与比较，语义无损）。</p>
     */
    private static Map<String, Object> toNeo4jProps(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) return Map.of();
        Map<String, Object> out = new java.util.LinkedHashMap<>(properties.size());
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            out.put(e.getKey(), toNeo4jValue(e.getValue()));
        }
        return out;
    }

    private static Object toNeo4jValue(Object v) {
        if (v == null) return null;
        if (v instanceof java.sql.Timestamp ts) return ts.toInstant().toString();
        if (v instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (v instanceof java.sql.Time t) return t.toLocalTime().toString();
        if (v instanceof java.time.temporal.TemporalAccessor t) return t.toString();
        if (v instanceof java.util.Date d) return d.toInstant().toString();
        if (v instanceof java.util.UUID u) return u.toString();
        if (v instanceof Map<?, ?> m) return m;
        if (v instanceof java.util.Collection<?> c) return c;
        if (v instanceof Number || v instanceof Boolean || v instanceof String) return v;
        // 驱动不认识的残余类型一律降为字符串，避免整条写入失败（宁可丢类型，不可丢节点）
        return v.toString();
    }

    /**
     * 异步同步单个 Object 实例到 Neo4j。
     *
     * @param entityCode 实体代码（对应 Neo4j Label）
     * @param objectId   对象 ID
     * @param properties 对象属性 Map（DELETE 时可为 null）
     * @param operation  操作类型: CREATE / UPDATE / DELETE
     */
    public void syncObjectToNeo4j(String entityCode, String objectId,
                                   Map<String, Object> properties, String operation) {
        if (neo4jClient == null || !neo4jClient.isAvailable()) {
            // M0 改造 (2026-09): Neo4j 不可用 (standard 档), no-op
            log.debug("syncObjectToNeo4j: Neo4j 不可用, skip (entityCode={}, op={})", entityCode, operation);
            return;
        }
        // 安全校验：Label 名仅允许字母、数字、下划线
        if (entityCode == null || !entityCode.matches("[A-Za-z0-9_]+")) {
            log.debug("Skipping KG sync for unsafe entityCode: {}", entityCode);
            return;
        }

        taskExecutor.execute(() -> {
            try {
                Map<String, Object> props = toNeo4jProps(properties);
                switch (operation) {
                    case "CREATE" -> {
                        // 参数化写入：防止 Cypher 注入
                        // Label 名已通过正则安全校验
                        neo4jClient.write(
                            "MERGE (n:`" + entityCode + "` {id: $id}) SET n += $props",
                            Map.of("id", objectId, "props", props)
                        );
                        log.debug("KG sync CREATE: {} id={}", entityCode, objectId);
                    }
                    case "UPDATE" -> {
                        neo4jClient.write(
                            "MATCH (n:`" + entityCode + "` {id: $id}) SET n += $props",
                            Map.of("id", objectId, "props", props)
                        );
                        log.debug("KG sync UPDATE: {} id={}", entityCode, objectId);
                    }
                    case "DELETE" -> {
                        neo4jClient.write(
                            "MATCH (n:`" + entityCode + "` {id: $id}) DETACH DELETE n",
                            Map.of("id", objectId)
                        );
                        log.debug("KG sync DELETE: {} id={}", entityCode, objectId);
                    }
                    default -> log.warn("Unknown KG sync operation: {}", operation);
                }
            } catch (Exception e) {
                // ★ 异常只 log，不抛 — 不能影响主业务流程（命名池内自吞，等价原 exceptionally 留痕）
                log.warn("KG sync async {} failed for {} id={}: {}",
                        operation, entityCode, objectId, e.getMessage());
            }
        });
    }

    /**
     * 同步两个对象之间的关系到 Neo4j。
     *
     * @param sourceEntityCode 源实体代码
     * @param sourceId         源对象 ID
     * @param relationType     关系类型（Relationship Type）
     * @param targetEntityCode 目标实体代码
     * @param targetId         目标对象 ID
     */
    public void syncRelationToNeo4j(String sourceEntityCode, String sourceId,
                                     String relationType, String targetEntityCode, String targetId) {
        if (neo4jClient == null || !neo4jClient.isAvailable()) {
            log.debug("syncRelationToNeo4j: Neo4j 不可用, skip");
            return;
        }
        // 安全校验
        if (sourceEntityCode == null || !sourceEntityCode.matches("[A-Za-z0-9_]+")
                || targetEntityCode == null || !targetEntityCode.matches("[A-Za-z0-9_]+")
                || relationType == null || !relationType.matches("[A-Za-z0-9_]+")) {
            log.debug("Skipping KG relation sync for unsafe codes: {}-[:{}]->{}",
                    sourceEntityCode, relationType, targetEntityCode);
            return;
        }

        taskExecutor.execute(() -> {
            try {
                // 使用参数化查询保护 id
                // Label 和 Relationship Type 已通过正则安全校验
                neo4jClient.write(
                    "MERGE (a:`" + sourceEntityCode + "` {id: $sourceId}) " +
                    "MERGE (b:`" + targetEntityCode + "` {id: $targetId}) " +
                    "MERGE (a)-[:`" + relationType + "`]->(b)",
                    Map.of("sourceId", sourceId, "targetId", targetId)
                );
                log.debug("KG sync RELATION: ({}:{})-[:{}]->({}:{})",
                        sourceEntityCode, sourceId, relationType, targetEntityCode, targetId);
            } catch (Exception e) {
                // ★ 异常只 log，不抛 — 不能影响主业务流程（命名池内自吞，等价原 exceptionally 留痕）
                log.warn("KG sync async RELATION failed for ({}:{})-[:{}]->({}:{}): {}",
                        sourceEntityCode, sourceId, relationType, targetEntityCode, targetId, e.getMessage());
            }
        });
    }
}
