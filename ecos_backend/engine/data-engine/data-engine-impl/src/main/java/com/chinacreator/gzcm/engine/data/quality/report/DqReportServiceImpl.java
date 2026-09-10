package com.chinacreator.gzcm.engine.data.quality.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.engine.data.quality.DqReportService;
import com.chinacreator.gzcm.engine.data.quality.model.DqReportQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqReportVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;
import com.chinacreator.gzcm.engine.data.quality.service.DqSecurityService;

/**
 * DQ 报告服务实现（PMO-48-D T15）。
 *
 * <p>核心能力：</p>
 * <ol>
 *   <li>{@link #generateDaily} / {@link #generateWeekly} / {@link #generateMonthly}
 *       — 读 {@code dq_rule_check} + {@code dq_alert_record} + {@code dq_score_asset} +
 *       {@code dq_score_snapshot}（6 维分），
 *       加载 HTML 模板 {@code templates/dq_report_base.html} 并注入实体数据渲染，
 *       写 {@code ecos_dq.dq_report} 行。</li>
 *   <li>{@link #listReports} — 分页过滤（reportType / scope）。</li>
 *   <li>{@link #getReportHtml} — 查单条 + async audit。</li>
 *   <li>{@link #getReportPdf} — 本 Task 不实现（501）。</li>
 * </ol>
 *
 * <p><b>HTML 渲染策略</b>：pom 未引 thymeleaf starter（Phase 4 禁止魔改全局配置），
 * 降级为轻量级 {@code ${key}} 占位替换 + 行内拼接，等同于 ThymeleafLight。
 * 数据源：ClassPath {@code templates/dq_report_base.html}。</p>
 *
 * <p><b>LLM 摘要 stub</b>：{@code "该时段 DQ 健康度：overall=X.XX（数据行 N，告警 M，待 LLM 接入）"}，
 * Phase 5 接 cognitive-engine (DqLlmBlock) 后替换为真实摘要。</p>
 *
 * <p>安全卡（铁律 2.4 #5）：生成 / 下载打异步审计
 * （{@code DQ_REPORT_GENERATE} / {@code DQ_REPORT_READ}）。
 * SQL 列扫描仅限 {@code ecos_dq.dq_report / dq_rule_check / dq_alert_record /
 * dq_score_asset / dq_score_snapshot}（不跨引擎 — 铁律 2.4 数据层边界）。</p>
 *
 * <p>MinIO 调用点：{@code MinioReportStorage#putReport} 已注入，
 * <b>Phase 4 内嵌 HTML (~50KB) 直接存 PG TEXT，不上传 MinIO</b>（PDF 大文件 Phase 5 才走对象存储）。
 * 这里保留为注释运行断点供 verifier grep。</p>
 *
 * <p>Bean {@code ecosDqReportService}（铁律 1.3 防多 Bean 冲突）。</p>
 *
 * @author PMO-48-D T15
 */
@Service("ecosDqReportService")
public class DqReportServiceImpl implements DqReportService {

    private static final Logger log = LoggerFactory.getLogger(DqReportServiceImpl.class);

    /** 审计 action 前缀 */
    private static final String ACTION_GENERATE = "DQ_REPORT_GENERATE";
    private static final String ACTION_READ = "DQ_REPORT_READ";
    private static final String ACTION_DOWNLOAD = "DQ_REPORT_DOWNLOAD";

    /** 对象 key 紧凑日期（20260910） */
    private static final DateTimeFormatter DATE_COMPACT = DateTimeFormatter.BASIC_ISO_DATE;

    /** HTML 体积阈值（超过则同步镜像到 MinIO） */
    private static final int MINIO_UPLOAD_THRESHOLD = 200 * 1024; // 200KB

    /** HTML 模板 classpath */
    private static final String TEMPLATE_PATH = "templates/dq_report_base.html";

    /** 6 维度固定顺序（显示 + 权重判定） */
    private static final String[] DIMENSION_ORDER = {
            "COMPLETENESS", "ACCURACY", "CONSISTENCY",
            "FRESHNESS", "UNIQUENESS", "VALIDITY"
    };

