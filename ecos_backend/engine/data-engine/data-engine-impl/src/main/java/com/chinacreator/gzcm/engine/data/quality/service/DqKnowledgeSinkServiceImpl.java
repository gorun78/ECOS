package com.chinacreator.gzcm.engine.data.quality.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.data.quality.DqKnowledgeSinkService;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqAlertVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqKnowledgeEntryVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DQ 知识沉淀服务实现（PMO-48-D T16）。
 *
 * <p><b>沉淀链</b>（fire-and-forget，@Async 不阻塞主流程）：</p>
 * <ol>
 *   <li>源表读实体（dq_rule / dq_alert_record / dq_work_order），拼 content + entityJson</li>
 *   <li>算哈希向量：MD5(content) → 32 字节 → 逐字节 32 次重复 → 1024 维 float[]（pgvector 未装降级）</li>
 *   <li>写 {@code ecos_dq.dq_knowledge_entry}（source_type / source_id / category / embedding JSON）</li>
 *   <li>异步 audit（{@code DQ_KB_INGEST_*}）— 铁律 2.4 #5</li>
 * </ol>
 *
 * <p><b>RAG 相似检索</b>：{@link #searchSimilar(String, int)} 全表扫描（本地数据 < 5000 条），
 * 反序列化 embedding 后算 <b>Jaccard 字符二元组重叠度</b>（0.0 ~ 1.0），降序取 top N。
 * 不用 cosine / 不用 {@code <->} pgvector 运算符（CSE 不装扩展）。</p>
 *
 * <p><b>异常策略</b>：每个 ingest 方法整体 try/catch，单条失败记 log + 返回，不抛（主流程不炸）。</p>
 *
 * <p>Bean 名 {@code ecosDqKnowledgeSinkService}（铁律 1.3 防多 Bean 冲突）。</p>
 *
 * @author PMO-48-D T16
 */
@Service("ecosDqKnowledgeSinkService")
public class DqKnowledgeSinkServiceImpl implements DqKnowledgeSinkService {

    private static final Logger log = LoggerFactory.getLogger(DqKnowledgeSinkServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** embedding 维度（V114 列降级 TEXT 存 JSON 数组，DIM=1024） */
    private static final int EMBEDDING_DIM = 1024;

    /** 全表扫描上限（本地知识条目 < 5000，5000 兜底防爆） */
    private static final int SCAN_LIMIT = 5000;

    /** 音频号占位 */
    private static final String SEP = " | ";

    private final DqSecurityService securityService;
    private final DqRuleMapper ruleMapper;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public DqKnowledgeSinkServiceImpl(DqSecurityService securityService,
                                       DqRuleMapper ruleMapper,
                                       ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.securityService = securityService;
        this.ruleMapper = ruleMapper;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    // ==================== 1. 三个沉淀入口（@Async fire-and-forget） ====================

    @Override
    @Async
    public void ingestFromRule(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return;
        }
        try {
            JdbcTemplate jdbc = requireJdbc();
            DqRuleVO rule = safeFindRule(ruleId);
            String title = buildRuleTitle(rule);
            String content = buildRuleContent(rule);
            String summary = buildRuleSummary(rule);
            String entityJson = buildRuleEntityJson(rule);
            String embedding = toEmbeddingJson(content);
            insertEntry(jdbc, "RULE", ruleId, "rule-def", title, summary, content, entityJson, embedding);
            securityService.auditWrite("DQ_KB_INGEST_RULE", ruleId, "SUCCESS");
            log.info("DqKnowledgeSink rule 沉淀: ruleId={}, title={}", ruleId, title);
        } catch (RuntimeException e) {
            log.warn("DqKnowledgeSink rule 沉淀失败（不阻塞）: ruleId={}, error={}", ruleId, e.getMessage());
            securityService.auditWrite("DQ_KB_INGEST_RULE", ruleId, "FAILED");
        }
    }

    @Override
    @Async
    public void ingestFromAlert(String alertId, DqAlertVO alert) {
        if (alertId == null || alertId.isBlank() || alert == null) {
            return;
        }
        try {
            JdbcTemplate jdbc = requireJdbc();
            String title = buildAlertTitle(alert);
            String content = buildAlertContent(alert);
            String summary = alert.getMessage() != null ? alert.getMessage() : title;
            String entityJson = buildAlertEntityJson(alert);
            String embedding = toEmbeddingJson(content);
            insertEntry(jdbc, "ALERT", alertId, "alert", title, summary, content, entityJson, embedding);
            securityService.auditWrite("DQ_KB_INGEST_ALERT", alertId, "SUCCESS");
            log.info("DqKnowledgeSink alert 沉淀: alertId={}, level={}, title={}", alertId, alert.getAlertLevel(), title);
        } catch (RuntimeException e) {
            log.warn("DqKnowledgeSink alert 沉淀失败（不阻塞）: alertId={}, error={}", alertId, e.getMessage());
            securityService.auditWrite("DQ_KB_INGEST_ALERT", alertId, "FAILED");
        }
    }

    @Override
    @Async
    public void ingestFromWorkOrder(String workOrderId, DqWorkOrderVO order) {
        if (workOrderId == null || workOrderId.isBlank() || order == null) {
            return;
        }
        try {
            JdbcTemplate jdbc = requireJdbc();
            String title = buildWorkOrderTitle(order);
            String content = buildWorkOrderContent(order);
            String summary = order.getResolutionNote() != null ? order.getResolutionNote() : order.getTitle();
            String entityJson = buildWorkOrderEntityJson(order);
            String embedding = toEmbeddingJson(content);
            String category = resolveWorkOrderCategory(order);
            insertEntry(jdbc, "WORK_ORDER", workOrderId, category, title, summary, content, entityJson, embedding);
            securityService.auditWrite("DQ_KB_INGEST_WORK_ORDER", workOrderId, "SUCCESS");
            log.info("DqKnowledgeSink workOrder 沉淀: workOrderId={}, category={}, title={}",
                    workOrderId, category, title);
        } catch (RuntimeException e) {
            log.warn("DqKnowledgeSink workOrder 沉淀失败（不阻塞）: workOrderId={}, error={}",
                    workOrderId, e.getMessage());
            securityService.auditWrite("DQ_KB_INGEST_WORK_ORDER", workOrderId, "FAILED");
        }
    }

    // ==================== 2. RAG 相似检索（Jaccard 全表扫描） ====================

    @Override
    public PageResult<DqKnowledgeEntryVO> searchSimilar(String query, int top) {
        int limit = top <= 0 ? 3 : Math.min(top, 20);
        if (query == null || query.isBlank()) {
            return new PageResult<>(new ArrayList<>(), 0L, 1, limit);
        }
        JdbcTemplate jdbc = requireJdbc();

        // 1. 算查询向量的字符二元组集合
        Set<String> queryBigrams = charBigrams(query.toLowerCase());

        // 2. 全表扫描（LIMIT 兜底，本地数据有限）
        List<DqKnowledgeEntryVO> rows;
        try {
            rows = jdbc.query(
                    "SELECT id, source_type AS sourceType, source_id AS sourceId, category, title, summary,"
                    + " content_md AS contentMd, entity_json::text AS entityJson, embedding AS embeddingJson,"
                    + " score_hint AS scoreHint, created_at AS createdAt"
                    + " FROM ecos_dq.dq_knowledge_entry ORDER BY created_at DESC LIMIT ?",
                    (rs, i) -> mapEntryRow(rs), limit * 5);
        } catch (DataAccessException e) {
            log.warn("DqKnowledgeSink searchSimilar 扫描失败: error={}", e.getMessage());
            return new PageResult<>(new ArrayList<>(), 0L, 1, limit);
        }

        // 3. 逐条算 Jaccard 相似度（优先 content，降级 title），收集 (score, vo)
        List<Map.Entry<Double, DqKnowledgeEntryVO>> scored = new ArrayList<>();
        for (DqKnowledgeEntryVO vo : rows) {
            double sim = jaccard(queryBigrams, textBigrams(vo.getContentMd(), vo.getTitle()));
            if (sim > 0.0D) {
                scored.add(Map.entry(sim, vo));
            }
        }

        // 4. 降序取 top N
        scored.sort((a, b) -> Double.compare(b.getKey(), a.getKey()));
        List<DqKnowledgeEntryVO> out = new ArrayList<>();
        for (int i = 0; i < limit && i < scored.size(); i++) {
            out.add(scored.get(i).getValue());
        }
        return new PageResult<>(out, (long) scored.size(), 1, limit);
    }

    // ==================== 3. content / entityJson 拼装 ====================

    /** 告警 title：[level] ruleName — targetTable:targetField（缺省剔除） */
    private static String buildAlertTitle(DqAlertVO alert) {
        StringBuilder sb = new StringBuilder("[DQ ").append(alert.getAlertLevel() == null ? "?" : alert.getAlertLevel());
        sb.append("] ").append(alert.getRuleName() != null ? alert.getRuleName() : alert.getRuleId());
        if (alert.getAssetName() != null && !alert.getAssetName().isBlank()) {
            sb.append(" — ").append(alert.getAssetName());
        }
        return truncate191(sb.toString());
    }

    /** 告警 content：可检索的自然语言拼合（level/ruleName/asset/message/errorCode） */
    private static String buildAlertContent(DqAlertVO alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("数据质量告警 ").append(alert.getAlertLevel() == null ? "?" : alert.getAlertLevel());
        sb.append(SEP).append(alert.getRuleName() != null ? alert.getRuleName() : alert.getRuleId());
        if (alert.getAssetName() != null && !alert.getAssetName().isBlank()) {
            sb.append(SEP).append(alert.getAssetName());
        }
        if (alert.getMessage() != null && !alert.getMessage().isBlank()) {
            sb.append(SEP).append(alert.getMessage());
        }
        Object err = alert.getPayload() != null ? alert.getPayload().get("errorMessage") : null;
        if (err != null && !String.valueOf(err).isBlank()) {
            sb.append(SEP).append(err);
        }
        return sb.toString();
    }

    private static String buildAlertEntityJson(DqAlertVO alert) {
        Map<String, Object> m = new LinkedHashMap<>();
        putIfPresent(m, "ruleId", alert.getRuleId());
        putIfPresent(m, "ruleName", alert.getRuleName());
        putIfPresent(m, "alertLevel", alert.getAlertLevel());
        putIfPresent(m, "alertType", alert.getAlertType());
        putIfPresent(m, "assetId", alert.getAssetId());
        putIfPresent(m, "assetName", alert.getAssetName());
        putIfPresent(m, "scope", alert.getAssetName());
        putIfPresent(m, "category", "alert");
        return toJsonSafe(m);
    }

    private static String buildWorkOrderTitle(DqWorkOrderVO order) {
        String base = order.getTitle() != null ? order.getTitle() : order.getOrderNo();
        return truncate191("[WO] " + base);
    }

    private static String buildWorkOrderContent(DqWorkOrderVO order) {
        StringBuilder sb = new StringBuilder();
        sb.append("工单 ").append(order.getOrderNo() != null ? order.getOrderNo() : order.getId());
        sb.append(SEP).append(order.getTitle() != null ? order.getTitle() : "");
        if (order.getDescription() != null && !order.getDescription().isBlank()) {
            sb.append(SEP).append(order.getDescription());
        }
        if (order.getResolutionNote() != null && !order.getResolutionNote().isBlank()) {
            sb.append(SEP).append("处理: ").append(order.getResolutionNote());
        }
        if (order.getRcaResult() != null && !order.getRcaResult().isBlank()) {
            sb.append(SEP).append(order.getRcaResult());
        }
        return sb.toString();
    }

    private static String buildWorkOrderEntityJson(DqWorkOrderVO order) {
        Map<String, Object> m = new LinkedHashMap<>();
        putIfPresent(m, "orderNo", order.getOrderNo());
        putIfPresent(m, "ruleId", order.getRuleId());
        putIfPresent(m, "severity", order.getSeverity());
        putIfPresent(m, "alertLevel", order.getSeverity());
        putIfPresent(m, "alertId", order.getAlertId());
        putIfPresent(m, "assetId", order.getAssetId());
        putIfPresent(m, "resolutionNote", order.getResolutionNote());
        putIfPresent(m, "category", resolveWorkOrderCategory(order));
        return toJsonSafe(m);
    }

    /** 工单 category：有 RCA 结果 → rule-hit；有处理说明 → fix-pattern；否则 rule-hit */
    private static String resolveWorkOrderCategory(DqWorkOrderVO order) {
        if (order.getResolutionNote() != null && !order.getResolutionNote().isBlank()) {
            return "fix-pattern";
        }
        return "rule-hit";
    }

    private static String buildRuleTitle(DqRuleVO rule) {
        if (rule == null) {
            return "[DQ] rule";
        }
        return truncate191("[RULE] " + (rule.getRuleName() != null ? rule.getRuleName() : rule.getId()));
    }

    private static String buildRuleSummary(DqRuleVO rule) {
        if (rule == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (rule.getRuleName() != null) {
            sb.append(rule.getRuleName());
        }
        if (rule.getTargetTable() != null && !rule.getTargetTable().isBlank()) {
            sb.append("（").append(rule.getTargetTable());
            if (rule.getTargetField() != null && !rule.getTargetField().isBlank()) {
                sb.append(".").append(rule.getTargetField());
            }
            sb.append("，").append(rule.getSeverity() != null ? rule.getSeverity() : "").append("）");
        }
        return sb.toString();
    }

    private static String buildRuleContent(DqRuleVO rule) {
        if (rule == null) {
            return "规则知识条目";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("数据质量规则 ").append(rule.getRuleName() != null ? rule.getRuleName() : rule.getId());
        if (rule.getRuleType() != null && !rule.getRuleType().isBlank()) {
            sb.append(SEP).append(rule.getRuleType());
        }
        if (rule.getTargetTable() != null && !rule.getTargetTable().isBlank()) {
            sb.append(SEP).append(rule.getTargetTable());
        }
        if (rule.getTargetField() != null && !rule.getTargetField().isBlank()) {
            sb.append(".").append(rule.getTargetField());
        }
        if (rule.getSeverity() != null && !rule.getSeverity().isBlank()) {
            sb.append(SEP).append(rule.getSeverity());
        }
        if (rule.getDescription() != null && !rule.getDescription().isBlank()) {
            sb.append(SEP).append(rule.getDescription());
        }
        return sb.toString();
    }

    private static String buildRuleEntityJson(DqRuleVO rule) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (rule != null) {
            putIfPresent(m, "ruleId", rule.getId());
            putIfPresent(m, "ruleName", rule.getRuleName());
            putIfPresent(m, "ruleType", rule.getRuleType());
            putIfPresent(m, "targetTable", rule.getTargetTable());
            putIfPresent(m, "targetField", rule.getTargetField());
            putIfPresent(m, "severity", rule.getSeverity());
            putIfPresent(m, "alertLevel", rule.getSeverity());
        }
        putIfPresent(m, "category", "rule-def");
        return toJsonSafe(m);
    }

    // ==================== 4. 落库 + 行映射 ====================

    private void insertEntry(JdbcTemplate jdbc, String sourceType, String sourceId, String category,
                             String title, String summary, String content, String entityJson, String embedding) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO ecos_dq.dq_knowledge_entry"
                + " (id, source_type, source_id, category, title, summary, content_md, entity_json, embedding, created_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, NOW())",
                id, sourceType, sourceId, category,
                truncate191(title != null ? title : "(untitled)"), summary, content,
                entityJson != null ? entityJson : "{}", embedding);
    }

    private static DqKnowledgeEntryVO mapEntryRow(ResultSet rs) throws SQLException {
        DqKnowledgeEntryVO vo = new DqKnowledgeEntryVO();
        vo.setId(rs.getString("id"));
        vo.setSourceType(rs.getString("sourceType"));
        vo.setSourceId(rs.getString("sourceId"));
        vo.setCategory(rs.getString("category"));
        vo.setTitle(rs.getString("title"));
        vo.setSummary(rs.getString("summary"));
        vo.setContentMd(rs.getString("contentMd"));
        vo.setEntityJson(rs.getString("entityJson"));
        vo.setEmbeddingJson(rs.getString("embeddingJson"));
        double sh = rs.getDouble("scoreHint");
        vo.setScoreHint(rs.wasNull() ? null : sh);
        Timestamp ts = rs.getTimestamp("createdAt");
        vo.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
        return vo;
    }

    // ==================== 5. embedding（MD5 字节展开 → 1024 维 JSON） ====================

    /**
     * 哈希向量：MD5(content) → 32 字节 → 逐字节重复 32 次 → 1024 维 float[]，
     * 归一化到 [0,1]，序列化 JSON 数组字符串（存 TEXT 列）。
     *
     * <p>确定性：同 content → 同向量（便于检索对齐）。不调 LLM / 不装 pgvector。</p>
     */
    private static String toEmbeddingJson(String content) {
        String h = content == null ? "" : content;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(h.getBytes(StandardCharsets.UTF_8));
            float[] vec = new float[EMBEDDING_DIM];
            for (int i = 0; i < EMBEDDING_DIM; i++) {
                vec[i] = (d[i % d.length] & 0xFF) / 255.0F;
            }
            StringBuilder sb = new StringBuilder(EMBEDDING_DIM * 6);
            sb.append('[');
            for (int i = 0; i < EMBEDDING_DIM; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(String.format(java.util.Locale.ROOT, "%.6f", vec[i]));
            }
            sb.append(']');
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 是 JDK 必备算法，此分支理论上不可达 — 日志并降级空
            log.warn("DqKnowledgeSink embedding MD5 算法不可用（降级空）: error={}", e.getMessage());
            return "[]";
        } catch (RuntimeException e) {
            log.warn("DqKnowledgeSink embedding 计算失败（降级空）: error={}", e.getMessage());
            return "[]";
        }
    }

    // ==================== 6. Jaccard 字符二元组相似度 ====================

    /** 字符二元组集合（小写化，空串返回空集合） */
    private static Set<String> textBigrams(String primary, String secondary) {
        if (primary != null && !primary.isBlank()) {
            return charBigrams(primary.toLowerCase());
        }
        if (secondary != null && !secondary.isBlank()) {
            return charBigrams(secondary.toLowerCase());
        }
        return new HashSet<>();
    }

    /** 提取所有相邻字符二元组（<2 字符返回单字符集合降级） */
    private static Set<String> charBigrams(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) {
            return out;
        }
        String t = s.replaceAll("\\s+", "");
        if (t.isEmpty()) {
            return out;
        }
        if (t.length() < 2) {
            out.add(t);
            return out;
        }
        for (int i = 0; i < t.length() - 1; i++) {
            out.add(t.substring(i, i + 2));
        }
        return out;
    }

    /** Jaccard = |A∩B| / |A∪B|；A 或 B 空 → 0.0 */
    private static double jaccard(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0D;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        int u = union.isEmpty() ? 1 : union.size();
        return (double) inter.size() / u;
    }

    // ==================== 7. 私有工具 ====================

    private DqRuleVO safeFindRule(String ruleId) {
        try {
            return ruleMapper.findById(ruleId);
        } catch (RuntimeException e) {
            log.debug("DqKnowledgeSink 查规则失败（降级空）: ruleId={}, error={}", ruleId, e.getMessage());
            return null;
        }
    }

    private static void putIfPresent(Map<String, Object> m, String k, Object v) {
        if (v != null && !String.valueOf(v).isBlank()) {
            m.put(k, String.valueOf(v));
        }
    }

    private static String toJsonSafe(Object o) {
        try {
            return MAPPER.writeValueAsString(o != null ? o : new HashMap<String, Object>());
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String truncate191(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 191 ? s.substring(0, 191) : s;
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            throw new BusinessException("JdbcTemplate 不可用，无法执行 DQ 知识沉淀");
        }
        return jdbc;
    }
}
