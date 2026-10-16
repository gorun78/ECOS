package com.chinacreator.gzcm.workspace.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ABAC 查询过滤器 — 在 ObjectQL/ObjectRuntime 查询时做列裁剪和行过滤。
 *
 * <h3>H10-T1（PMO-74 N7/N16）整改</h3>
 * <p>原实现直查 {@code td_abac_policy} 表、本地解析 environment_condition JSON，
 * 并用 {@code rowFilterSql.replace("${userId}", userId)} 字符串插值（二阶注入面）。
 * 现按架构铁律 §2.4 全部裁决改经 {@link WorkspaceSecurityEngineClient} 调
 * security-engine REST（RLS {@code /api/v1/security/rls/apply} +
 * CLS {@code /api/v1/security/cls/columns}），workspace 进程不再自建 ABAC 解析。</p>
 *
 * <h3>默认 DENY</h3>
 * <ul>
 *   <li>行过滤：security-engine 明确拒绝 → {@code "1=0"}（调用方拼进 WHERE 即零行）</li>
 *   <li>列裁剪：security-engine 明确拒绝 → 可见列空集 → 返回空行集</li>
 *   <li>security-engine <b>进程不可用</b>（连不上/超时/5xx）不属上述两态：由客户端单点转
 *       503 {@code ECOS-SEC-501}（{@link com.chinacreator.gzcm.workspace.exception
 *       .SecurityEngineUnavailableException}）原样上抛，禁冒充"无数据"
 *       （详设-01 §4.5.4 / §4.7.2，门禁 {@code SecurityDownAllChannelDenyTest}）</li>
 * </ul>
 *
 * <h3>调用契约（保持不变，API 只增不改）</h3>
 * <ul>
 *   <li>{@link #buildRowFilterCondition(String)}：<b>只</b>取片段字符串，兼容旧调用方；
 *       生产者现在发的是带 {@code ?} 的片段 + 同序参数，<b>只取片段会漏绑参数</b>，
 *       需要真正下推行过滤的调用方必须改用 {@link #buildRowFilter(String)}</li>
 *   <li>{@link #buildRowFilter(String)}：返回 {@link RlsFilter}（片段与参数成对，含拒绝原因）</li>
 *   <li>{@link #filterColumns(String, List)}：返回列裁剪后的新行列表</li>
 * </ul>
 */
@Component
public class AbacQueryFilter {

    private static final Logger log = LoggerFactory.getLogger(AbacQueryFilter.class);

    private final WorkspaceSecurityEngineClient securityEngineClient;

    public AbacQueryFilter(WorkspaceSecurityEngineClient securityEngineClient) {
        this.securityEngineClient = securityEngineClient;
    }

    // ═══════════════ 公共 API ═══════════════════

    /**
     * 对查询结果做列裁剪：仅保留 security-engine CLS 裁决允许的列。
     *
     * @param entityCode 实体编码（如 "Contract"）
     * @param rows       查询结果行列表
     * @return 裁剪后的行列表（新 List，不影响原数据）；CLS 明确拒绝 → 空列表（默认 DENY）；
     *         security-engine 进程不可用 → 上抛 503 {@code ECOS-SEC-501}，禁空行集冒充
     */
    public List<Map<String, Object>> filterColumns(String entityCode, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return rows;

        List<String> allColumns = new ArrayList<>(rows.get(0).keySet());
        List<String> allowed = securityEngineClient.allowedColumns(entityCode, allColumns);
        if (allowed.isEmpty()) {
            // 明确 DENY（4xx 鉴权失败/响应不可解析同归此态）：默认拒绝，不返回任何列数据。
            // 「进程不可用」已在客户端单点转 503 上抛，不会走到本分支冒充空行集。
            log.warn("ABAC列裁剪 DENY（security-engine 明确拒绝/应答不可解析）: entity={}, rows={} 被置空",
                    entityCode, rows.size());
            return List.of();
        }

        Set<String> allowedLower = new LinkedHashSet<>();
        for (String col : allowed) {
            if (col != null) allowedLower.add(col.toLowerCase());
        }

        List<Map<String, Object>> filtered = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if (key == null || allowedLower.contains(key.toLowerCase())) {
                    cleaned.put(key, entry.getValue());
                }
            }
            filtered.add(cleaned);
        }
        log.debug("ABAC列裁剪(security-engine CLS): entity={}, allowed={}, rows={}",
                entityCode, allowed.size(), rows.size());
        return filtered;
    }

    /**
     * 构建行过滤 SQL WHERE 片段 — 经 security-engine RLS 端点裁决（片段单值兼容出口）。
     *
     * <p><b>#142/B4-3c-2 语义订正（D-B4-17）</b>：此前"应答缺 {@code whereClause} 字段"被折叠成
     * 空串＝"无过滤条件"，即页面通道行过滤恒等于不过滤（fail-open）。现在缺字段/空白一律拒绝，
     * 本方法<b>不再返回空串</b>（除调用异常），故 {@code isEmpty()} 判"无过滤"的旧写法已不成立 ——
     * 需要下推带参数片段的调用方请用 {@link #buildRowFilter(String)}。</p>
     *
     * @param entityCode 实体编码
     * @return SQL WHERE 片段；RLS 明确拒绝 / 应答形态漂移 → {@code "1=0"}；
     *         security-engine 进程不可用 → 上抛 503 {@code ECOS-SEC-501}，禁 {@code "1=0"} 冒充空列表
     */
    public String buildRowFilterCondition(String entityCode) {
        RlsFilter filter = buildRowFilter(entityCode);
        if (filter.denied()) {
            log.warn("ABAC行过滤 DENY（security-engine RLS 明确拒绝/应答形态漂移）: entity={} reason={}",
                    entityCode, filter.denyReason());
        }
        return filter.clause();
    }

    /**
     * 构建行过滤<b>执行契约</b>（片段 + 同序参数）—— security-engine {@code /rls/apply} 单源裁决
     * （#142/B4-3c-2 只增，收口 D-B4-17；本方法是 workspace 侧唯一"成对取片段与参数"的出口，
     * 禁在调用方各自解析 REST 应答形成第二套真值）。
     *
     * @param entityCode 实体编码
     * @return 永不为 {@code null} 的 {@link RlsFilter}；漂移/拒绝态已归一为 {@code 1=0} +
     *         {@code denyReason}（见 {@link RlsFilter} 构造期不变量）；
     *         security-engine 进程不可用 → 503 {@code ECOS-SEC-501} 上抛（与"策略拒绝"分态）
     */
    public RlsFilter buildRowFilter(String entityCode) {
        RlsFilter filter = securityEngineClient.applyRlsFilter(entityCode, entityCode, Map.of());
        if (filter == null) {
            // 客户端未返回裁决载体＝形态漂移（不是"无策略"），归一为拒绝态。
            // 这里不抛 NPE：500 会把内部堆栈外泄，且把一个可解释的 DENY 变成不可解释的崩溃。
            log.warn("ABAC行过滤 DENY（客户端未返回裁决载体）: entity={}", entityCode);
            return RlsFilter.deny(RlsFilter.DRIFT_ABSENT + " entity=" + entityCode
                    + "（客户端未返回裁决载体）", true, List.of());
        }
        return filter;
    }

    /**
     * 获取当前实体需要隐藏的列名集合。
     *
     * <p>H10-T1 后列级隐藏由 security-engine CLS 在 {@link #filterColumns} 内实时裁决，
     * 本方法无列全集上下文、不再返回本地策略推导结果（保留签名以兼容既有调用方）。</p>
     */
    public Set<String> getHiddenColumns(String entityCode) {
        log.debug("getHiddenColumns 已由 security-engine CLS 实时裁决取代: entity={}", entityCode);
        return Set.of();
    }

    /**
     * 三通道唯一裁决入口的 workspace 侧委托（详设-01 C.1 [3] / F01-02，#142/B4-3b 只增）。
     *
     * <p>本方法是 workspace 出<em>裁决</em>的第二条腿（第一条是 {@link #buildRowFilterCondition}
     * / {@link #filterColumns} 的分散面），刻意放在同一委托类里：workspace 侧"谁能调
     * security-engine"的清单保持单点，不新增第三个出口。导出通道需要的是
     * <b>一次拿全四源 + 范围证据</b>，分散面给不了（{@code /rls/apply} 只回谓词、
     * 不回"命中了哪条策略"），故 B4-3b 先接导出；页面/AI 两通道合流到本入口归 B4-3c
     * （差距文档 D-B4-15）。</p>
     *
     * @param entityCode 受保护资产定位符（与 {@code AssetRef.table} 同义）
     * @param purpose    通道字面量（{@code page} / {@code export} / {@code ai}）
     * @return {@code DecisionBundle} 的 REST 形态；security 明确拒绝/应答缺段 → {@code null}
     *         （调用方按 fail-closed 处理）；进程不可用 → 503 {@code ECOS-SEC-501} 上抛
     */
    public Map<String, Object> decisionBundle(String entityCode, String purpose) {
        // columns 传空 = 整表面裁决；列裁剪仍走 filterColumns/allowedColumns（bundle 列面
        // 在 columns=[] 时不可用，收口项 D-B4-16），禁在此处猜列名。
        return securityEngineClient.decide(entityCode, List.of(), purpose);
    }

    /**
     * 策略缓存清空的兼容入口 — 裁决已收敛 security-engine，本地无缓存可刷（保留签名）。
     */
    public void invalidateCache() {
        log.info("ABAC 裁决已收敛 security-engine（无本地缓存需刷新）");
    }
}