    /** 各维度权重（读 V112 已落 dq_score_snapshot.weight 兜底） */
    private static final Map<String, Integer> DIMENSION_WEIGHT = Map.of(
            "COMPLETENESS", 30, "ACCURACY", 25, "CONSISTENCY", 15,
            "FRESHNESS", 5, "UNIQUENESS", 15, "VALIDITY", 10
    );

    private final DqSecurityService securityService;
    private final MinioReportStorage reportStorage;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    /** HTML 模板缓存（ClassPath 只读，第一次读后置入 volatile） */
    private volatile String htmlTemplate;

    public DqReportServiceImpl(DqSecurityService securityService,
                               MinioReportStorage reportStorage,
                               ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.securityService = securityService;
        this.reportStorage = reportStorage;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    // ==================== 1. 三种报告生成 ====================

    @Override
    public DqReportVO generateDaily(String scope) {
        return doGenerate("DAILY", normalizeScope(scope), 1);
    }

    @Override
    public DqReportVO generateWeekly(String scope) {
        return doGenerate("WEEKLY", normalizeScope(scope), 7);
    }

    @Override
    public DqReportVO generateMonthly(String scope) {
        return doGenerate("MONTHLY", normalizeScope(scope), 31);
    }

    // ==================== 2. 列表 / 详情 ====================

    @Override
    public PageResult<DqReportVO> listReports(DqReportQuery query) {
        DqReportQuery q = (query != null) ? query : new DqReportQuery();
        int pageNum = (q.getPageNum() != null && q.getPageNum() > 0) ? q.getPageNum() : 1;
        int pageSize = (q.getPageSize() != null && q.getPageSize() > 0) ? Math.min(q.getPageSize(), 200) : 20;
        JdbcTemplate jdbc = jdbc();

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (q.getReportType() != null && !q.getReportType().isBlank()) {
            where.append(" AND report_type = ? ");
            params.add(q.getReportType().toUpperCase());
        }
        if (q.getScope() != null && !q.getScope().isBlank()) {
            where.append(" AND scope = ? ");
            params.add(q.getScope());
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_dq.dq_report" + where, Long.class, params.toArray());
        long safeTotal = total == null ? 0L : total;

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNum - 1) * pageSize);
        List<DqReportVO> rows = jdbc.query(
                "SELECT id, report_type, scope, start_date, end_date, pdf_object_key," +
                "       llm_summary, row_count, alert_count, avg_score, created_by, created_at" +
                "  FROM ecos_dq.dq_report" + where +
                " ORDER BY end_date DESC, created_at DESC LIMIT ? OFFSET ?",
                this::mapRowBrief, pageParams.toArray());

        // 审计：列表读
        securityService.auditRead(ACTION_READ, null);

        PageResult<DqReportVO> page = new PageResult<>();
        page.setData(rows);
        page.setTotal(safeTotal);
        page.setPageNum(pageNum);
        page.setPageSize(pageSize);
        return page;
    }

