package com.chinacreator.gzcm.engine.ontology.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chinacreator.gzcm.engine.ontology.dao.CaliberDao;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberFormulaValidationVO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 *   <li>{@link CaliberFormulaRejectedException} — 400 ECOS-ONTO-070/071/072/073（H-2 四关写入/审批咽喉）;</li>
 *   <li>其它 — 500 ECOS-ONTO-042（fail-closed）。</li>
 * </ul>
 */
@Component
public class CaliberService {

    private static final Logger log = LoggerFactory.getLogger(CaliberService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CaliberDao caliberDao;
    /**
     * H-2 四关校验门面（写入 / 审批咽喉复用）。{@code null} 仅出现在遗留单测的单参装配面，
     * Spring 容器恒走 {@link #CaliberService(CaliberDao, CaliberFormulaDslService)}。
     */
    private final CaliberFormulaDslService caliberFormulaDslService;

    /** 遗留装配面（无 DSL 门面 = 无闸门），仅供既有纯 Mockito 单测沿用。 */
    public CaliberService(CaliberDao caliberDao) {
        this(caliberDao, null);
    }

    @Autowired
    public CaliberService(CaliberDao caliberDao, CaliberFormulaDslService caliberFormulaDslService) {
        this.caliberDao = caliberDao;
        this.caliberFormulaDslService = caliberFormulaDslService;
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
     * <p><b>H-2 写入咽喉</b>：{@code formula} 非空时先过四关（V1 语法 / V2 引用闭合 / V3 量纲 /
     * V4 无环），任一关不过即抛 {@link CaliberFormulaRejectedException} 且<b>不落库</b>
     * （校验严格先于 DAO 写）。空 {@code formula} 裁定见
     * {@link #assertFormulaGatePassed(String, String)}。</p>
     *
     * @return 插入后的版本行
     */
    public Map<String, Object> insertVersion(String caliberId, String versionNo, String formula,
                                             Integer additive, String periodGranularity, String status) {
        String effectiveStatus = (status == null || status.isBlank()) ? "DRAFT" : status;
        assertFormulaGatePassed(formula, "insertVersion");
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
     * <p><b>H-2 审批咽喉（纵深防御）</b>：目标为 APPROVED 时，状态 UPDATE 之前重跑同一公式的
     * 四关校验 —— 库里可能存有四关生效前写入的坏公式，审批是坏公式进入计算面的最后一道闸。</p>
     *
     * @return 受影响行数（0 = 无迁移 / 已终态 / 行不存在）
     */
    public int archiveVersion(String caliberId, String versionNo, String newStatus, String approvedBy) {
        String current = caliberDao.findVersionStatus(caliberId, versionNo);
        if (current == null || current.equals(newStatus) || !isForwardTransition(current, newStatus)) {
            return 0;
        }
        if ("APPROVED".equals(newStatus)) {
            assertFormulaGatePassed(caliberDao.lookupVersionFormula(caliberId, versionNo), "approve");
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

    /**
     * H-2 四关校验咽喉（写入 {@code formula} 与审批置 APPROVED 两处共用）。
     *
     * <p><b>裁定</b>：</p>
     * <ul>
     *   <li>{@code formula} 为 {@code null} / 全空白 ⇒ <b>跳过校验</b>：无公式口径是既有合法形态
     *       （DDL 允许 formula 为空，派生口径 / 仅登记周期粒度的版本行），不算违例；</li>
     *   <li>非空 ⇒ 必须是 {@code IDENT '=' expr} 的 DSL 形态，任何非法（含旧数据里的裸表达式
     *       {@code SUM(x)}、SQL 片段、越白名单函数）一律 <b>V1 拒绝</b>，
     *       <b>禁</b>为兼容旧数据降级为 warning；</li>
     *   <li>DSL 门面未装配（遗留单参构造）⇒ 沿用旧行为不校验；Spring 容器恒装配。</li>
     * </ul>
     *
     * @param stage 触发咽喉的标识（异常消息里定位是哪一道闸）
     */
    private void assertFormulaGatePassed(String formula, String stage) {
        if (caliberFormulaDslService == null || formula == null || formula.isBlank()) {
            return;
        }
        CaliberFormulaValidationVO vo = caliberFormulaDslService.validateFormula(formula);
        if (!vo.isValid()) {
            log.warn("H-2 four-check gate rejected formula at {} stage={} violations={}",
                    stage, vo.getTarget(), vo.getViolations());
            throw new CaliberFormulaRejectedException(stage, formula, vo.getViolations());
        }
    }

    /**
     * WAgent Readiness 供数：同指标多口径冲突数。
     *
     * <p>判据：同一 caliber.code 下存在 ≥2 条 APPROVED 且未删除的版本行。</p>
     *
     * @return 冲突 caliber code 数量
     */
    public int countConflictedCaliberCodes() {
        return caliberDao.countConflictedCaliberCodes();
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

    /**
     * H-2 四关不过（公式落库 / 审批咽喉）→ 400，错误码取首条违反项的
     * {@code ECOS-ONTO-070/071/072/073}，逐条违反项经 {@link #getViolations()} 透出给调用方。
     */
    public static final class CaliberFormulaRejectedException extends RuntimeException {
        private final String code;
        private final List<CaliberFormulaValidationVO.Violation> violations;

        public CaliberFormulaRejectedException(String stage, String formula,
                                               List<CaliberFormulaValidationVO.Violation> violations) {
            super("caliber formula rejected at H-2 gate [" + stage + "]: " + violations
                    + " — formula=" + formula);
            this.code = (violations == null || violations.isEmpty())
                    ? CaliberDslValidator.CODE_SYNTAX
                    : violations.get(0).getCode();
            this.violations = violations == null ? List.of() : List.copyOf(violations);
        }

        /** 首条违反项的错误码（ECOS-ONTO-070/071/072/073）。 */
        public String getCode() {
            return code;
        }

        /** 逐条违反项（gate / code / message / 符号定位）。 */
        public List<CaliberFormulaValidationVO.Violation> getViolations() {
            return violations;
        }
    }
}
