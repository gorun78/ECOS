package com.chinacreator.gzcm.engine.ontology.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chinacreator.gzcm.engine.ontology.dao.CaliberDao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Caliber 一等实体 + 版本状态机 + Metric 口径绑定（W73/M0 · F03-01 + W76/M0 · rule 4）.
 *
 * <p>口径主权归本体域（R-6=①）：主表 {@code ecos_ontology.ecos_caliber} + 版本表
 * {@code ecos_ontology.ecos_caliber_version}（V167.1），指标口径绑定落
 * {@code ecos_ontology.ecos_metric_definition}（V168.1，列式绑定，无独立绑定表）。
 * 认知/工作空间/kb 经 {@code GET /api/v1/ontology/calibers/{code}/current} 只读消费。
 * 数据访问下沉 {@link CaliberDao}（R2-B3 / 架构铁律 §2.5 #1：Service 不直持 JdbcTemplate）。
 *
 * <p>纪律（《详细设计-03-本体域》F03-01 / 《数据库访问规范》MC01/MC06 / ST07）：
 * <ul>
 *   <li>MC01 — 主键 VARCHAR(36) 由<b>应用侧</b> {@link UUID#randomUUID()} 生成，DDL 无默认值；</li>
 *   <li>MC06 — SQL 统一 schema 前缀（默认 {@code ecos_ontology}，
 *       {@code @Value("${ecos.ontology.control-schema:ecos_ontology}")} 注入 DAO）；</li>
 *   <li>DR05 — {@code is_deleted=0} 软删；显式列清单，禁 {@code SELECT *}。</li>
 * </ul>
 *
 * <p>异常语义（Controller 映射为 HTTP）：
 * <ul>
 *   <li>{@link SnapshotFrozenException} — 409 ECOS-ONTO-041（发布冻结快照不可改写）;</li>
 *   <li>{@link CaliberMissingException} / {@link CaliberNotApprovedException} — 400 ECOS-ONTO-040;</li>
 *   <li>其它 — 500 ECOS-ONTO-042（fail-closed）。</li>
 * </ul>
 */
@Component
public class CaliberService {

    private static final Logger log = LoggerFactory.getLogger(CaliberService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CaliberDao caliberDao;

    public CaliberService(CaliberDao caliberDao) {
        this.caliberDao = caliberDao;
    }

    // ────────────────────────────────────────────────────────────
    // 口径主表
    // ────────────────────────────────────────────────────────────

    /**
     * 新建 DRAFT 口径主记录。
     *
     * <p>MC01：id 由应用侧 {@link UUID#randomUUID()} 生成；DR05 is_deleted=0；
     * DR07 version_no='1'；DR08 domain='default'。</p>
     *
     * @return 插入后的主表行（显式列清单）
     */
    public Map<String, Object> insertCaliber(String code, String name, String unit, String currency,
                                             String ownerRole, String dimensionJson, String status) {
        String id = UUID.randomUUID().toString();
        String effectiveStatus = (status == null || status.isBlank()) ? "ACTIVE" : status;
        caliberDao.insertCaliber(id, code, name, effectiveStatus, unit, currency, ownerRole, dimensionJson);
        return findByCode(code).orElseGet(LinkedHashMap::new);
    }

    /**
     * 口径列表（{@code is_deleted=0}），可选 status 过滤，LIMIT ?。
     */
    public List<Map<String, Object>> listCalibers(String status, int limit) {
        return caliberDao.listCalibers(status, limit);
    }

    /**
     * 按业务编码检索口径（{@code is_deleted=0}），不存在返回 empty。
     */
    public Optional<Map<String, Object>> findByCode(String code) {
        return caliberDao.findByCode(code);
    }

    /**
     * 取口径当前生效版本行 = 该口径（主表 status='ACTIVE'）下<b>最近一个 APPROVED</b> 版本。
     *
     * <p>这是跨引擎（cognitive/workspace/kb）的只读对外面。</p>
     */
    public Optional<Map<String, Object>> getByCaliberCodeAndCurrentVersion(String code) {
        return caliberDao.getByCaliberCodeAndCurrentVersion(code);
    }

    // ────────────────────────────────────────────────────────────
    // 口径版本（行不可变，一次升版一行）
    // ────────────────────────────────────────────────────────────

    /**
     * 列出某口径的版本行（{@code is_deleted=0}），可选 status 过滤。
     */
    public List<Map<String, Object>> listVersionsByCaliberId(String caliberId, String status) {
        return caliberDao.listVersionsByCaliberId(caliberId, status);
    }

    /**
     * 发布一个新的口径版本行（行不可变）。
     *
     * <p>status 为空时默认 DRAFT；升版后仅在 {@link #archiveVersion} 推进时改为 APPROVED。</p>
     *
     * @return 插入后的版本行
     */
    public Map<String, Object> insertVersion(String caliberId, String versionNo, String formula,
                                             Integer additive, String periodGranularity, String status) {
        String effectiveStatus = (status == null || status.isBlank()) ? "DRAFT" : status;
        String id = UUID.randomUUID().toString();
        caliberDao.insertVersion(id, caliberId, versionNo, formula, additive, periodGranularity, effectiveStatus);
        return caliberDao.findVersionById(id);
    }

    /**
     * 版本状态机推进（单次前向推进）。
     *
     * <p>仅允许前向迁移：DRAFT→REVIEWING、DRAFT→APPROVED、REVIEWING→APPROVED、
     * APPROVED→RETIRED。</p>
     * <p><b>行不可变 = 幂等无副作用护栏</b>：当前状态已等于目标（重审批）、迁移非法、
     * 或行不存在 → 一律返回 0（不 UPDATE、不抛异常）。APPROVED 之后除 RETIRED 外不可再流转
     * （idempotency guard）。</p>
     *
     * @return 受影响行数（0 = 无迁移 / 已终态 / 行不存在）
     */
    public int archiveVersion(String caliberId, String versionNo, String newStatus, String approvedBy) {
        String current = caliberDao.findVersionStatus(caliberId, versionNo);
        if (current == null || current.equals(newStatus) || !isForwardTransition(current, newStatus)) {
            return 0;
        }
        return caliberDao.archiveVersion(caliberId, versionNo, newStatus, approvedBy, current);
    }

    private static boolean isForwardTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        return (from.equals("DRAFT") && (to.equals("REVIEWING") || to.equals("APPROVED")))
                || (from.equals("REVIEWING") && to.equals("APPROVED"))
                || (from.equals("APPROVED") && to.equals("RETIRED"));
    }

    // ────────────────────────────────────────────────────────────
    // Metric 口径绑定（rule 4 / rule 3）
    // ────────────────────────────────────────────────────────────

    /**
     * 将指标绑定到口径 + 其版本，并在首次绑定时冻结 {@code caliber_snapshot}。
     *
     * <p>rule 3：若该指标的 {@code caliber_snapshot} 已非空，抛
     * {@link SnapshotFrozenException}（发布后快照只读）；否则在同一 UPDATE 写入
     * {@code caliber_id / formula_version / caliber_snapshot}（快照由 Jackson 组装）。</p>
     */
    public Map<String, Object> bindMetricCaliber(String metricCode, String caliberId, String formulaVersion) {
        throwIfSnapshotFrozen(metricCode);
        String formula = caliberDao.lookupVersionFormula(caliberId, formulaVersion);
        String snapshot = buildSnapshot(caliberId, formula, formulaVersion);
        caliberDao.bindMetricCaliber(metricCode, caliberId, formulaVersion, snapshot);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("metricCode", metricCode);
        row.put("caliber_id", caliberId);
        row.put("formula_version", formulaVersion);
        row.put("caliber_snapshot", snapshot);
        return row;
    }

    /**
     * 单独改写快照 — <b>仅当</b> 当前快照为空时才允许。
     *
     * <p>rule 3：快照已非空 → 抛 {@link SnapshotFrozenException}。</p>
     *
     * @return 写入的快照文本
     */
    public String updateMetricCaliberSnapshot(String metricCode, String newSnapshot) {
        throwIfSnapshotFrozen(metricCode);
        caliberDao.updateMetricCaliberSnapshot(metricCode, newSnapshot);
        return newSnapshot;
    }

    /**
     * rule 4 发布门禁：PUBLISHED 指标无口径 / 口径版本非 APPROVED → 发布失败。
     *
     * <ul>
     *   <li>行不存在 → 静默（Draft 路径，由调用方裁定）；</li>
     *   <li>{@code caliber_id IS NULL} → 抛 {@link CaliberMissingException};</li>
     *   <li>口径存在但 {@code formula_version} 对应版本 status != APPROVED → 抛
     *       {@link CaliberNotApprovedException}；</li>
     *   <li>口径已绑定且 APPROVED 但快照为空 → 就地补写冻结快照。</li>
     * </ul>
     */
    public void assertMetricCaliberApprovedForPublish(String metricCode) {
        Map<String, Object> metric = caliberDao.findMetricCaliberBinding(metricCode);
        if (metric == null) {
            return; // 行不存在 → Draft 路径
        }

        Object caliberId = metric.get("caliber_id");
        if (caliberId == null || String.valueOf(caliberId).isBlank()) {
            throw new CaliberMissingException(
                    "PUBLISHED metric '" + metricCode + "' has no approved caliber (400 ECOS-ONTO-040)");
        }
        String cid = String.valueOf(caliberId);
        String fv = nullToEmpty(String.valueOf(metric.get("formula_version")));

        String rowStatus = caliberDao.lookupVersionStatus(cid, fv);
        if (!"APPROVED".equals(rowStatus)) {
            throw new CaliberNotApprovedException(
                    "PUBLISHED metric '" + metricCode + "' binds caliber version '" + fv
                            + "' with status " + rowStatus + " != APPROVED (400 ECOS-ONTO-040)");
        }

        Object snapshot = metric.get("caliber_snapshot");
        if (snapshot == null || String.valueOf(snapshot).isBlank()) {
            String frozen = buildSnapshot(cid, caliberDao.lookupVersionFormula(cid, fv), fv);
            caliberDao.updateMetricCaliberSnapshot(metricCode, frozen);
        }
    }

    /** rule 3 护栏：快照已非空则抛 {@link SnapshotFrozenException}。 */
    private void throwIfSnapshotFrozen(String metricCode) {
        String snapshot = caliberDao.findMetricCaliberSnapshot(metricCode);
        if (snapshot != null && !snapshot.isBlank()) {
            throw new SnapshotFrozenException(
                    "caliber_snapshot for metric '" + metricCode
                            + "' is already frozen and immutable (409 ECOS-ONTO-041)");
        }
    }

    private static String buildSnapshot(String caliberId, String formula, String formulaVersion) {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "caliber_id", nullToEmpty(caliberId),
                    "formula", nullToEmpty(formula),
                    "version_no", nullToEmpty(formulaVersion),
                    "frozen_at", Instant.now().toString()));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize caliber_snapshot", e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ────────────────────────────────────────────────────────────
    // 异常类型（Controller 映射 HTTP）
    // ────────────────────────────────────────────────────────────

    /** rule 3：发布冻结快照不可改写 → 409 ECOS-ONTO-041。 */
    public static final class SnapshotFrozenException extends RuntimeException {
        public SnapshotFrozenException(String message) {
            super(message);
        }
    }

    /** rule 4：PUBLISHED 指标无口径 → 400 ECOS-ONTO-040。 */
    public static final class CaliberMissingException extends RuntimeException {
        public CaliberMissingException(String message) {
            super(message);
        }
    }

    /** rule 4：PUBLISHED 指标绑定的口径版本非 APPROVED → 400 ECOS-ONTO-040。 */
    public static final class CaliberNotApprovedException extends RuntimeException {
        public CaliberNotApprovedException(String message) {
            super(message);
        }
    }
}
