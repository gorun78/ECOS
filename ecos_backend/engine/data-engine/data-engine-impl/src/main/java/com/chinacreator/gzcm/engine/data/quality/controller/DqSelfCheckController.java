package com.chinacreator.gzcm.engine.data.quality.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.quality.model.SelfCheckResult;
import com.chinacreator.gzcm.engine.data.quality.service.DqSecurityService;

/**
 * DQ 模块自检 REST API — {@code POST /api/v1/dq/health/selfcheck}（PMO-48-A T6）。
 *
 * <p>
 * 自检项（自动 + 人工，详见 {@link SelfCheckResult} javadoc）：
 * <ol>
 *   <li>PG schema {@code ecos_dq} 存在性（自动，JDBC）</li>
 *   <li>schema 下 5 张表 {@code dq_rule/dq_rule_version/dq_rule_check/dq_alert_record/dq_work_order}
 *       存在性（自动，{@code to_regclass} 防御性查询）</li>
 *   <li>DqSecurityService Bean 注入/mask+audit 能力（自动，ObjectProvider）</li>
 *   <li>三滤波器 / rewrite KEEP / yml whitelist（人工确认 T2，issues 标注）</li>
 * </ol>
 * 所有探测异常都不抛出，封装进 issues，HTTP 永远 200（对齐项目 ApiResponse 约定）。
 * </p>
 *
 * <p>
 * 三滤波器（铁律 1.2）：本端点路径 {@code /api/v1/dq/health/selfcheck} 已被
 * {@code /api/v1/dq/**} 通配覆盖，T2 追加的过滤配置在此无须新增。
 * </p>
 *
 * @author PMO-48-A T6
 */
@RestController
@RequestMapping("/api/v1/dq/health")
public class DqSelfCheckController {

    private static final Logger log = LoggerFactory.getLogger(DqSelfCheckController.class);

    /** 目标 schema（V111 迁移建库） */
    private static final String DQ_SCHEMA = "ecos_dq";

    /** V111 应建 5 张表 */
    private static final List<String> DQ_TABLES = List.of(
            "dq_rule", "dq_rule_version", "dq_rule_check", "dq_alert_record", "dq_work_order");

    private final JdbcTemplate jdbc;

    /** ObjectProvider 让 DqSecurityService 缺失时不阻断 Controller 启动，自检项报 false */
    private final ObjectProvider<DqSecurityService> securityServiceProvider;

    public DqSelfCheckController(JdbcTemplate jdbc,
                                 ObjectProvider<DqSecurityService> securityServiceProvider) {
        this.jdbc = jdbc;
        this.securityServiceProvider = securityServiceProvider;
    }

    /**
     * 自检入口。
     *
     * @return SelfCheckResult（HTTP 永远 200）
     */
    @PostMapping("/selfcheck")
    public ApiResponse<SelfCheckResult> selfCheck() {
        SelfCheckResult r = new SelfCheckResult();
        List<String> issues = new ArrayList<>();

        // 1. PG schema 存在性
        boolean schema = checkSchema(issues);
        r.setSchemaPresent(schema);

        // 2. 5 表存在性（依赖 schema，schema 缺失时跳过细查）
        List<String> missing = new ArrayList<>();
        if (schema) {
            missing = checkTables();
        } else {
            missing.addAll(DQ_TABLES);
            issues.add("PG schema '" + DQ_SCHEMA + "' 不存在，schema 下 5 表状态未知。确认 Flyway V111 已执行");
        }
        r.setTablesPresent(missing.isEmpty());
        r.setMissingTables(missing);
        if (!missing.isEmpty() && schema) {
            issues.add("检测到缺失表 " + missing + "，确认 V111 迁移完整");
        }

        // 3. DqSecurityService 注入（mask + audit 能力）
        boolean security = checkSecurityLoaded(issues);
        r.setSecurityLoaded(security);

        // 4. 三滤波器 / rewrite KEEP / yml whitelist（人工确认，issues 标注）
        // 当前 POST /api/v1/dq/health/selfcheck 能进入本 Controller，
        // 间接说明 T2 已让 /api/v1/dq/** 通过 filter 链；但 rewrite KEEP 与 yml 白名单
        // 两项无法在运行时稳定判定，本阶段标记为人工验证项。
        r.setFiltersPresent(true);
        r.setRewriteKeep(false);
        r.setYmlWhitelist(false);
        issues.add("rewriteKeep: VersionPrefixRewriteFilter 的 /api/v1/dq 条目需人工验证（预期 KEEP，不应再有 REMOVE 改写）");
        issues.add("ymlWhitelist: application.yml 的 auth.whitelist.paths 需人工验证 /api/v1/dq/** 已注册");

        r.setIssues(issues);
        boolean allOk = schema && missing.isEmpty() && security;
        r.setSummary(allOk
                ? "DQ modules ready (5 tables + filters + security)"
                : "DQ selfcheck: " + issues.size() + " issues found");
        return ApiResponse.success("ok", r);
    }

    // ==================== 内部探测 ====================

    /** 检查 schema 存在性。异常时记 issue 并返回 false。 */
    private boolean checkSchema(List<String> issues) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*)::int FROM information_schema.schemata WHERE schema_name = ?",
                    Integer.class, DQ_SCHEMA);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("DQ selfcheck: schema probe failed, error={}", e.getMessage());
            issues.add("PG schema 探测失败（DB 不可用?）：" + e.getMessage());
            return false;
        }
    }

    /** 检查 5 张表是否存在（to_regclass 防御性查询，缺失返回 NULL）。 */
    private List<String> checkTables() {
        List<String> missing = new ArrayList<>();
        for (String table : DQ_TABLES) {
            try {
                // to_regclass('schema.table') 返回 OID；表缺失时返回 NULL
                Long oid = jdbc.queryForObject(
                        "SELECT to_regclass(?)::int8",
                        Long.class, DQ_SCHEMA + "." + table);
                if (oid == null || oid == 0L) {
                    missing.add(table);
                }
            } catch (Exception e) {
                log.warn("DQ selfcheck: table probe failed, table={}, error={}", table, e.getMessage());
                missing.add(table);
            }
        }
        return missing;
    }

    /** 检查 DqSecurityService 是否注入。 */
    private boolean checkSecurityLoaded(List<String> issues) {
        DqSecurityService svc = securityServiceProvider.getIfAvailable();
        if (svc == null) {
            issues.add("DqSecurityService Bean 未注入，安全检查器不可用");
            return false;
        }
        // 额外探测一次 maskParameters（不耗 IO），证明 Bean 实例方法是可调用对象
        try {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("phone", "13800000000");
            Map<String, Object> masked = svc.maskParameters(sample);
            if (masked == null || !DqSecurityService.MASKED.equals(masked.get("phone"))) {
                issues.add("DqSecurityService.maskParameters 行为异常，建议复查 T3 实现");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("DQ selfcheck: securityLoaded probe failed, error={}", e.getMessage());
            issues.add("DqSecurityService 探测失败：" + e.getMessage());
            return false;
        }
    }
}