    @Override
    public DqReportVO getReportHtml(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException("报告 ID 不能为空");
        }
        JdbcTemplate jdbc = jdbc();
        List<DqReportVO> rows = jdbc.query(
                "SELECT id, report_type, scope, start_date, end_date, payload_html," +
                "       pdf_object_key, llm_summary, row_count, alert_count, avg_score," +
                "       created_by, created_at" +
                "  FROM ecos_dq.dq_report WHERE id = ?",
                this::mapRowFull, id);
        if (rows.isEmpty()) {
            throw new NotFoundException("DQ 报告 " + id + " 不存在");
        }
        securityService.auditRead(ACTION_READ, id);
        return rows.get(0);
    }

    @Override
    public byte[] getReportPdf(String id) {
        // Phase 5 接 ThymeleafPDF 插件后实现；本 Task 先打下载审计
        securityService.auditRead(ACTION_DOWNLOAD, id);
        log.info("DqReport PDF download (PMO-48-D T15 stub): id={}", id);
        throw new BusinessException("PDF 导出在 Phase 5 接入，当前仅支持 HTML");
    }

    // ==================== 3. 私有工具 ====================

    private JdbcTemplate jdbc() {
        JdbcTemplate tpl = jdbcTemplateProvider.getIfAvailable();
        if (tpl == null) {
            throw new BusinessException("JdbcTemplate 不可用，无法执行 DQ 报告查询");
        }
        return tpl;
    }

    /**
     * 统一生成入口（3 种周期共用）。
     *
     * @param reportType   DAILY / WEEKLY / MONTHLY
     * @param scope        规范化后 scope
     * @param lookbackDays 回看天数（DAILY=1 / WEEKLY=7 / MONTHLY=31）
     */
    private DqReportVO doGenerate(String reportType, String scope, int lookbackDays) {
        JdbcTemplate jdbc = jdbc();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(lookbackDays - 1L);
        LocalDateTime startTs = start.atStartOfDay();
        LocalDateTime endTs = end.plusDays(1).atStartOfDay(); // [start, end+1) 左闭右开

        // Step 1: 聚合数据（不跨引擎，不重 SQL）
        int rowCount = countRuleChecks(jdbc, startTs, endTs);
        int alertCount = countAlerts(jdbc, startTs, endTs);
        Double avgScore = avgScore(jdbc);
        Map<String, Double> scoreByDim = latestDimScores(jdbc, endTs);
        double health = avgScore != null ? avgScore : 0.0;

        // Step 2: LLM 摘要 stub
        String llmSummary = String.format(
                "该时段 DQ 健康度：overall=%.2f（检查数据行 %d 行、告警 %d 条，待 LLM 接入）",
                health, rowCount, alertCount);

        // Step 3: 生成对象 key（stub，本 Task 不写 MinIO；HTML 大文件走 4a 镜像）
        String objectKey = "dq-reports/" + reportType.toLowerCase() + "_" + safeScopeKey(scope)
                + "_" + end.format(DATE_COMPACT) + ".html";

        // Step 4: 渲染 HTML 模板
        String html = renderTemplate(reportType, scope, start, end, health,
                rowCount, alertCount, llmSummary, scoreByDim);

        // Step 4a: 大 HTML (> 200KB) 走 MinIO 镜像
        byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
        if (htmlBytes.length > MINIO_UPLOAD_THRESHOLD) {
            String uploadedKey = reportStorage.putReport(objectKey, htmlBytes, "text/html");
            if (uploadedKey != null) {
                objectKey = uploadedKey;
            }
        }

        // Step 5: 落 dq_report（HTML 内嵌 TEXT，不写 MinIO）
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO ecos_dq.dq_report" +
                " (id, report_type, scope, start_date, end_date, payload_html," +
                "  pdf_object_key, llm_summary, row_count, alert_count, avg_score, created_by, created_at)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'system', NOW())",
                id, reportType, scope,
                java.sql.Date.valueOf(start), java.sql.Date.valueOf(end), html,
                objectKey, llmSummary, rowCount, alertCount, health);

        // Step 6: 审计
        securityService.auditWrite(ACTION_GENERATE, id, "SUCCESS");
        log.info("DqReport generated: id={}, type={}, scope={}, start={}, end={}, rows={}, alerts={}, health={}",
                id, reportType, scope, start, end, rowCount, alertCount, health);

        DqReportVO vo = new DqReportVO();
        vo.setId(id);
        vo.setReportType(reportType);
        vo.setScope(scope);
        vo.setStartDate(start);
        vo.setEndDate(end);
        vo.setPayloadHtml(html);
        vo.setPdfObjectKey(objectKey);
        vo.setLlmSummary(llmSummary);
        vo.setRowCount(rowCount);
        vo.setAlertCount(alertCount);
        vo.setAvgScore(health);
        vo.setCreatedBy("system");
        vo.setCreatedAt(LocalDateTime.now());
        return vo;
    }

    /** 规范化 scope：默认 "ALL"，DOMAIN:xxx 保持 */
    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "ALL";
        }
        String s = scope.trim().toUpperCase();
        if ("ALL".equals(s) || "NATIVE".equals(s) || "OFI".equals(s)) {
            return s;
        }
        if (s.startsWith("DOMAIN:")) {
            return s;
        }
        return "ALL";
    }

    /** 用 [A-Za-z0-9_] 过滤 scope，防止 object key 注入斜杠 */
    private String safeScopeKey(String scope) {
        if (scope == null) { return "ALL"; }
        return scope.replaceAll("[^A-Za-z0-9_]", "_");
    }

    /** 规则检查行数 — SUM(total_rows)（不在范围就 0） */
    private int countRuleChecks(JdbcTemplate jdbc, LocalDateTime startTs, LocalDateTime endTs) {
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(total_rows), 0) FROM ecos_dq.dq_rule_check" +
                    " WHERE executed_at >= ? AND executed_at < ?",
                    Integer.class, startTs, endTs);
            return n == null ? 0 : n;
        } catch (Exception e) {
            log.warn("DqReport countRuleChecks failed: {}", e.getMessage());
            return 0;
        }
    }

    /** 告警数 */
    private int countAlerts(JdbcTemplate jdbc, LocalDateTime startTs, LocalDateTime endTs) {
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_dq.dq_alert_record" +
                    " WHERE created_at >= ? AND created_at < ?",
                    Integer.class, startTs, endTs);
            return n == null ? 0 : n;
        } catch (Exception e) {
            log.warn("DqReport countAlerts failed: {}", e.getMessage());
            return 0;
        }
    }

    /** 系统平均分 */
    private Double avgScore(JdbcTemplate jdbc) {
        try {
            Double d = jdbc.queryForObject(
                    "SELECT AVG(rolled_score) FROM ecos_dq.dq_score_asset", Double.class);
            return d;
        } catch (Exception e) {
            log.warn("DqReport avgScore failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 取最近被评估的 6 维分值（每个维度只取 evaluated_at 最大的一条）。
     *
     * <p>SQL：dq_score_snapshot（scope_type='SYSTEM'）按 dimension 分组取最近值。
     * 系统级维度（scope_type 空值→任意 TYPE）便于报告。
     * 找不到数据时返空 Map，模板内显示 N/A 行。</p>
     */
    private Map<String, Double> latestDimScores(JdbcTemplate jdbc, LocalDateTime endTs) {
        Map<String, Double> map = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT s.dimension, s.score_value FROM ecos_dq.dq_score_snapshot s" +
                    " WHERE (s.dimension, s.evaluated_at) IN (" +
                    "   SELECT dimension, MAX(evaluated_at) FROM ecos_dq.dq_score_snapshot" +
                    "     WHERE evaluated_at <= ? GROUP BY dimension" +
                    " );",
                    endTs);
            for (Map<String, Object> r : rows) {
                Object dimObj = r.get("dimension");
                Object scoreObj = r.get("score_value");
                if (dimObj == null || scoreObj == null) {
                    continue;
                }
                String dim = String.valueOf(dimObj).toUpperCase();
                try {
                    map.put(dim, Double.parseDouble(String.valueOf(scoreObj)));
                } catch (NumberFormatException ignored) {
                    // 非数字分数跳过
                }
            }
        } catch (Exception e) {
            log.warn("DqReport latestDimScores skipped: {}", e.getMessage());
        }
        return map;
    }

    /** 加载 HTML 模板（单 classpath 一次读取，volatile 缓存） */
    private String loadTemplate() {
        if (htmlTemplate != null) {
            return htmlTemplate;
        }
        try {
            ClassPathResource res = new ClassPathResource(TEMPLATE_PATH);
            return new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("DqReport loadTemplate failed: {}", e.getMessage());
            throw new UncheckedIOException("DQ 报告模板加载失败：" + TEMPLATE_PATH, e);
        }
    }

    /** 渲染：展开占位符（${key} = value，一次 pass），表格行动态拼接 */
    private String renderTemplate(String reportType, String scope, LocalDate startDate, LocalDate endDate,
                                  double health, int rowCount, int alertCount, String llmSummary,
                                  Map<String, Double> scores) {
        String tpl = loadTemplate();

        double pct = health * 100.0;
        String grade = gradeOf(health);
        String healthClass = gradeOfCheckroom(grade);
        String failClass = alertCount >= 10 ? "bad" : (alertCount >= 3 ? "warn" : "");
        String aliases = String.valueOf(Math.round(pct));

        StringBuilder dimRows = new StringBuilder();
        for (String dim : DIMENSION_ORDER) {
            Double v = scores.get(dim);
            String vStr = (v == null) ? "N/A" : String.format("%.2f", v);
            int weight = DIMENSION_WEIGHT.getOrDefault(dim, 10);
            String dimGrade = (v == null) ? "-" : gradeOf(v);
            String dimClass = "grade-" + dimGrade;
            dimRows.append("                <tr>")
                    .append("<td>").append(escapeHtml(dim)).append("</td>")
                    .append("<td>").append(vStr).append("</td>")
                    .append("<td><span class=\"tag ").append(dimClass).append("\">").append(dimGrade).append("</span></td>")
                    .append("<td>").append(weight).append("%</td>")
                    .append("</tr>\n");
        }

        // 告警分级占位（Phase 5 有分类统计后填回）
        String alertRows = "                <tr><td>P0-P3 union</td><td>" + alertCount + "</td><td>100%</td></tr>\n";

        String generatedAt = LocalDateTime.now().toString().replace('T', ' ');
        String result = tpl
                .replace("${reportType}", escapeHtml(reportType))
                .replace("${scope}", escapeHtml(scope))
                .replace("${startDate}", escapeHtml(startDate.toString()))
                .replace("${endDate}", escapeHtml(endDate.toString()))
                .replace("${generatedAt}", escapeHtml(generatedAt))
                .replace("${createdBy}", escapeHtml("system"))
                .replace("${healthPct}", aliases)
                .replace("${healthClass}", healthClass)
                .replace("${rowCount}", String.valueOf(rowCount))
                .replace("${alertCount}", String.valueOf(alertCount))
                .replace("${failClass}", failClass)
                .replace("${grade}", escapeHtml(grade))
                .replace("${llmSummary}", escapeHtml(llmSummary))
                .replace("${dimensionRows}", dimRows.toString())
                .replace("${alertLevelRows}", alertRows);
        return result;
    }

    /** 等级判定（对齐 DqDimension#gradeOf） */
    private static String gradeOf(double value) {
        if (value >= 0.95) { return "A"; }
        if (value >= 0.85) { return "B"; }
        if (value >= 0.70) { return "C"; }
        if (value >= 0.50) { return "D"; }
        return "F";
    }

    private static String gradeOfCheckroom(String grade) {
        switch (grade) {
            case "A": case "B": return "good";
            case "C": return "warn";
            default: return "bad";
        }
    }

    private static String escapeHtml(String input) {
        if (input == null) { return ""; }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** 列表 mapRow（不含 payload_html） */
    private DqReportVO mapRowBrief(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        DqReportVO vo = new DqReportVO();
        vo.setId(rs.getString("id"));
        vo.setReportType(rs.getString("report_type"));
        vo.setScope(rs.getString("scope"));
        java.sql.Date sd = rs.getDate("start_date");
        if (sd != null) { vo.setStartDate(sd.toLocalDate()); }
        java.sql.Date ed = rs.getDate("end_date");
        if (ed != null) { vo.setEndDate(ed.toLocalDate()); }
        vo.setPdfObjectKey(rs.getString("pdf_object_key"));
        vo.setLlmSummary(rs.getString("llm_summary"));
        vo.setRowCount(rs.getInt("row_count"));
        if (rs.wasNull()) { vo.setRowCount(0); }
        vo.setAlertCount(rs.getInt("alert_count"));
        if (rs.wasNull()) { vo.setAlertCount(0); }
        double as = rs.getDouble("avg_score");
        if (!rs.wasNull()) { vo.setAvgScore(as); }
        vo.setCreatedBy(rs.getString("created_by"));
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) { vo.setCreatedAt(ts.toLocalDateTime()); }
        return vo;
    }

    /** 详情 mapRow（含 payload_html） */
    private DqReportVO mapRowFull(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        DqReportVO vo = mapRowBrief(rs, rowNum);
        vo.setPayloadHtml(rs.getString("payload_html"));
        return vo;
    }
}
