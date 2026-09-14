package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.client.KbEngineGraphSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A1 — Ontology → KG 同步引擎（走 kb-engine REST，避免直连 Neo4j）。
 *
 * <p>从 ecos_ontology_entity / ecos_ontology_relationship 读取 Ontology 定义，
 * 把 entities/relationships 摘要通过 {@link KbEngineGraphSyncClient} 发给
 * kb-engine (POST /api/v1/kb/graph/sync), 由 kb-engine 内部走 runtime-access
 * 统一 Driver 落地 Neo4j (enterprise/flagship 档)。standard 档不需要 KG, @Profile
 * 限定。</p>
 *
 * <p>Wave B-2 · T14 (来源: 肖国荣 / 日期: 2026-09-12 / 责任人: fullstack-implementer)
 * 改造: 原版本 import org.neo4j.driver.* + Driver.session() 直接写 Cypher
 * (违反架构铁律 §2.5: 基础设施 Driver 收敛 runtime-access, 引擎不 new Driver)。
 * T14 改为 delegate 到 KbEngineGraphSyncClient，本类不再持有 Driver 字段。</p>
 *
 * <p>方法契约变更: {@code syncOntologyToNeo4j()} 原返回
 * {@code {syncedEntities, syncedRelationships}} 改为 {@code boolean}
 * (true = kb-engine 受理成功, false = 不可达/失败), 以 boolean 表达 "是否受理"
 * 这个核心语义, synced 计数交给 kb-engine 日志。</p>
 *
 * <p>只在 enterprise / flagship profile 下激活 (@Profile 守卫),
 * standard 档下本 bean 不加载。</p>
 */
@Service
@Profile({"enterprise", "flagship"})
public class OntologyKgSyncService {

    private static final Logger log = LoggerFactory.getLogger(OntologyKgSyncService.class);

    private final JdbcTemplate jdbc;
    private final KbEngineGraphSyncClient kgClient;

    public OntologyKgSyncService(JdbcTemplate jdbc, KbEngineGraphSyncClient kgClient) {
        this.jdbc = jdbc;
        this.kgClient = kgClient;
    }

    /**
     * 将 Ontology 定义同步到知识库图谱 (经 kb-engine REST)。
     *
     * <ul>
     *   <li>从 ecos_ontology_entity 读取实体 code 摘要</li>
     *   <li>从 ecos_ontology_relationship 读取关系 id 摘要</li>
     *   <li>POST /api/v1/kb/graph/sync 把摘要发过去, 由 kb-engine 内部走
     *       runtime-access 统一 Driver 落 Neo4j</li>
     * </ul>
     *
     * <p>只返回 kb-engine 是否受理 (boolean), 不报 specifics:</p>
     * <ul>
     *   <li>true: kb-engine 返回 success=true, 同步已受理 (落图是 kb 内部事务)</li>
     *   <li>false: kb-engine 不可达 / 端点 404 / 业务失败, 同步未受理
     *       (TODO PMO-06 T21 补端点合同前, standard/enterprise 都返回 false)</li>
     * </ul>
     *
     * @return true = kb-engine 受理成功, false = 不可达/失败
     */
    public boolean syncOntologyToKnowledgeGraph() {
        // ── 1. 读实体 code 摘要 (本体自有表, JdbcTemplate 合理) ──
        List<Map<String, Object>> entities = jdbc.queryForList(
            "SELECT code FROM ecos_ontology_entity");

        List<Map<String, Object>> entitySummaries = new java.util.ArrayList<>();
        for (Map<String, Object> e : entities) {
            String code = (String) e.get("code");
            if (code == null || code.isEmpty()) continue;
            // Label 名仅允许字母数字下划线, 防御脏数据 (kb 侧也会再校验)
            if (!code.matches("[A-Za-z0-9_]+")) {
                log.warn("Skipping entity with unsafe code for KG sync: {}", code);
                continue;
            }
            entitySummaries.add(Map.of("code", code));
        }

        // ── 2. 读关系 id 摘要 ──
        List<Map<String, Object>> relRows = jdbc.queryForList(
            "SELECT r.id FROM ecos_ontology_relationship r");
        List<Map<String, Object>> relationshipSummaries = new java.util.ArrayList<>();
        for (Map<String, Object> r : relRows) {
            Object id = r.get("id");
            if (id == null) continue;
            relationshipSummaries.add(Map.of("id", String.valueOf(id)));
        }

        // ── 3. POST 到 kb-engine 走统一 runtime-access Driver 落 Neo4j ──
        boolean accepted = kgClient.syncOntologyGraph("default", entitySummaries, relationshipSummaries);
        if (!accepted) {
            log.warn("KG sync not accepted by kb-engine ({}), Ontology→KG 同步未完成",
                    entitySummaries.size());
        } else {
            log.info("Ontology sync accepted by kb-engine: {} entities, {} relationships",
                    entitySummaries.size(), relationshipSummaries.size());
        }
        return accepted;
    }

    /**
     * 兼容旧方法名 (保留签名), 直接 delegate 到新契约方法。
     *
     * <p>原方法返回 Map, 现语义改为 boolean — 旧调用方需适配。
     * 保留此重载避免编译失败 (其它 service 仍可能按旧契约调用)。</p>
     *
     * @return 兼容返回 Map，包含 accepted (boolean) 与 disabled/已废弃字段
     */
    @Deprecated
    public Map<String, Object> syncOntologyToNeo4j() {
        boolean ok = syncOntologyToKnowledgeGraph();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", ok);
        result.put("syncedEntities", ok ? -1 : 0);
        result.put("syncedRelationships", ok ? -1 : -1);
        result.put("disabled", !ok);
        if (!ok) {
            result.put("reason", "kb-engine graph/sync 未受理 (端点未落地/不可达)");
        }
        return result;
    }
}
