package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.DataAccessException;
import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 图谱实例抽取服务 — PMO 批次 B3-2（缺陷 D2 核心落地 / 方案 §2.1 实例层 + §6.1 K1 映射驱动抽取）。
 *
 * <p><b>职责</b>：按本体工作台产出的映射契约 {@code ecos_entity_table_mapping}，
 * 经 REST 只读消费数据工作台的 DW 层（{@code layer=CURATED}）实例行，写入知识图谱实例
 * （{@code graph_node} / {@code graph_edge}），与 B1 已有骨架/版本对齐解耦。
 *
 * <p><b>边界铁律遵循</b>：
 * <ol>
 *   <li>单一事实源（铁律 §0.5-1）：DW 层表只读，图谱**不回写** DW/本体表；</li>
 *   <li>契约先行（铁律 §0.5-2）：不自行推断「表 ↔ 实体」，映射契约全部经
 *       {@code GET /api/v1/ontology/entity-mappings} 获取，映射数据**不落 kb 表**（仅内存装配）；</li>
 *   <li>引擎间只调 API（铁律 §2.1）：DW 实例行走 {@code GET /api/v1/engine/data/layers/CURATED/...}，
 *       本体 schema/关系走 {@code /api/v1/ecos/...}，**禁止直查 {@code td_data_*} 或本体表**。</li>
 * </ol>
 *
 * <p><b>校验遵循方案 §2.3</b>：
 * <ul>
 *   <li>C2 关系合法性：实例边 {@code (source_type, relation, target_type)} 必须 ∈ 本体关系定义，
 *       不合法则跳过该边并记录告警；</li>
 *   <li>C4 映射有效性：映射指向的 DW 表/列无效 → 拒绝该实体实例化并记录 {@code INVALID_MAPPING}（不静默吞）；</li>
 *   <li>Q2 实例化范围：仅处理 {@code materialized == true} 且存在有效映射的实体。</li>
 * </ul>
 *
 * <p><b>性能（方案风险 R3）</b>：DW 实例行强制分页（{@code rows?watermark=&limit=} 循环，页大小 500，
 * 页数上限 200）+ 水位线增量；列定义按 resourceId 缓存，避免循环查库。
 */
