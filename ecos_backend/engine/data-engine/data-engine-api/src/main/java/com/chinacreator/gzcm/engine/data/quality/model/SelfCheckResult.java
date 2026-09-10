package com.chinacreator.gzcm.engine.data.quality.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 模块自检结果 VO — {@code POST /api/v1/dq/health/selfcheck} 出参（PMO-48-A T6）。
 * <p>
 * 自检策略（诚实报告范围，见 issues）：
 * <ul>
 *   <li><b>自动检查</b>：{@link #schemaPresent} / {@link #tablesPresent} / {@link #missingTables}
 *       通过 JDBC 查 PG {@code information_schema}/{@code to_regclass}；
 *       {@link #securityLoaded} 通过 {@code DqSecurityService} Bean 是否注入成功判定
 *       （能注入即说明 sec-engine 的 mask/audit 方法可用）。</li>
 *   <li><b>人工验证</b>：{@link #filtersPresent}（三滤波器生效，等价于访问 /api/v1/dq/**
 *       非 403/404）、{@link #rewriteKeep}（VersionPrefixRewriteFilter
 *       /api/v1/dq 走 KEEP）、{@link #ymlWhitelist}（application.yml
 *       auth.whitelist.paths 含 /api/v1/dq/**）——这三项已在 PMO-48-A T2 静态确认，
 *       本端点检测靠「当前请求已能到达本端点」推断，details 详见 issues 字段。</li>
 * </ul>
 * 任何探测异常都不抛出，封装进 {@link #issues}，HTTP 永远 200（对齐项目 ApiResponse 约定）。
 *
 * @author PMO-48-A T6
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SelfCheckResult {

    /** PG schema {@code ecos_dq} 是否存在（Flyway V111 建库） */
    private boolean schemaPresent;

    /** schema 下 5 张表（dq_rule/dq_rule_version/dq_rule_check/dq_alert_record/dq_work_order）是否齐备 */
    private boolean tablesPresent;

    /** 缺失的表名列表（齐备时为空） */
    private List<String> missingTables;

    /** 三滤波器（VersionPrefixRewriteFilter + SecurityConfig + ClearanceInterceptor + yml）是否生效。
     * 本 Phase 简化实现：以「当前 POST /api/v1/dq/health/selfcheck 能进入 Controller」间接证明 filter 链放行。 */
    private boolean filtersPresent;

    /** VersionPrefixRewriteFilter 中 /api/v1/dq 是否走 KEEP（即无 REMOVE 改写条目）。
     * 本 Phase 需人工确认，issues 会标注此项需手动验证 T2 已加。 */
    private boolean rewriteKeep;

    /** application.yml 的 auth.whitelist.paths 是否含 {@code /api/v1/dq/**}。
     * 本 Phase 需人工确认，issues 会标注此项需手动验证 T2 已加。 */
    private boolean ymlWhitelist;

    /** DqSecurityService Bean 是否构造成功（mask + audit 能力可用） */
    private boolean securityLoaded;

    /** 自检发现的问题列表 */
    private List<String> issues;

    /** 汇总结论：全通过 → "DQ modules ready"；否则 "DQ selfcheck: N issues found" */
    private String summary;
}
