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
 *       未被拒）、{@link #rewriteKeep}（VersionPrefixRewriteFilter
 *       /api/v1/dq 走 KEEP）、{@link #ymlWhitelist}（PMO-74 H9-T5c 更正：yml
 *       {@code auth.whitelist.paths} 键已删除，此项恒为 false，不再是「待人工确认」）。
 *       原注释所称「三项已在 PMO-48-A T2 静态确认」已失效：H9-T1 把 permitAll 收敛为 8 条、
 *       H9-T2 移出 dq 豁免 ⇒ dq 现为需认证 + L1 准入，而非匿名白名单。</li>
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

    /** 三滤波器（VersionPrefixRewriteFilter + SecurityConfig + ClearanceInterceptor）是否生效。
     * 本 Phase 简化实现：以「当前 POST /api/v1/dq/health/selfcheck 能进入 Controller」间接证明。
     * PMO-74 H9-T5c 语义更正：dq 已移出匿名面，故该间接证明成立时同时意味着调用方带了有效
     * Token 且过 L1 准入校验，不再等价于「白名单放行」。 */
    private boolean filtersPresent;

    /** VersionPrefixRewriteFilter 中 /api/v1/dq 是否走 KEEP（即无 REMOVE 改写条目）。
     * 实测：正向 V1_REWRITE_MAP 10 条无 dq，仅反向 {@code /api/dq/ → /api/v1/dq/} 一条。 */
    private boolean rewriteKeep;

    /** 历史字段名保留（出参契约不可改）：原意是「application.yml 的 auth.whitelist.paths
     * 是否含 {@code /api/v1/dq/**}」。PMO-74 H9-T5/T5b 已删除该 yml 键且全仓无存活 Java 消费方
     * ⇒ 本项<b>恒为 false</b>，{@code DqSelfCheckController} 显式置 false 并在 issues 说明。 */
    private boolean ymlWhitelist;

    /** DqSecurityService Bean 是否构造成功（mask + audit 能力可用） */
    private boolean securityLoaded;

    /** 自检发现的问题列表 */
    private List<String> issues;

    /** 汇总结论：全通过 → "DQ modules ready"；否则 "DQ selfcheck: N issues found" */
    private String summary;
}