@Service
public class KbEntityInstanceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(KbEntityInstanceExtractionService.class);

    /** DW 层（数据工作台唯一写入层，kb 只读）。 */
    private static final String DW_LAYER = "CURATED";

    /** C4 失败拒绝码（方案 §2.3 C4）。 */
    private static final String REJECT_CODE_INVALID_MAPPING = "INVALID_MAPPING";

    /** 抽取模式：全量（忽略水位线，重读全部实例行）。 */
    private static final String MODE_FULL = "FULL";

    /** 抽取模式：增量（按持久化水位线续读）。 */
    private static final String MODE_INCREMENTAL = "INCREMENTAL";

    /** 单页行数（方案 R3：强制限量防慢 SQL）。 */
    private static final int PAGE_LIMIT = 500;

    /** 单资源最大页数（防无界循环，500 × 200 = 10 万行上限）。 */
    private static final int MAX_PAGES = 200;

    /** graph_node.id / node_type / label 列宽上限（V134 注释：VARCHAR(64)）。 */
    private static final int SHORT_COLUMN_MAX = 64;

    /** 节点/边 id 哈希段长度（超长 id 走 SHA-256 前 N 位，保证确定性且幂等）。 */
    private static final int ID_HASH_LEN = 40;

    /** 端点节点存在性批量查询的分片大小（单条 IN 列表上限保护）。 */
    private static final int EXISTENCE_QUERY_CHUNK = 500;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;

    private final RestTemplate restTemplate;

    /** data-engine REST 基址（直连 :18082，既有姿态；不新增 Security 绕过）。 */
    private final String datanetBaseUrl;

    /** ontology-engine REST 基址（直连 :18083，既有姿态）。 */
    private final String ontologyApiBase;

    /** 列定义缓存（resourceId → 字段列表），避免循环查库。 */
    private final Map<String, List<Map<String, Object>>> fieldsCache = new LinkedHashMap<>();

    public KbEntityInstanceExtractionService(
            JdbcTemplate jdbc,
            RestTemplate restTemplate,
            @Value("${ecos.datanet.base-url:http://localhost:18082}") String datanetBaseUrl,
            @Value("${ecos.ontology-api-base:http://localhost:18083/api/v1}") String ontologyApiBase) {
        this.jdbc = jdbc;
        this.restTemplate = restTemplate;
        this.datanetBaseUrl = datanetBaseUrl;
        this.ontologyApiBase = ontologyApiBase;
    }

    /**
     * 执行一次图谱实例抽取（非 dry-run，写入图谱）。
     *
     * @param ontologyId  本体业务 ID；{@code ALL} / 空表示按 {@code kb_ontology_snapshot} 全量本体
     * @param jobId       任务标识（写入审计与 {@code kg_sync_log} 供溯源）
     * @param incremental {@code true} = 按持久化水位线增量；{@code false} = 全量重读
     * @return 结构化抽取报告
     */
    public EntityInstanceExtractionReportVO extract(String ontologyId, String jobId, boolean incremental) {
        return extract(ontologyId, jobId, incremental, false);
    }

    /**
     * 执行一次图谱实例抽取（含 dry-run 预览）。
     *
     * @param ontologyId  本体业务 ID；{@code ALL} / 空表示按 {@code kb_ontology_snapshot} 全量本体
     * @param jobId       任务标识（写入审计与 {@code kg_sync_log} 供溯源）
     * @param incremental {@code true} = 按持久化水位线增量；{@code false} = 全量重读
     * @param dryRun      {@code true} = 只统计不落库（预览）；{@code false} = 正常抽取写入
     * @return 结构化抽取报告
     */
    public EntityInstanceExtractionReportVO extract(String ontologyId, String jobId,
                                                    boolean incremental, boolean dryRun) {
        long startedAt = System.currentTimeMillis();
        EntityInstanceExtractionReportVO report = new EntityInstanceExtractionReportVO();
        report.setMode(incremental ? MODE_INCREMENTAL : MODE_FULL);
        report.setDryRun(dryRun);
        report.setOntologyId((ontologyId == null || ontologyId.isBlank()) ? "ALL" : ontologyId.trim());

        // 0. 抽取范围：kb 自有快照表提供 ontologyId + version（版本对齐基准，D9）
        List<OntologySnapshot> snapshots = readSnapshots(ontologyId);
        if (snapshots.isEmpty()) {
            log.warn("实例抽取跳过：无可用本体快照 scope={}", ontologyId);
            report.addIssue(null, "NO_SNAPSHOT", "无可用本体快照，未抽取任何实例");
            report.setDurationMs(System.currentTimeMillis() - startedAt);
            return report;
        }
        report.setOntologyCount(snapshots.size());

        // 1. DW 层 CURATED 资源索引（一次拉取，按 resource_id / resource_name 双索引）
        ResourceIndex resourceIndex = loadCuratedResources(report);

        String lastWatermark = null;
        // 实例边候选池：节点全部落库后统一 flush，规避 graph_edge → graph_node 外键悬空
        List<EdgeCandidate> pendingEdges = new ArrayList<>();
        for (OntologySnapshot snap : snapshots) {
            report.addOntology(snap.ontologyId() + "@" + snap.version());
            // 2. 映射契约（契约先行；materialized=false 显式关闭实例化）
            List<Map<String, Object>> mappings = loadEntityMappings(snap.ontologyId(), report);
            if (mappings.isEmpty()) {
                continue;
            }
            // 3. 实体 ref(id/code) → 规范 code 别名表 + 关系定义（C2 判据）
            Map<String, String> refToCode = loadEntityAliases(snap.ontologyId(), report);
            List<RelationDef> relations = loadRelationDefs(snap.ontologyId(), refToCode, report);

            for (Map<String, Object> mapping : mappings) {
                String watermark = extractForMapping(snap, mapping, resourceIndex, refToCode, relations,
                        incremental, dryRun, pendingEdges, report);
                if (watermark != null && !watermark.isBlank()) {
                    lastWatermark = watermark;
                }
            }
        }
        // 4. 实例边统一落库（C2 收口 + 端点存在性校验）
        flushEdges(pendingEdges, report, dryRun);
        report.setNextWatermark(lastWatermark);
        report.setDurationMs(System.currentTimeMillis() - startedAt);
        log.info("实例抽取完成: mode={} dryRun={} scope={} jobId={} ontologies={} entities={} "
                        + "nodesCreated={} nodesUpdated={} edges={} skipped={} invalid={} costMs={}",
                report.getMode(), dryRun, ontologyId, jobId, report.getOntologyCount(), report.getEntityCount(),
                report.getNodeCreated(), report.getNodeUpdated(), report.getEdgeCreated(),
                report.getNodeSkipped(), report.getInvalidMappings(), report.getDurationMs());
        emitAudit("kg_instance_extract", "jobId=" + jobId + " mode=" + report.getMode() + " dryRun=" + dryRun
                + " nodesCreated=" + report.getNodeCreated() + " nodesUpdated=" + report.getNodeUpdated()
                + " edges=" + report.getEdgeCreated() + " skipped=" + report.getNodeSkipped()
                + " invalidMappings=" + report.getInvalidMappings());
        return report;
    }

    // ═══════════════ 单实体抽取 ═══════════════

    /**
     * 抽取单个映射实体的 DW 实例行 → 图谱节点/边。
     *
     * @return 本次抽取收敛后的最后水位（无数据时为 null）
     */
    private String extractForMapping(OntologySnapshot snap,
                                     Map<String, Object> mapping,
                                     ResourceIndex resourceIndex,
                                     Map<String, String> refToCode,
                                     List<RelationDef> relations,
                                     boolean incremental,
                                     boolean dryRun,
                                     List<EdgeCandidate> pendingEdges,
                                     EntityInstanceExtractionReportVO report) {
        String entityCode = text(mapping.get("entityCode"));
        if (entityCode.isEmpty()) {
            report.setNodeSkipped(report.getNodeSkipped() + 1);
            return null;
        }
        // C1 类型存在性：entityCode 必须 ∈ 本体快照 entity_codes 集合（快照集合为空时无判据，放行）
        if (!snap.entityCodes().isEmpty()
                && !snap.entityCodes().contains(entityCode.toUpperCase(Locale.ROOT))) {
            report.setNodeSkipped(report.getNodeSkipped() + 1);
            report.addIssue(entityCode, "UNKNOWN_ENTITY_TYPE",
                    "实体 code 不在本体快照 entity_codes 集合内，拒绝实例化");
            log.warn("C1 拒绝实例化: entity={} ontology={} version={}",
                    entityCode, snap.ontologyId(), snap.version());
            return null;
        }
        // Q2 裁决：materialized=false → 显式关闭实例化
        if (!isMaterialized(mapping.get("materialized"))) {
            report.setNodeSkipped(report.getNodeSkipped() + 1);
            report.addIssue(entityCode, "Q2_SKIP", "materialized=false，跳过实例化");
            return null;
        }
        report.setEntityCount(report.getEntityCount() + 1);

        // C4 ①：定位 DW 资源（优先 datasetId，回退表名）
        String datasetId = text(mapping.get("datasetId"));
        String resourceName = text(mapping.get("resourceName"));
        Map<String, Object> resource = resourceIndex.resolve(datasetId, resourceName);
        if (resource == null) {
            rejectInvalidMapping(entityCode, "DW 表不在 CURATED 层: datasetId=" + datasetId + " table=" + resourceName, report);
            return null;
        }
        String resourceId = text(resource.get("resource_id"));
        if (resourceId.isEmpty()) {
            rejectInvalidMapping(entityCode, "DW 资源缺少 resource_id", report);
            return null;
        }

        // C4 ②：列定义（缓存）
        List<Map<String, Object>> fields = loadMetadataFields(resourceId, report);
        if (fields.isEmpty()) {
            rejectInvalidMapping(entityCode, "DW 资源 " + resourceId + " 无字段元数据（未采集）", report);
            return null;
        }
        Set<String> columnNames = new LinkedHashSet<>();
        String pkColumn = null;
        for (Map<String, Object> field : fields) {
            String fieldName = text(field.get("fieldName"));
            if (fieldName.isEmpty()) {
                continue;
            }
            columnNames.add(fieldName.toLowerCase(Locale.ROOT));
            if (pkColumn == null && isPrimaryKey(field.get("primaryKey"))) {
                pkColumn = fieldName;
            }
        }

        // C4 ③：字段映射解析（哪一侧是物理列按 DW 列定义判定，兼容历史书写方向不固定）
        List<FieldRef> fieldRefs = resolveFieldRefs(mapping.get("fieldMappings"), columnNames);
        if (fieldRefs.isEmpty()) {
            rejectInvalidMapping(entityCode, "映射字段无一命中 DW 表 " + resourceName + " 的物理列", report);
            return null;
        }
        if (pkColumn == null) {
            pkColumn = fieldRefs.get(0).dwColumn();
        }

        // 规范 node_type = 实体 code（ref 可能是 id 或 code，统一到 code）
        String nodeType = truncate(refToCode.getOrDefault(entityCode, entityCode), SHORT_COLUMN_MAX);
        String domain = truncate(text(mapping.get("sourceType")), SHORT_COLUMN_MAX);
        String description = text(mapping.get("sourceName"));

        // 4. 分页拉取 DW 实例行（水位线增量）
        String watermark = incremental ? readWatermark(snap.ontologyId(), entityCode, resourceId) : null;
        String lastWatermark = watermark;
        for (int page = 0; page < MAX_PAGES; page++) {
            RowsPage rowsPage = readRows(resourceId, watermark, report);
            if (rowsPage == null) {
                break;
            }
            for (Map<String, Object> row : rowsPage.rows()) {
                String pkValue = text(row.get(pkColumn));
                if (pkValue.isEmpty()) {
                    report.setNodeSkipped(report.getNodeSkipped() + 1);
                    continue;
                }
                String nodeId = buildNodeId(nodeType, pkValue);
                if (dryRun) {
                    // dry-run：只统计不落库（预览）
                    report.setNodeCreated(report.getNodeCreated() + 1);
                } else if (upsertNode(nodeId, pkValue, nodeType, description, domain, row, fieldRefs, snap, resourceId)) {
                    report.setNodeCreated(report.getNodeCreated() + 1);
                } else {
                    report.setNodeUpdated(report.getNodeUpdated() + 1);
                }
                // 关系边候选（C2 校验后入候选池，节点全部落库后统一 flush，规避外键悬空）
                collectEdges(nodeId, nodeType, pkValue, row, fieldRefs, relations, snap, resourceId, report, pendingEdges);
            }
            watermark = rowsPage.nextWatermark();
            lastWatermark = watermark;
            if (!rowsPage.hasMore() || watermark == null || watermark.isBlank()) {
                break;
            }
        }
        // 水位线持久化（尽力而为，失败不影响抽取结果；dry-run 不落水位）
        if (!dryRun && lastWatermark != null && !lastWatermark.isBlank()) {
            writeWatermark(snap.ontologyId(), entityCode, resourceId, lastWatermark);
        }
        return lastWatermark;
    }

    /**
     * 按映射收集实例边候选（方案 §2.3 C2：{@code (source_type, relation, target_type)} 必须 ∈ 本体关系定义）。
     *
     * <p>判定：字段映射的「本体侧」引用命中某条关系 code，且该关系的源实体与当前实体一致 →
     * 入候选池；关系 code 存在但源实体不匹配 → 跳过并告警（C2 不合法）。
     *
     * <p>候选池延迟到全部实体节点落库后由 {@link #flushEdges} 统一落库，
     * 以便用一次批量查询校验端点节点存在性（graph_edge → graph_node 有外键约束）。
     */
    private void collectEdges(String sourceNodeId, String sourceType, String pkValue,
                              Map<String, Object> row, List<FieldRef> fieldRefs,
                              List<RelationDef> relations, OntologySnapshot snap,
                              String resourceId, EntityInstanceExtractionReportVO report,
                              List<EdgeCandidate> pendingEdges) {
        for (FieldRef ref : fieldRefs) {
            String relCode = ref.ontologyRef();
            if (relCode.isEmpty()) {
                continue;
            }
            RelationDef matched = null;
            boolean codeExists = false;
            for (RelationDef def : relations) {
                if (!def.code().equalsIgnoreCase(relCode)) {
                    continue;
                }
                codeExists = true;
                if (def.sourceCode().equalsIgnoreCase(sourceType)) {
                    matched = def;
                    break;
                }
            }
            if (matched == null) {
                if (codeExists) {
                    report.setNodeSkipped(report.getNodeSkipped() + 1);
                    report.addIssue(sourceType, "C2_SKIP",
                            "边 (" + sourceType + "," + relCode + ") 源实体不匹配本体关系定义，跳过");
                }
                continue;
            }
            String fkValue = text(row.get(ref.dwColumn()));
            if (fkValue.isEmpty()) {
                report.setNodeSkipped(report.getNodeSkipped() + 1);
                continue;
            }
            String targetNodeId = buildNodeId(matched.targetCode(), fkValue);
            String edgeId = buildEdgeId(snap.ontologyId(), sourceType, relCode, pkValue, fkValue);
            pendingEdges.add(new EdgeCandidate(edgeId, sourceNodeId, targetNodeId, relCode,
                    snap, resourceId, pkValue));
        }
    }

    /**
     * 统一落库实例边候选池。
     *
     * <p>端点节点存在性用一次（分片）批量查询校验：端点缺失（目标实体无映射 / 未实例化）→ 跳过该边并告警，
     * 不强行插入（graph_edge 对 graph_node 有外键约束，悬空边会直接失败）。
     *
     * @param candidates 候选边
     * @param report     抽取报告
     * @param dryRun     {@code true} = 只统计不落库
     */
    private void flushEdges(List<EdgeCandidate> candidates, EntityInstanceExtractionReportVO report, boolean dryRun) {
        if (candidates.isEmpty()) {
            return;
        }
        if (dryRun) {
            report.setEdgeCreated(candidates.size());
            return;
        }
        Set<String> ids = new LinkedHashSet<>();
        for (EdgeCandidate candidate : candidates) {
            ids.add(candidate.sourceNodeId());
            ids.add(candidate.targetNodeId());
        }
        Set<String> existing = queryExistingNodeIds(ids);
        int created = 0;
        for (EdgeCandidate candidate : candidates) {
            if (!existing.contains(candidate.sourceNodeId()) || !existing.contains(candidate.targetNodeId())) {
                report.setNodeSkipped(report.getNodeSkipped() + 1);
                report.addIssue(candidate.snapshot().ontologyId(), "C2_SKIP",
                        "边 (" + candidate.sourceNodeId() + "," + candidate.relation() + ","
                                + candidate.targetNodeId() + ") 端点节点不存在，跳过");
                continue;
            }
            upsertEdge(candidate.edgeId(), candidate.sourceNodeId(), candidate.targetNodeId(),
                    candidate.relation(), candidate.snapshot(), candidate.resourceId(), candidate.sourcePk());
            created++;
        }
        report.setEdgeCreated(created);
    }

    /** 批量查询已存在的节点 id（分片 IN 查询，非循环单查；参数化防注入）。 */
    private Set<String> queryExistingNodeIds(Set<String> ids) {
        Set<String> existing = new LinkedHashSet<>();
        List<String> all = new ArrayList<>(ids);
        for (int i = 0; i < all.size(); i += EXISTENCE_QUERY_CHUNK) {
            List<String> chunk = all.subList(i, Math.min(i + EXISTENCE_QUERY_CHUNK, all.size()));
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            try {
                existing.addAll(jdbc.queryForList(
                        "SELECT id FROM ecos_knowledge.graph_node WHERE id IN (" + placeholders + ")",
                        String.class, chunk.toArray()));
            } catch (org.springframework.dao.DataAccessException e) {
                log.error("批量查询节点存在性失败 chunkSize={}", chunk.size(), e);
                throw new DataAccessException("批量查询节点存在性失败", e);
            }
        }
        return existing;
    }

    // ═══════════════ 图谱写入（仅 kb 自有表；不回写 DW/本体） ═══════════════

    /**
     * 幂等 upsert 实例节点（同 id 覆盖属性与溯源列，支持全量重跑）。
     *
     * @return {@code true} = 新建（INSERT），{@code false} = 更新既有（ON CONFLICT DO UPDATE）
     */
    private boolean upsertNode(String nodeId, String pkValue, String nodeType, String description,
                               String domain, Map<String, Object> row, List<FieldRef> fieldRefs,
                               OntologySnapshot snap, String resourceId) {
        String propertiesJson = buildPropertiesJson(row, fieldRefs);
        try {
            // RETURNING (xmax = 0)：PG 惯用法，区分 INSERT（true）与 ON CONFLICT UPDATE（false）
            List<Boolean> inserted = jdbc.queryForList(
                    "INSERT INTO ecos_knowledge.graph_node "
                            + "(id, label, node_type, description, properties, domain, created_at, updated_at, "
                            + " ontology_id, ontology_version, source_resource_id, source_pk) "
                            + "VALUES (?, ?, ?, ?, ?::jsonb, ?, NOW(), NOW(), ?, ?, ?, ?) "
                            + "ON CONFLICT (id) DO UPDATE SET label = EXCLUDED.label, node_type = EXCLUDED.node_type, "
                            + " description = EXCLUDED.description, properties = EXCLUDED.properties, "
                            + " domain = EXCLUDED.domain, updated_at = NOW(), ontology_id = EXCLUDED.ontology_id, "
                            + " ontology_version = EXCLUDED.ontology_version, "
                            + " source_resource_id = EXCLUDED.source_resource_id, source_pk = EXCLUDED.source_pk "
                            + "RETURNING (xmax = 0) AS inserted",
                    Boolean.class,
                    nodeId, truncate(pkValue, SHORT_COLUMN_MAX), nodeType, description, propertiesJson,
                    domain, snap.ontologyId(), snap.version(), resourceId, truncate(pkValue, 255));
            return !inserted.isEmpty() && Boolean.TRUE.equals(inserted.get(0));
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("实例节点写入失败: nodeId={} entity={} resourceId={}", nodeId, nodeType, resourceId, e);
            throw new DataAccessException("实例节点写入失败: " + nodeId, e);
        }
    }

    /** 幂等 upsert 实例边（同 id 覆盖，支持全量重跑）。 */
    private void upsertEdge(String edgeId, String sourceNodeId, String targetNodeId, String relation,
                            OntologySnapshot snap, String resourceId, String sourcePk) {
        try {
            jdbc.update(
                    "INSERT INTO ecos_knowledge.graph_edge "
                            + "(id, source_id, target_id, type, weight, properties, created_at, "
                            + " ontology_id, ontology_version, source_resource_id, source_pk) "
                            + "VALUES (?, ?, ?, ?, 1.0, '{}'::jsonb, NOW(), ?, ?, ?, ?) "
                            + "ON CONFLICT (id) DO UPDATE SET source_id = EXCLUDED.source_id, "
                            + " target_id = EXCLUDED.target_id, type = EXCLUDED.type, properties = EXCLUDED.properties, "
                            + " ontology_id = EXCLUDED.ontology_id, ontology_version = EXCLUDED.ontology_version, "
                            + " source_resource_id = EXCLUDED.source_resource_id, source_pk = EXCLUDED.source_pk",
                    edgeId, sourceNodeId, targetNodeId, truncate(relation, SHORT_COLUMN_MAX),
                    snap.ontologyId(), snap.version(), resourceId, truncate(sourcePk, 255));
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("实例边写入失败: edgeId={} relation={} resourceId={}", edgeId, relation, resourceId, e);
            throw new DataAccessException("实例边写入失败: " + edgeId, e);
        }
    }

    // ═══════════════ 引擎间 REST 调用 ═══════════════

    /**
     * 读 kb 自有本体快照（版本对齐基准 + C1 类型存在性判据）。
     *
     * <p>scope=ALL 时取每个本体最新一条生效快照；快照的 {@code entity_codes} 作为 C1 校验集合。
     */
    private List<OntologySnapshot> readSnapshots(String ontologyId) {
        String scope = (ontologyId == null || ontologyId.isBlank()) ? "ALL" : ontologyId.trim();
        List<Map<String, Object>> rows;
        try {
            if ("ALL".equalsIgnoreCase(scope)) {
                rows = jdbc.queryForList(
                        "SELECT DISTINCT ON (ontology_id) ontology_id, version, entity_codes "
                                + "FROM ecos_knowledge.kb_ontology_snapshot WHERE is_deleted = 0 "
                                + "ORDER BY ontology_id, created_at DESC");
            } else {
                rows = jdbc.queryForList(
                        "SELECT ontology_id, version, entity_codes FROM ecos_knowledge.kb_ontology_snapshot "
                                + "WHERE is_deleted = 0 AND ontology_id = ? ORDER BY created_at DESC LIMIT 1",
                        scope);
            }
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("读取本体快照失败 scope={}", scope, e);
            throw new DataAccessException("读取本体快照失败: ontologyId=" + scope, e);
        }
        List<OntologySnapshot> snapshots = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            snapshots.add(new OntologySnapshot(text(row.get("ontology_id")), text(row.get("version")),
                    parseEntityCodes(row.get("entity_codes"))));
        }
        return snapshots;
    }

    /**
     * 解析快照 {@code entity_codes}（JSONB 数组 → 大写规范 code 集合），作为 C1 校验集合。
     *
     * @return 实体 code 集合（无法解析或空数组时返回空集合）
     */
    @SuppressWarnings("unchecked")
    private Set<String> parseEntityCodes(Object raw) {
        Set<String> codes = new LinkedHashSet<>();
        if (raw == null) {
            return codes;
        }
        try {
            List<String> list = MAPPER.readValue(String.valueOf(raw), List.class);
            for (String code : list) {
                if (code != null && !code.isBlank()) {
                    codes.add(code.trim().toUpperCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            log.warn("快照 entity_codes 解析失败，C1 校验将跳过: {}", e.getMessage());
        }
        return codes;
    }

    /** 拉 DW 层 CURATED 资源清单并建索引（一次拉取，避免逐实体重复请求）。 */
    @SuppressWarnings("unchecked")
    private ResourceIndex loadCuratedResources(EntityInstanceExtractionReportVO report) {
        String url = datanetBaseUrl + "/api/v1/engine/data/layers/" + DW_LAYER;
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof Map<?, ?> dataMap && dataMap.get("resources") instanceof List<?> list) {
                return ResourceIndex.of((List<Map<String, Object>>) list);
            }
            report.addIssue(null, "METADATA_INVALID", "DW 层资源响应非法 " + url);
            log.warn("DW 层资源响应非法: {}", url);
            return ResourceIndex.empty();
        } catch (Exception e) {
            log.warn("DW 层资源端点不可用 ({}): {}", url, e.getMessage());
            report.addIssue(null, "METADATA_UNAVAILABLE", "DW 层资源端点不可用 " + e.getMessage());
            return ResourceIndex.empty();
        }
    }

    /** 拉本体映射契约（含 materialized）；失败记告警不抛，返回空列表。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadEntityMappings(String ontologyId, EntityInstanceExtractionReportVO report) {
        String url = ontologyApiBase + "/ontology/entity-mappings?ontologyId=" + encode(ontologyId);
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
            report.addIssue(ontologyId, "MAPPING_INVALID", "映射契约响应非法 ontologyId=" + ontologyId);
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("本体映射契约端点不可用 ({}): {}", url, e.getMessage());
            report.addIssue(ontologyId, "MAPPING_UNAVAILABLE", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 拉本体实体清单，构建 ref(id/code) → 规范 code 别名表。失败返回空表（node_type 回退原 ref）。 */
    @SuppressWarnings("unchecked")
    private Map<String, String> loadEntityAliases(String ontologyId, EntityInstanceExtractionReportVO report) {
        Map<String, String> aliases = new LinkedHashMap<>();
        String url = ontologyApiBase + "/ecos/ontologies/" + encode(ontologyId) + "/entities";
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof List<?> list) {
                for (Object element : list) {
                    if (element instanceof Map<?, ?> entity) {
                        String id = text(entity.get("id"));
                        String code = text(entity.get("code"));
                        String canonical = code.isEmpty() ? id : code;
                        if (!id.isEmpty()) {
                            aliases.put(id, canonical);
                        }
                        if (!code.isEmpty()) {
                            aliases.put(code, canonical);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("本体实体端点不可用 ({}): {}", url, e.getMessage());
            report.addIssue(ontologyId, "ENTITY_UNAVAILABLE", e.getMessage());
        }
        return aliases;
    }

    /** 拉本体关系定义（C2 判据），源/目标实体 id 经别名表转规范 code。失败返回空列表。 */
    @SuppressWarnings("unchecked")
    private List<RelationDef> loadRelationDefs(String ontologyId, Map<String, String> refToCode,
                                               EntityInstanceExtractionReportVO report) {
        List<RelationDef> defs = new ArrayList<>();
        String url = ontologyApiBase + "/ecos/ontologies/" + encode(ontologyId) + "/relationships";
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            Object data = body == null ? null : body.get("data");
            if (!(data instanceof List<?> list)) {
                return defs;
            }
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> rel)) {
                    continue;
                }
                String code = text(rel.get("code"));
                String sourceRef = text(rel.get("source_entity_id"));
                String targetRef = text(rel.get("target_entity_id"));
                if (code.isEmpty() || sourceRef.isEmpty() || targetRef.isEmpty()) {
                    continue;
                }
                String sourceCode = refToCode.getOrDefault(sourceRef, sourceRef);
                String targetCode = refToCode.getOrDefault(targetRef, targetRef);
                defs.add(new RelationDef(sourceCode, code, targetCode));
            }
        } catch (Exception e) {
            log.warn("本体关系端点不可用 ({}): {}", url, e.getMessage());
            report.addIssue(ontologyId, "RELATION_UNAVAILABLE", e.getMessage());
        }
        return defs;
    }

    /** DW 实例行增量读取（{@code rows?watermark=&limit=}）。失败返回 null（终止该资源分页）。 */
    @SuppressWarnings("unchecked")
    private RowsPage readRows(String resourceId, String watermark, EntityInstanceExtractionReportVO report) {
        StringBuilder url = new StringBuilder(datanetBaseUrl)
                .append("/api/v1/engine/data/layers/").append(DW_LAYER)
                .append("/resources/").append(encode(resourceId))
                .append("/rows?limit=").append(PAGE_LIMIT);
        if (watermark != null && !watermark.isBlank()) {
            url.append("&watermark=").append(encode(watermark));
        }
        try {
            Map<String, Object> body = restTemplate.getForObject(url.toString(), Map.class);
            Object data = body == null ? null : body.get("data");
            if (!(data instanceof Map<?, ?> dataMap)) {
                report.addIssue(resourceId, "ROWS_INVALID", "实例行响应非法 resourceId=" + resourceId);
                return null;
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            if (dataMap.get("rows") instanceof List<?> list) {
                for (Object element : list) {
                    if (element instanceof Map<?, ?> rowMap) {
                        Map<String, Object> ordered = new LinkedHashMap<>();
                        rowMap.forEach((k, v) -> ordered.put(String.valueOf(k), v));
                        rows.add(ordered);
                    }
                }
            }
            boolean hasMore = Boolean.TRUE.equals(dataMap.get("hasMore"));
            return new RowsPage(rows, text(dataMap.get("nextWatermark")), hasMore);
        } catch (Exception e) {
            log.warn("DW 实例行端点不可用 ({}): {}", url, e.getMessage());
            report.addIssue(resourceId, "ROWS_UNAVAILABLE", e.getMessage());
            return null;
        }
    }

    /** 列定义读取（resourceId 缓存，避免循环查库）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadMetadataFields(String resourceId, EntityInstanceExtractionReportVO report) {
        List<Map<String, Object>> cached = fieldsCache.get(resourceId);
        if (cached != null) {
            return cached;
        }
        String url = datanetBaseUrl + "/api/v1/datanet/metadata/fields/" + encode(resourceId);
        List<Map<String, Object>> fields = Collections.emptyList();
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof List<?> list) {
                fields = (List<Map<String, Object>>) list;
            } else {
                report.addIssue(resourceId, "FIELDS_INVALID", "字段元数据响应非法 resourceId=" + resourceId);
            }
        } catch (Exception e) {
            log.warn("字段元数据端点不可用 ({}): {}", url, e.getMessage());
            report.addIssue(resourceId, "FIELDS_UNAVAILABLE", e.getMessage());
        }
        fieldsCache.put(resourceId, fields);
        return fields;
    }

    // ═══════════════ 水位线持久化（kb 自有表；尽力而为） ═══════════════

    /** 读上次抽取水位（表缺失或异常时返回 null，即从头读）。 */
    private String readWatermark(String ontologyId, String entityCode, String resourceId) {
        try {
            List<String> rows = jdbc.queryForList(
                    "SELECT watermark FROM ecos_knowledge.kb_extract_watermark "
                            + "WHERE ontology_id = ? AND entity_code = ? AND resource_id = ?",
                    String.class, ontologyId, entityCode, resourceId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("读取抽取水位失败（按全量处理）: ontology={} entity={} resource={} err={}",
                    ontologyId, entityCode, resourceId, e.getMessage());
            return null;
        }
    }

    /** 写本次抽取水位（幂等 upsert；失败仅记 warn，不影响抽取结果）。 */
    private void writeWatermark(String ontologyId, String entityCode, String resourceId, String watermark) {
        try {
            jdbc.update(
                    "INSERT INTO ecos_knowledge.kb_extract_watermark "
                            + "(ontology_id, entity_code, resource_id, watermark, updated_at) "
                            + "VALUES (?, ?, ?, ?, NOW()) "
                            + "ON CONFLICT (ontology_id, entity_code, resource_id) "
                            + "DO UPDATE SET watermark = EXCLUDED.watermark, updated_at = NOW()",
                    ontologyId, entityCode, resourceId, truncate(watermark, 255));
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("写入抽取水位失败（下次仍按全量处理）: ontology={} entity={} resource={} err={}",
                    ontologyId, entityCode, resourceId, e.getMessage());
        }
    }

    // ═══════════════ 装配与工具 ═══════════════

    /** C4 拒绝：计入 invalidMappings 并记录 INVALID_MAPPING 明细（不静默吞）。 */
    private void rejectInvalidMapping(String entityCode, String reason, EntityInstanceExtractionReportVO report) {
        report.setInvalidMappings(report.getInvalidMappings() + 1);
        report.addIssue(entityCode, REJECT_CODE_INVALID_MAPPING, reason);
        log.warn("C4 拒绝实例化: entity={} reason={}", entityCode, reason);
    }

    /** 字段映射解析：按 DW 列定义判定哪一侧是物理列，兼容历史书写方向不固定。 */
    private List<FieldRef> resolveFieldRefs(Object fieldMappings, Set<String> columnNamesLower) {
        List<FieldRef> refs = new ArrayList<>();
        if (!(fieldMappings instanceof List<?> list)) {
            return refs;
        }
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> item)) {
                continue;
            }
            String left = firstNonBlank(text(item.get("source")), text(item.get("field")));
            String right = firstNonBlank(text(item.get("target")), text(item.get("propertyCode")));
            if (columnNamesLower.contains(left.toLowerCase(Locale.ROOT))) {
                refs.add(new FieldRef(left, right));
            } else if (columnNamesLower.contains(right.toLowerCase(Locale.ROOT))) {
                refs.add(new FieldRef(right, left));
            }
        }
        return refs;
    }

    /** 组装节点属性 JSON（本体侧引用 → 行值；写库时显式 {@code ?::jsonb} 转换）。 */
    private String buildPropertiesJson(Map<String, Object> row, List<FieldRef> fieldRefs) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (FieldRef ref : fieldRefs) {
            if (!ref.ontologyRef().isEmpty()) {
                properties.put(ref.ontologyRef(), row.get(ref.dwColumn()));
            }
        }
        try {
            return MAPPER.writeValueAsString(properties);
        } catch (Exception e) {
            log.warn("节点属性序列化失败，落空对象: {}", e.getMessage());
            return "{}";
        }
    }

    /** 节点 id：{@code nodeType:pk}；超 64 列宽时走 SHA-256 前缀（确定性 + 幂等）。 */
    private String buildNodeId(String nodeType, String pkValue) {
        String raw = nodeType + ":" + pkValue;
        if (raw.length() <= SHORT_COLUMN_MAX) {
            return raw;
        }
        String prefix = nodeType.length() > 20 ? nodeType.substring(0, 20) : nodeType;
        return prefix + "-" + sha256Hex(raw).substring(0, ID_HASH_LEN);
    }

    /** 边 id：{@code kgrel:ontology:source:relation:pk:fk}；超 64 列宽时走 SHA-256 前缀。 */
    private String buildEdgeId(String ontologyId, String sourceType, String relation,
                               String sourcePk, String fkValue) {
        String raw = "kgrel:" + ontologyId + ":" + sourceType + ":" + relation + ":" + sourcePk + ":" + fkValue;
        if (raw.length() <= SHORT_COLUMN_MAX) {
            return raw;
        }
        return "kgrel-" + sha256Hex(raw).substring(0, SHORT_COLUMN_MAX - 6);
    }

    /** SHA-256 十六进制摘要（id 收敛用，不用于安全用途）。 */
    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("SHA-256 计算失败，回退原文前缀: {}", e.getMessage());
            return Integer.toHexString(input.hashCode());
        }
    }

    /** materialized 解析：null / 缺省视为 true（Q2 裁决默认参与实例化）。 */
    private boolean isMaterialized(Object value) {
        if (value == null) {
            return true;
        }
        String raw = String.valueOf(value).trim();
        return raw.isEmpty() || "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    /** 主键标记解析（DataField.primaryKey 可能为 Boolean / Integer / String）。 */
    private boolean isPrimaryKey(Object value) {
        if (value == null) {
            return false;
        }
        String raw = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    /** null 安全转字符串（null → ""，去首尾空白）。 */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** 取首个非空白值（均空白返回 ""）。 */
    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    /** 字符串按列宽截断（null → null）。 */
    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** URL 查询参数编码。 */
    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 审计事件：走 Kafka {@code ecos.audit}；Kafka 不可用时本地 log 兜底（铁律 §2.4 #5）。
     */
    private void emitAudit(String action, String detail) {
        try {
            String payload = String.format("{\"action\":\"%s\",\"detail\":\"%s\",\"service\":\"kb-engine\"}",
                    action, detail == null ? "" : detail.replace("\"", "'").replace("\\", "/"));
            log.info("AUDIT topic={} payload={}", KafkaTopics.AUDIT, payload);
        } catch (Exception e) {
            log.warn("emitAudit fallback log failed: {}", e.getMessage());
        }
    }

    // ═══════════════ 内部类型 ═══════════════

    /** 本体快照投影（版本对齐基准 + C1 类型存在性判据）。 */
    private record OntologySnapshot(String ontologyId, String version, Set<String> entityCodes) {
    }

    /** DW 资源索引（按 resource_id / resource_name 双索引，一次拉取多次复用）。 */
    private record ResourceIndex(Map<String, Map<String, Object>> byId,
                                 Map<String, Map<String, Object>> byName) {

        /** 构建索引（resource_name 统一小写）。 */
        private static ResourceIndex of(List<Map<String, Object>> resources) {
            Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
            Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
            for (Map<String, Object> row : resources) {
                String id = text(row.get("resource_id"));
                String name = text(row.get("resource_name"));
                if (!id.isEmpty()) {
                    byId.putIfAbsent(id, row);
                }
                if (!name.isEmpty()) {
                    byName.putIfAbsent(name.toLowerCase(Locale.ROOT), row);
                }
            }
            return new ResourceIndex(byId, byName);
        }

        /** 空索引（元数据不可用时默认拒绝，不降级放行）。 */
        private static ResourceIndex empty() {
            return new ResourceIndex(Collections.emptyMap(), Collections.emptyMap());
        }

        /** 定位 DW 资源：优先 datasetId（resource_id），回退表名。 */
        private Map<String, Object> resolve(String datasetId, String resourceName) {
            Map<String, Object> resource = datasetId.isEmpty() ? null : byId.get(datasetId);
            if (resource == null && !resourceName.isEmpty()) {
                resource = byName.get(resourceName.toLowerCase(Locale.ROOT));
            }
            return resource;
        }
    }

    /** 字段映射（DW 物理列 ↔ 本体属性/关系引用）。 */
    private record FieldRef(String dwColumn, String ontologyRef) {
    }

    /** 本体关系定义（C2 判据：source_type + relation + target_type）。 */
    private record RelationDef(String sourceCode, String code, String targetCode) {
    }

    /** 实例边候选（延迟落库：节点全部写完后统一 flush，规避 graph_edge → graph_node 外键悬空）。 */
    private record EdgeCandidate(String edgeId, String sourceNodeId, String targetNodeId, String relation,
                                 OntologySnapshot snapshot, String resourceId, String sourcePk) {
    }

    /** DW 实例行分页结果。 */
    private record RowsPage(List<Map<String, Object>> rows, String nextWatermark, boolean hasMore) {
    }
}
