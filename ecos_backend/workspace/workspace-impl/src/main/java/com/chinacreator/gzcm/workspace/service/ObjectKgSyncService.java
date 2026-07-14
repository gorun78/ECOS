package com.chinacreator.gzcm.workspace.service;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A2 — Object 实例 → KG 节点同步引擎。
 *
 * <p>在 ObjectController 的 CRUD 操作中异步将对象实例同步到 Neo4j 知识图谱。
 * 所有 Neo4j 写入均为异步执行，异常仅记录日志，不抛给主业务流程。</p>
 */
@Service
public class ObjectKgSyncService {

    private static final Logger log = LoggerFactory.getLogger(ObjectKgSyncService.class);

    @Value("${neo4j.uri:bolt://localhost:7687}")
    private String neo4jUri;

    @Value("${neo4j.username:neo4j}")
    private String neo4jUser;

    @Value("${neo4j.password:neo4j123}")
    private String neo4jPass;

    private Driver driver;

    @PostConstruct
    void init() {
        driver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUser, neo4jPass),
                Config.builder().withConnectionTimeout(5, TimeUnit.SECONDS).build());
        log.info("ObjectKgSyncService Neo4j driver initialized: {}", neo4jUri);
    }

    @PreDestroy
    void close() {
        if (driver != null) driver.close();
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
        // 安全校验：Label 名仅允许字母、数字、下划线
        if (entityCode == null || !entityCode.matches("[A-Za-z0-9_]+")) {
            log.debug("Skipping KG sync for unsafe entityCode: {}", entityCode);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try (Session session = driver.session()) {
                switch (operation) {
                    case "CREATE" -> {
                        // 参数化写入：防止 Cypher 注入
                        // Label 名已通过正则安全校验
                        session.run(
                            "MERGE (n:`" + entityCode + "` {id: $id}) SET n += $props",
                            Map.of("id", objectId, "props", properties != null ? properties : Map.of())
                        );
                        log.debug("KG sync CREATE: {} id={}", entityCode, objectId);
                    }
                    case "UPDATE" -> {
                        session.run(
                            "MATCH (n:`" + entityCode + "` {id: $id}) SET n += $props",
                            Map.of("id", objectId, "props", properties != null ? properties : Map.of())
                        );
                        log.debug("KG sync UPDATE: {} id={}", entityCode, objectId);
                    }
                    case "DELETE" -> {
                        session.run(
                            "MATCH (n:`" + entityCode + "` {id: $id}) DETACH DELETE n",
                            Map.of("id", objectId)
                        );
                        log.debug("KG sync DELETE: {} id={}", entityCode, objectId);
                    }
                    default -> log.warn("Unknown KG sync operation: {}", operation);
                }
            } catch (Exception e) {
                // ★ 异常只 log，不抛 — 不能影响主业务流程
                log.warn("KG sync {} failed for {} id={}: {}",
                        operation, entityCode, objectId, e.getMessage());
            }
        }).exceptionally(ex -> {
            log.warn("KG sync async {} failed for {} id={}: {}",
                    operation, entityCode, objectId, ex.getMessage());
            return null;
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
        // 安全校验
        if (sourceEntityCode == null || !sourceEntityCode.matches("[A-Za-z0-9_]+")
                || targetEntityCode == null || !targetEntityCode.matches("[A-Za-z0-9_]+")
                || relationType == null || !relationType.matches("[A-Za-z0-9_]+")) {
            log.debug("Skipping KG relation sync for unsafe codes: {}-[:{}]->{}",
                    sourceEntityCode, relationType, targetEntityCode);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try (Session session = driver.session()) {
                // 使用参数化查询保护 id
                // Label 和 Relationship Type 已通过正则安全校验
                session.run(
                    "MERGE (a:`" + sourceEntityCode + "` {id: $sourceId}) " +
                    "MERGE (b:`" + targetEntityCode + "` {id: $targetId}) " +
                    "MERGE (a)-[:`" + relationType + "`]->(b)",
                    Map.of("sourceId", sourceId, "targetId", targetId)
                );
                log.debug("KG sync RELATION: ({}:{})-[:{}]->({}:{})",
                        sourceEntityCode, sourceId, relationType, targetEntityCode, targetId);
            } catch (Exception e) {
                log.warn("KG sync RELATION failed for ({}:{})-[:{}]->({}:{}): {}",
                        sourceEntityCode, sourceId, relationType, targetEntityCode, targetId, e.getMessage());
            }
        }).exceptionally(ex -> {
            log.warn("KG sync RELATION async failed: {}", ex.getMessage());
            return null;
        });
    }
}
