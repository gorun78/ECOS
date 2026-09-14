package com.chinacreator.gzcm.engine.data.quality.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.data.quality.DqScoreService;
import com.chinacreator.gzcm.engine.data.quality.model.DqAssetScoreVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqScoreSystemVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqScoreTrendVO;
import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionScore;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;
import com.chinacreator.gzcm.engine.data.quality.scoring.service.DqScoreEngine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DQ 评分服务实现（PMO-48-B T8）。
 *
 * <p>核心能力（接口注释 + 铁律 2.4 安全卡集成）：</p>
 * <ol>
 *   <li>{@link #recomputeForAsset} — 查 dq_rule/dq_rule_check → 6 维评估 → 写 dq_score_snapshot + dq_score_asset
 *       UPSERT → 异步 audit（DQ_SCORE_RECOMPUTE / DQ_SCORE_RECOMPUTE_REJECTED）</li>
 *   <li>{@link #getAssetScore} — 直查 dq_score_asset 单条 + 审计（DQ_SCORE_GET）</li>
 *   <li>{@link #trend} — 按天 GROUP BY date(evaluated_at) 查 dq_score_snapshot + 审计（DQ_SCORE_TREND）</li>
 *   <li>{@link #byGrade} — 全资产 + Java 侧按 grade 过滤 + 审计（DQ_SCORE_GRADE_SCAN）</li>
 *   <li>{@link #systemScore} — AVG(overall_score) + 近 1 天 6 维度均值 + 审计（DQ_SCORE_SYSTEM）</li>
 * </ol>
 *
 * <p>边界：</p>
 * <ul>
 *   <li>lookback 1h 防止跨窗口统计（dq_rule_check.executed_at >= NOW() - interval '1 hour'）</li>
 *   <li>无 QUALIFIED 规则 → 当前资产不可评估，返回 null + audit REJECTED</li>
 *   <li>rollback：写 snapshot/asset 用 {@code @Transactional}，单资产失败整体回滚</li>
 *   <li>敏感字段：target_field 命中 phone/mobile/金额/身份证/银行卡/手机 → mask（&lt;meta mask&gt; 在 detail）</li>
 * </ul>
 *
 * <p>Bean 名 {@code ecosDqScoreService}（铁律 1.3 防多 Bean 冲突）。</p>
 *
 * @author PMO-48-B T8
 */
@Service("ecosDqScoreService")
public class DqScoreServiceImpl implements DqScoreService {

    private static final Logger log = LoggerFactory.getLogger(DqScoreServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** 最大回看天数（DB 防爆炸） */
    private static final int TREND_DAYS_CAP = 365;

    /** 取最近 1h dq_rule_check 的窗口（与 V112 落表执行间隔对齐） */
    private static final String RECENT_CHECK_ONE_HOUR = "INTERVAL '1 hour'";

    private final DqScoreEngine engine;
    private final DqSecurityService securityService;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public DqScoreServiceImpl(DqScoreEngine engine,
                              DqSecurityService securityService,
                              ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.engine = engine;
        this.securityService = securityService;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    // ==================== 1. recomputeForAsset（写端点） ====================

    @Override
    @Transactional
    public DqAssetScoreVO recomputeForAsset(String assetType, String assetId) {
        JdbcTemplate jdbc = jdbc();
        String prefix = auditPrefix(assetType, assetId);
        try {
            // 1. 取该资产相关 ACTIVE 规则（限 TABLE 维度，target_kind='TABLE'）
            List<Map<String, Object>> rules = loadRulesForAsset(jdbc, assetType, assetId);
            if (rules.isEmpty()) {
                log.info("DqScore recompute: no ACTIVE rules for {} {}, skipped", assetType, assetId);
                // 审计写失败（默认 DENY 不阻塞主流程，但记录 reason）
                securityService.auditWrite("DQ_SCORE_RECOMPUTE", prefix, "REJECTED");
                return null;
            }
            // 2. 取配对 dq_rule_check（最近 1h）
            Map<String, Map<String, Object>> checkByRule = loadRecentChecks(jdbc, rules);
            // 3. 按维度聚合（一个维度可能有多个规则 → 用 max pass_rate 作为该维代表）
            Map<DqDimension, AggDim> byDim = aggregateByDimension(rules, checkByRule, assetType, assetId);
            // 4. 写 snapshot（每维度 1 行）
            for (Map.Entry<DqDimension, AggDim> entry : byDim.entrySet()) {
                jdbc.update(
                        "INSERT INTO ecos_dq.dq_score_snapshot" +
                        " (dimension, scope_type, scope_id, score_value, weight, sample_size, distinct_rule_count, evaluated_at, metadata)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), ?::jsonb)",
                        entry.getKey().name(),
                        assetType,
                        assetId,
                        entry.getValue().score,
                        entry.getKey().getDefaultWeight(),
                        entry.getValue().sampleSize,
                        entry.getValue().ruleCount,
                        entry.getValue().metadataJson);
            }
            // 5. 写 asset UPSERT（rolledScore + rolled_up_scores + last_evaluated_at）
            double rolled = weightedOverall(byDim);
            String rolledUpJson = serializeRolledUp(byDim);
            jdbc.update(
                    "INSERT INTO ecos_dq.dq_score_asset" +
                    " (asset_type, asset_id, overall_score, rolled_up_scores, last_evaluated_at, is_deleted)" +
                    " VALUES (?, ?, ?, ?, NOW(), FALSE)" +
                    " ON CONFLICT (asset_type, asset_id) DO UPDATE" +
                    " SET overall_score = EXCLUDED.overall_score," +
                    "     rolled_up_scores = EXCLUDED.rolled_up_scores," +
                    "     last_evaluated_at = EXCLUDED.last_evaluated_at," +
                    "     is_deleted = FALSE",
                    assetType, assetId, rolled, rolledUpJson);

            log.info("DqScore recomputed: {} {}, rules={}, rolled={}, grade={}",
                    assetType, assetId, rules.size(), rolled, DqDimension.FRESHNESS.gradeOf(rolled));
            DqAssetScoreVO vo = buildVO(assetType, assetId, rolled, byDim);
            // 6. 异步 audit 写操作（DQ_SCORE_RECOMPUTE / SUCCESS）
            securityService.auditWrite("DQ_SCORE_RECOMPUTE", prefix, "SUCCESS");
            return vo;
        } catch (RuntimeException ex) {
            log.warn("DqScore recompute failed for {} {}: {}", assetType, assetId, ex.getMessage());
            throw ex; // 事务回滚
        }
    }

    /** 查资产当前评分（dq_score_asset 最新一条）。 */
    @Override
    public DqAssetScoreVO getAssetScore(String assetType, String assetId) {
        JdbcTemplate jdbc = jdbc();
        List<DqAssetScoreVO> rows = jdbc.query(
                "SELECT asset_type AS assetType, asset_id AS assetId, overall_score AS rolledScore," +
                " rolled_up_scores, last_evaluated_at AS lastEvaluatedAt" +
                " FROM ecos_dq.dq_score_asset WHERE asset_type = ? AND asset_id = ? AND is_deleted = FALSE",
                (rs, i) -> mapAssetScore(rs),
                assetType, assetId);
        DqAssetScoreVO vo = rows.isEmpty() ? null : rows.get(0);
        securityService.auditRead("DQ_SCORE_GET", auditPrefix(assetType, assetId));
        return vo;
    }

    // ==================== 3. trend ====================

    @Override
    public List<DqScoreTrendVO> trend(String assetType, String assetId, int days) {
        int d = days <= 0 ? 30 : Math.min(days, TREND_DAYS_CAP);
        JdbcTemplate jdbc = jdbc();
        List<DqScoreTrendVO> rows = jdbc.query(
                "SELECT CAST(evaluated_at AS DATE) AS day," +
                " dimension, AVG(score_value) AS score_value, COUNT(*) AS hits" +
                " FROM ecos_dq.dq_score_snapshot" +
                " WHERE scope_type = ? AND scope_id = ?" +
                "   AND evaluated_at >= NOW() - (? || ' days')::interval" +
                " GROUP BY 1, 2 ORDER BY 1 DESC, 2",
                (rs, i) -> new DqScoreTrendVO(
                        rs.getDate("day") != null ? rs.getDate("day").toLocalDate() : LocalDate.now(),
                        rs.getString("dimension"),
                        rs.getDouble("score_value"),
                        rs.getInt("hits")),
                assetType, assetId, d);
        securityService.auditRead("DQ_SCORE_TREND", auditPrefix(assetType, assetId));
        return rows;
    }

    // ==================== 4. byGrade ====================

    @Override
    public List<DqAssetScoreVO> byGrade(String grade) {
        JdbcTemplate jdbc = jdbc();
        List<DqAssetScoreVO> all = jdbc.query(
                "SELECT asset_type AS assetType, asset_id AS assetId, overall_score AS rolledScore," +
                " rolled_up_scores, last_evaluated_at AS lastEvaluatedAt" +
                " FROM ecos_dq.dq_score_asset WHERE is_deleted = FALSE ORDER BY overall_score ASC",
                (rs, i) -> mapAssetScore(rs));
        List<DqAssetScoreVO> result = new ArrayList<>();
        String wanted = grade == null ? "" : grade.trim().toUpperCase();
        for (DqAssetScoreVO vo : all) {
            String g = DqDimension.FRESHNESS.gradeOf(vo.getRolledScore());
            vo.setGrade(g);
            if (wanted.isEmpty() || wanted.equals(g)) {
                result.add(vo);
            }
        }
        securityService.auditRead("DQ_SCORE_GRADE_SCAN", "grade=" + wanted);
        return result;
    }

    // ==================== 5. systemScore ====================

    @Override
    public DqScoreSystemVO systemScore() {
        JdbcTemplate jdbc = jdbc();
        DqScoreSystemVO vo = new DqScoreSystemVO();
        Double avg = jdbc.queryForObject(
                "SELECT AVG(overall_score) FROM ecos_dq.dq_score_asset WHERE is_deleted = FALSE", Double.class);
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_dq.dq_score_asset WHERE is_deleted = FALSE", Integer.class);
        vo.setOverallScore(avg != null ? avg : 0.0D);
        vo.setCount(cnt != null ? cnt : 0);
        vo.setGrade(DqScoreSystemVO.gradeOf(vo.getOverallScore()));

        jdbc.query(
                "SELECT dimension, AVG(score_value) AS avg_score" +
                " FROM ecos_dq.dq_score_snapshot" +
                " WHERE evaluated_at >= NOW() - interval '1 day'" +
                " GROUP BY dimension",
                rs -> {
                    String dim = rs.getString("dimension");
                    Double v = rs.getDouble("avg_score");
                    vo.getPerDimension().put(dim, v);
                    return null;
                });
        securityService.auditRead("DQ_SCORE_SYSTEM", "batch");
        return vo;
    }

    // ==================== 6. evaluateAll（内部 delegate） ====================

    @Override
    public Map<DqDimension, DimensionScore> evaluateAll(ScoringContext ctx) {
        return engine.evaluateAll(ctx);
    }

    // ==================== 私有工具 ====================

    private JdbcTemplate jdbc() {
        JdbcTemplate tpl = jdbcTemplateProvider.getIfAvailable();
        if (tpl == null) {
            throw new BusinessException("JdbcTemplate 不可用，无法执行 DQ 评分查询");
        }
        return tpl;
    }

    /** 审计资源前缀（dq_score_asset:{type}:{id}，铁律 2.4 #5 必须 resource 字段）。 */
    private static String auditPrefix(String assetType, String assetId) {
        return assetType + ":" + assetId;
    }

    /** 查活跃规则：target_kind='TABLE' AND target_id=assetId（DATASOURCE 留 interface 待 Phase 3）。 */
    private List<Map<String, Object>> loadRulesForAsset(JdbcTemplate jdbc, String assetType, String assetId) {
        if (!"TABLE".equalsIgnoreCase(assetType)) {
            return List.of();
        }
        return jdbc.query(
                "SELECT id, rule_type AS ruleType, target_field AS targetField," +
                " parameters::text AS parametersJson, domain, severity" +
                " FROM ecos_dq.dq_rule" +
                " WHERE target_kind = 'TABLE' AND target_id = ?" +
                "   AND status = 'ACTIVE' AND is_deleted = FALSE",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("ruleType", rs.getString("ruleType"));
                    m.put("targetField", rs.getString("targetField"));
                    m.put("parametersJson", rs.getString("parametersJson"));
                    m.put("domain", rs.getString("domain"));
                    m.put("severity", rs.getString("severity"));
                    return m;
                },
                assetId);
    }

    /** 查最近 1h 的 dq_rule_check 行（executed_at >= NOW() - interval '1 hour'）。 */
    private Map<String, Map<String, Object>> loadRecentChecks(JdbcTemplate jdbc, List<Map<String, Object>> rules) {
        Map<String, Map<String, Object>> byRule = new LinkedHashMap<>();
        if (rules.isEmpty()) {
            return byRule;
        }
        List<Object> ids = new ArrayList<>();
        for (Map<String, Object> r : rules) {
            ids.add(r.get("id"));
        }
        // 防注入：in (?) 占位
        String inClause = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        jdbc.query(
                "SELECT rule_id AS ruleId, passed, total_rows AS totalRows, failed_rows AS failedRows," +
                " pass_rate AS passRate, executed_at AS executedAt" +
                " FROM ecos_dq.dq_rule_check" +
                " WHERE rule_id IN (" + inClause + ")" +
                "   AND executed_at >= NOW() - " + RECENT_CHECK_ONE_HOUR,
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("passed", rs.getBoolean("passed"));
                    long t = rs.getLong("totalRows");
                    m.put("totalRows", t);
                    long f = rs.getLong("failedRows");
                    m.put("failedRows", f);
                    m.put("passRate", rs.getDouble("passRate"));
                    Timestamp execTs = rs.getTimestamp("executedAt");
                    m.put("executedAt", execTs != null ? execTs.toLocalDateTime() : null);
                    byRule.put(rs.getString("ruleId"), m);
                    return null;
                },
                ids.toArray());
        return byRule;
    }

    /** 按维度聚合：per-dim 累计 max(score) + sum(rule_count) + sum(sample_size) + 元数据。 */
    private Map<DqDimension, AggDim> aggregateByDimension(List<Map<String, Object>> rules,
                                                          Map<String, Map<String, Object>> checks,
                                                          String assetType, String assetId) {
        Map<DqDimension, AggDim> byDim = new LinkedHashMap<>();
        for (Map<String, Object> rule : rules) {
            String ruleType = (String) rule.get("ruleType");
            DqDimension dim = DqDimension.ofRuleType(ruleType);
            AggDim agg = byDim.computeIfAbsent(dim, k -> new AggDim());
            agg.ruleCount++;
            // 取对应 dq_rule_check（最近 1h 内）
            Map<String, Object> check = checks.get(rule.get("id"));
            long totalRows = check != null ? toLong(check.get("totalRows")) : 0L;
            long failedRows = check != null ? toLong(check.get("failedRows")) : 0L;
            Double passRate = toDouble(check != null ? check.get("passRate") : null);
            LocalDateTime executedAt = toLocalDateTime(check != null ? check.get("executedAt") : null);
            Map<String, Object> params = parseParamsJson((String) rule.get("parametersJson"));
            Map<String, Object> lastCheck = checks.get(rule.get("id"));
            ScoringContext ctx = DqScoreEngine.toContext(
                    (String) rule.get("id"), assetType, assetId,
                    params, new LinkedHashMap<>(), new LinkedHashMap<>(),
                    totalRows, failedRows, executedAt, lastCheck,
                    (String) rule.get("targetField"));
            // 评估器计算（不写库）
            DimensionScore s = engine.evaluateAll(ctx).get(dim);
            double v = s != null ? s.getScoreValue() : 0.0D;
            agg.score = Math.max(agg.score, clamp(v));
            agg.sampleSize += s != null ? s.getSampleSize() : 0;
            // metadata（区分维度 vs 规则）
            agg.metadata = mergeMetadata(agg.metadata, s, (String) rule.get("targetField"));
        }
        for (Map.Entry<DqDimension, AggDim> e : byDim.entrySet()) {
            e.getValue().metadataJson = metadataToJson(e.getValue().metadata);
        }
        return byDim;
    }

    /** 加权汇总：Σ(score × weight) / Σ(weight)；维度全空 → 0.0（F 级）。 */
    private double weightedOverall(Map<DqDimension, AggDim> byDim) {
        double sumW = 0.0D;
        double sumWS = 0.0D;
        for (Map.Entry<DqDimension, AggDim> e : byDim.entrySet()) {
            int w = e.getKey().getDefaultWeight();
            sumW += w;
            sumWS += e.getValue().score * w;
        }
        return sumW <= 0.0D ? 0.0D : sumWS / sumW;
    }

    /** 构造 rolled_up_scores JSONB 字符串。 */
    private String serializeRolledUp(Map<DqDimension, AggDim> byDim) {
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map.Entry<DqDimension, AggDim> e : byDim.entrySet()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("dimension", e.getKey().name());
                r.put("score", e.getValue().score);
                r.put("weight", e.getKey().getDefaultWeight());
                rows.add(r);
            }
            return MAPPER.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private DqAssetScoreVO buildVO(String assetType, String assetId, double rolled,
                                   Map<DqDimension, AggDim> byDim) {
        DqAssetScoreVO vo = DqAssetScoreVO.blank(assetType, assetId);
        vo.setRolledScore(rolled);
        vo.setGrade(DqDimension.FRESHNESS.gradeOf(rolled));
        vo.setLastEvaluatedAt(LocalDateTime.now());
        double weightSum = 0.0D;
        int sampleSize = 0;
        int ruleCount = 0;
        Map<String, Double> dimMap = new LinkedHashMap<>();
        for (Map.Entry<DqDimension, AggDim> e : byDim.entrySet()) {
            dimMap.put(e.getKey().name(), e.getValue().score);
            weightSum += e.getKey().getDefaultWeight();
            sampleSize += e.getValue().sampleSize;
            ruleCount += e.getValue().ruleCount;
        }
        vo.setDimensionScores(dimMap);
        vo.setWeightSum(weightSum);
        vo.setSampleSize(sampleSize);
        vo.setDistinctRuleCount(ruleCount);
        return vo;
    }

    /** 把 dq_score_asset 行映射 VO + rolled_up_scores 展开 dimensionScores。 */
    private DqAssetScoreVO mapAssetScore(java.sql.ResultSet rs) {
        DqAssetScoreVO vo = new DqAssetScoreVO();
        try {
            vo.setAssetType(rs.getString("assetType"));
            vo.setAssetId(rs.getString("assetId"));
            vo.setRolledScore(rs.getDouble("rolledScore"));
            vo.setGrade(DqDimension.FRESHNESS.gradeOf(vo.getRolledScore()));
            Timestamp ts = rs.getTimestamp("lastEvaluatedAt");
            vo.setLastEvaluatedAt(ts != null ? ts.toLocalDateTime() : null);
            // rolled_up_scores JSONB: [{"dimension":"X","score":0.9,"weight":30}, ...] — 列表结构
            String rolled = rs.getString("rolled_up_scores");
            Map<String, Double> dimScores = new LinkedHashMap<>();
            double weightSum = 0.0D;
            List<Map<String, Object>> parsed = parseRolledUp(rolled);
            for (Map<String, Object> row : parsed) {
                String dimName = row.get("dimension") == null ? null : String.valueOf(row.get("dimension"));
                Double s = toDouble(row.get("score"));
                Integer w = row.get("weight") instanceof Number n2 ? n2.intValue() : 0;
                if (dimName != null && s != null) {
                    dimScores.put(dimName, s);
                    weightSum += w;
                }
            }
            vo.setDimensionScores(dimScores);
            vo.setWeightSum(weightSum);
            return vo;
        } catch (java.sql.SQLException e) {
            log.error("DqScore mapAssetScore 读取 ResultSet 失败: {}", e.getMessage(), e);
            throw new BusinessException("读取数据质量评分结果失败: " + e.getMessage());
        }
    }

    /** 解析 rolled_up_scores JSONB 为 List<Map>（容忍 JSON 字符串/空）。 */
    private List<Map<String, Object>> parseRolledUp(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(json);
            if (node != null && node.isArray()) {
                return MAPPER.convertValue(node,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            }
            return new ArrayList<>();
        } catch (JsonProcessingException e) {
            log.warn("DqScoreService: rolled_up_scores 解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private Map<String, Object> parseParamsJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("DqScoreService: parametersJson 解析失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /** 合并 metadata（敏感字段 mask）。 */
    private Map<String, Object> mergeMetadata(Map<String, Object> merged, DimensionScore s, String targetField) {
        Map<String, Object> m = merged != null ? merged : new LinkedHashMap<>();
        if (s != null && s.getDetails() != null) {
            for (Map.Entry<String, Object> entry : s.getDetails().entrySet()) {
                String k = entry.getKey();
                Object v = entry.getValue();
                if (k != null && k.equalsIgnoreCase("targetField")) {
                    m.put(k, "***");
                } else {
                    m.put(k, v);
                }
            }
        }
        if (targetField != null && !targetField.isBlank()
                && securityService.containsSensitiveField(targetField)) {
            m.put("targetField", "***");
            m.put("sensitiveMasked", true);
        }
        return m;
    }

    private String metadataToJson(Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static Double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private static LocalDateTime toLocalDateTime(Object o) {
        return o instanceof LocalDateTime ldt ? ldt : (o instanceof Timestamp ts ? ts.toLocalDateTime() : null);
    }

    private static double clamp(double v) {
        if (Double.isNaN(v) || v < 0.0D) {
            return 0.0D;
        } else if (v > 1.0D) {
            return 1.0D;
        }
        return v;
    }

    /** 单维度聚合（per-dim 取 max(score) + sum(sample) + sum(rule_count)）。 */
    static class AggDim {
        double score = 0.0D;
        int sampleSize = 0;
        int ruleCount = 0;
        Map<String, Object> metadata = new LinkedHashMap<>();
        String metadataJson = "{}";
    }
}
