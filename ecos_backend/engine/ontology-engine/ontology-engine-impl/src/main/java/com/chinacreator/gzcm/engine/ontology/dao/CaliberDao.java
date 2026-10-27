package com.chinacreator.gzcm.engine.ontology.dao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/**
 * B3-a 数据访问收口（R2-B3 / 架构铁律 §2.5 #1 / IR06）：{@code CaliberService}
 * （口径主表 + 版本状态机 + Metric 口径绑定）的 JDBC 落点。
 *
 * <p>方法逐个搬运自 CaliberService，<b>只搬不改语义</b>：SQL 文本、参数序、
 * 异常→Optional 转换、动态 WHERE 拼接全部原样迁移。MC06 schema（{@code
 * ${ecos.ontology.control-schema:ecos_ontology}}）随 DAO 注入，Service 不再持有。</p>
 */
@Repository
public class CaliberDao extends JdbcTemplateDaoSupport {

    static final String COLS =
            "id, code, name, status, unit, currency, owner_role, dimension_json, "
            + "create_time, update_time, create_by, update_by, is_deleted, version_no, domain";

    private final String schema;

    public CaliberDao(
            @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.schema = schema;
    }

    /** 插入口径主表行（DRAFT/ACTIVE），主键应用侧生成（MC01）。 */
    public void insertCaliber(String id, String code, String name, String effectiveStatus,
                              String unit, String currency, String ownerRole, String dimensionJson) {
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".ecos_caliber "
                        + "(id, code, name, status, unit, currency, owner_role, dimension_json, "
                        + " create_time, update_time, is_deleted, version_no, domain) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, '1', 'default')",
                id, code, name, effectiveStatus, unit, currency, ownerRole, dimensionJson);
    }

    /** 口径列表（{@code is_deleted=0}），可选 status 过滤，按 code 排序、LIMIT。 */
    public List<Map<String, Object>> listCalibers(String status, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT " + COLS + " FROM " + schema + ".ecos_caliber WHERE is_deleted=0");
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=?");
            params.add(status);
        }
        sql.append(" ORDER BY code LIMIT ?");
        params.add(limit);
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /** 按业务编码检索口径（{@code is_deleted=0}），不存在返回 empty。 */
    public Optional<Map<String, Object>> findByCode(String code) {
        try {
            return Optional.of(jdbcTemplate.queryForMap(
                    "SELECT " + COLS + " FROM " + schema + ".ecos_caliber WHERE code=? AND is_deleted=0",
                    code));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** 口径 + 当前生效（最近 APPROVED）版本行 JOIN 查询，不存在返回 empty。 */
    public Optional<Map<String, Object>> getByCaliberCodeAndCurrentVersion(String code) {
        try {
            return Optional.of(jdbcTemplate.queryForMap(
                    "SELECT c.id AS caliber_id, c.code, c.name, c.status AS caliber_status, "
                            + "c.unit, c.currency, c.owner_role, c.dimension_json, c.domain, "
                            + "cv.id AS version_id, cv.version_no, cv.formula, cv.additive, "
                            + "cv.period_granularity, cv.status AS version_status, "
                            + "cv.approved_by, cv.approved_at, cv.git_ref "
                            + "FROM " + schema + ".ecos_caliber c "
                            + "JOIN " + schema + ".ecos_caliber_version cv "
                            + "  ON cv.caliber_id = c.id AND cv.is_deleted=0 AND cv.status='APPROVED' "
                            + "WHERE c.code=? AND c.is_deleted=0 AND c.status='ACTIVE' "
                            + "ORDER BY cv.create_time DESC, cv.version_no DESC LIMIT 1",
                    code));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** 某口径的版本行列表（{@code is_deleted=0}），可选 status 过滤。 */
    public List<Map<String, Object>> listVersionsByCaliberId(String caliberId, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, caliber_id, version_no, formula, additive, period_granularity, status, "
                        + "approved_by, approved_at, git_ref, create_time, update_time, "
                        + "create_by, update_by, is_deleted, domain "
                        + "FROM " + schema + ".ecos_caliber_version WHERE caliber_id=? AND is_deleted=0");
        java.util.List<Object> params = new java.util.ArrayList<>();
        params.add(caliberId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=?");
            params.add(status);
        }
        sql.append(" ORDER BY create_time DESC, version_no DESC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /** 插入口径版本行（行不可变），主键应用侧生成。 */
    public void insertVersion(String id, String caliberId, String versionNo, String formula,
                              Integer additive, String periodGranularity, String effectiveStatus) {
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".ecos_caliber_version "
                        + "(id, caliber_id, version_no, formula, additive, period_granularity, "
                        + " status, create_time, update_time, is_deleted, domain) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'default')",
                id, caliberId, versionNo, formula, additive, periodGranularity, effectiveStatus);
    }

    /** 按主键读回刚插入的版本行（显式列集），不存在返回 empty map。 */
    public Map<String, Object> findVersionById(String id) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT id, caliber_id, version_no, formula, additive, period_granularity, status, "
                            + "approved_by, approved_at, git_ref "
                            + "FROM " + schema + ".ecos_caliber_version WHERE id=?", id);
        } catch (EmptyResultDataAccessException e) {
            return new LinkedHashMap<>();
        }
    }

    /** 版本当前状态（用于状态机前向推进判定），不存在返回 null。 */
    public String findVersionStatus(String caliberId, String versionNo) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT status FROM " + schema + ".ecos_caliber_version "
                            + "WHERE caliber_id=? AND version_no=? AND is_deleted=0",
                    String.class, caliberId, versionNo);
        } catch (EmptyResultDataAccessException e) {
            return null; // 行不存在 → 由 Service 判定为"无迁移"
        }
    }

    /** 版本状态推进 UPDATE（status + approved_by + 时间戳，乐观状态守卫）。 */
    public int archiveVersion(String caliberId, String versionNo, String newStatus,
                              String approvedBy, String current) {
        return jdbcTemplate.update(
                "UPDATE " + schema + ".ecos_caliber_version "
                        + "SET status=?, approved_by=?, approved_at=CURRENT_TIMESTAMP, "
                        + "update_time=CURRENT_TIMESTAMP "
                        + "WHERE caliber_id=? AND version_no=? AND is_deleted=0 AND status=?",
                newStatus, approvedBy, caliberId, versionNo, current);
    }

    /** 指标绑定口径 + 版本 + 冻结快照（rule 4 / rule 3 快照写入）。 */
    public void bindMetricCaliber(String metricCode, String caliberId, String formulaVersion,
                                  String snapshot) {
        jdbcTemplate.update(
                "UPDATE " + schema + ".ecos_metric_definition "
                        + "SET caliber_id=?, formula_version=?, caliber_snapshot=?, "
                        + "update_time=CURRENT_TIMESTAMP WHERE code=? AND is_deleted=0",
                caliberId, formulaVersion, snapshot, metricCode);
    }

    /** 单独改写指标口径快照（rule 3：仅快照为空时允许）。 */
    public void updateMetricCaliberSnapshot(String metricCode, String newSnapshot) {
        jdbcTemplate.update(
                "UPDATE " + schema + ".ecos_metric_definition "
                        + "SET caliber_snapshot=?, update_time=CURRENT_TIMESTAMP "
                        + "WHERE code=? AND is_deleted=0",
                newSnapshot, metricCode);
    }

    /** 发布门禁口径校验用：读指标行的口径绑定三要素，不存在返回 null（Draft 路径）。 */
    public Map<String, Object> findMetricCaliberBinding(String metricCode) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT id, code, caliber_id, formula_version, caliber_snapshot, status "
                            + "FROM " + schema + ".ecos_metric_definition WHERE code=? AND is_deleted=0",
                    metricCode);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * WAgent Readiness 供数：统计同指标多口径冲突的 caliber code 数。
     *
     * <p>判据：同一 {@code caliber.code} 下存在 ≥2 条 {@code status='APPROVED'} AND
     * {@code is_deleted=0} 的版本行 ⇒ 冲突指标。</p>
     */
    public int countConflictedCaliberCodes() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ("
                        + "  SELECT c.code "
                        + "  FROM " + schema + ".ecos_caliber c "
                        + "  JOIN " + schema + ".ecos_caliber_version cv "
                        + "    ON cv.caliber_id = c.id AND cv.is_deleted = 0 AND cv.status = 'APPROVED' "
                        + "  WHERE c.is_deleted = 0 "
                        + "  GROUP BY c.code "
                        + "  HAVING COUNT(cv.id) > 1"
                        + ") t",
                Integer.class);
        return count == null ? 0 : count;
    }

    /** rule 3 护栏：读指标当前 caliber_snapshot，行不存在返回 null（首次绑定视为可写）。 */
    public String findMetricCaliberSnapshot(String metricCode) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT caliber_snapshot FROM " + schema + ".ecos_metric_definition "
                            + "WHERE code=? AND is_deleted=0",
                    String.class, metricCode);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 版本 formula 查询（不存在/参数空由调用方分流），不存在返回 null。 */
    public String lookupVersionFormula(String caliberId, String formulaVersion) {
        if (caliberId == null || caliberId.isBlank()) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT formula FROM " + schema + ".ecos_caliber_version "
                            + "WHERE caliber_id=? AND version_no=? AND is_deleted=0",
                    String.class, caliberId, formulaVersion);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 版本 status 查询（发布门禁 rule 4），不存在返回 null。 */
    public String lookupVersionStatus(String caliberId, String formulaVersion) {
        if (caliberId == null || caliberId.isBlank()) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT status FROM " + schema + ".ecos_caliber_version "
                            + "WHERE caliber_id=? AND version_no=? AND is_deleted=0",
                    String.class, caliberId, formulaVersion);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * H-2 DSL 四关校验（V2/V3/V4）用：指标注册表快照（R-4 唯一语义入口
     * {@code ecos_metric_definition}）。显式列清单（DR05 is_deleted=0 过滤，禁 SELECT *）。
     */
    public List<Map<String, Object>> listMetricDefinitionExpressions() {
        return jdbcTemplate.queryForList(
                "SELECT code, expression FROM " + schema + ".ecos_metric_definition WHERE is_deleted=0");
    }
}
