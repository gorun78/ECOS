package com.chinacreator.gzcm.runtime.access.graph;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4j 统一图客户端 (runtime-access, 铁律 §3.2 / PMO-74.3 T3)。
 * <p>
 * 引擎侧唯一的 Neo4j 出入口: Driver 生命周期由本模块 Neo4jConfig 持有,
 * 各引擎只做 Cypher 编排, 仅消费本类的 plain-Java 签名 (String/Map/List),
 * 不再 import org.neo4j.driver。节点/关系/路径结果统一扁平化为
 * {elementId, labels, properties} / {elementId, type, startElementId, endElementId, properties}
 * / {nodes, relationships, length} 结构。
 * </p>
 */
@Component
public class Neo4jClient {

    private static final Logger log = LoggerFactory.getLogger(Neo4jClient.class);

    private final Driver driver;
    private final String database;

    public Neo4jClient(ObjectProvider<Driver> driverProvider,
                       @org.springframework.beans.factory.annotation.Value("${neo4j.database:neo4j}") String database) {
        this.driver = driverProvider.getIfAvailable();
        this.database = database;
        if (this.driver == null) {
            log.info("Neo4jClient: Driver 不可用 (standard 档或 neo4j.uri 未配置), 图操作降级为 no-op/empty");
        }
    }

    public boolean isAvailable() {
        return driver != null;
    }

    public String getDatabase() {
        return database;
    }

    /** 驱动级连通性校验 (runtime-access 内部封装, 引擎不感知 Driver)。 */
    public boolean verifyConnectivity() {
        if (driver == null) return false;
        try {
            driver.verifyConnectivity();
            return true;
        } catch (Exception e) {
            log.warn("Neo4j connectivity verification failed: {}", e.getMessage());
            return false;
        }
    }

    /** 轻量探活: RETURN 1。 */
    public boolean testConnection() {
        if (driver == null) return false;
        try (Session session = openSession()) {
            session.run("RETURN 1").consume();
            return true;
        } catch (Exception e) {
            log.warn("Neo4j test connection failed: {}", e.getMessage());
            return false;
        }
    }

    /** 自动提交读查询, 返回扁平化记录列表; Driver 不可用时返回空列表。 */
    public List<Map<String, Object>> run(String cypher, Map<String, Object> params) {
        if (driver == null) return Collections.emptyList();
        try (Session session = openSession()) {
            List<Map<String, Object>> list = new ArrayList<>();
            var result = session.run(cypher, params == null ? Map.of() : params);
            while (result.hasNext()) {
                list.add(toPlainRecord(result.next()));
            }
            return list;
        }
    }

    /** 取首条记录; 无结果或 Driver 不可用时返回 null。 */
    public Map<String, Object> runFirst(String cypher, Map<String, Object> params) {
        List<Map<String, Object>> rows = run(cypher, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 自动提交写操作; Driver 不可用时 no-op。 */
    public void write(String cypher, Map<String, Object> params) {
        if (driver == null) return;
        try (Session session = openSession()) {
            session.run(cypher, params == null ? Map.of() : params).consume();
        }
    }

    /** 多条语句在同一个写事务内执行 (cyphers 与 paramsList 一一对应)。 */
    public void writeBatch(List<String> cyphers, List<Map<String, Object>> paramsList) {
        if (driver == null) return;
        try (Session session = openSession()) {
            session.writeTransaction(tx -> {
                for (int i = 0; i < cyphers.size(); i++) {
                    Map<String, Object> params = (paramsList != null && i < paramsList.size()
                            && paramsList.get(i) != null) ? paramsList.get(i) : Map.of();
                    tx.run(cyphers.get(i), params).consume();
                }
                return null;
            });
        }
    }

    private Session openSession() {
        return driver.session(SessionConfig.forDatabase(database));
    }

    // ── Value → plain Java 扁平化 ──

    private static Map<String, Object> toPlainRecord(Record record) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : record.keys()) {
            map.put(key, toPlain(record.get(key)));
        }
        return map;
    }

    private static Object toPlain(Value value) {
        if (value == null || value.isNull()) return null;
        String type = value.type().name();
        switch (type) {
            case "NODE":
                return nodeToMap(value.asNode());
            case "RELATIONSHIP":
                return relToMap(value.asRelationship());
            case "PATH":
                return pathToMap(value.asPath());
            case "LIST":
                return value.asList(Neo4jClient::toPlain);
            case "MAP":
                return value.asMap(Neo4jClient::toPlain);
            default:
                return value.asObject();
        }
    }

    private static Map<String, Object> nodeToMap(Node node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("elementId", node.elementId());
        map.put("labels", iterateToList(node.labels()));
        map.put("properties", keysToProperties(node));
        return map;
    }

    private static Map<String, Object> relToMap(Relationship rel) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("elementId", rel.elementId());
        map.put("type", rel.type());
        map.put("startElementId", rel.startNodeElementId());
        map.put("endElementId", rel.endNodeElementId());
        Map<String, Object> props = new LinkedHashMap<>();
        for (String key : rel.keys()) {
            props.put(key, toPlain(rel.get(key)));
        }
        map.put("properties", props);
        return map;
    }

    private static Map<String, Object> keysToProperties(Node node) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (String key : node.keys()) {
            props.put(key, toPlain(node.get(key)));
        }
        return props;
    }

    private static Map<String, Object> pathToMap(Path path) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> rels = new ArrayList<>();
        Iterator<Path.Segment> it = path.iterator();
        while (it.hasNext()) {
            Path.Segment segment = it.next();
            nodes.add(nodeToMap(segment.start()));
            rels.add(relToMap(segment.relationship()));
            if (!it.hasNext()) {
                nodes.add(nodeToMap(segment.end()));
            }
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("nodes", nodes);
        map.put("relationships", rels);
        map.put("length", path.length());
        return map;
    }

    /** driver 的 labels()/nodes() 等返回 {@code Iterable}，非 {@code Collection}，需显式收集。 */
    private static <T> List<T> iterateToList(Iterable<T> source) {
        List<T> list = new ArrayList<>();
        source.forEach(list::add);
        return list;
    }
}
