package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
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
 * 现按架构铁律 §2.4 全部裁决改经 {@link WorkspaceSecurityEngineClient} 调 security-engine REST，
 * workspace 进程不再自建 ABAC 解析。</p>
 *
 * <h3>裁决腿现状（#142/B4-3d-2 订正，差距 D-B4-15 / D-B4-16）</h3>
 * <ul>
 *   <li>列裁剪 {@link #filterColumns}：<b>{@code /api/v1/security/decision}（{@code purpose=page}）
 *       单腿</b> —— 页面通道已合流三通道唯一入口，不再另调 {@code /cls/columns}；</li>
 *   <li>导出 {@link #decisionBundle}：同一入口（{@code purpose=export}）；</li>
 *   <li>行过滤 {@link #buildRowFilter}：仍走 {@code /rls/apply}（bundle 的 rls 段已自带可执行片段，
 *       合流归 B4-3d 后续子批 —— 本批不动行面，避免一次改两条腿导致回归面不可定位）。</li>
 * </ul>
 *
 * <h3>默认 DENY</h3>
 * <ul>
 *   <li>行过滤：security-engine 明确拒绝 → {@code "1=0"}（调用方拼进 WHERE 即零行）</li>
 *   <li>列裁剪：裁决束缺失 / 列面漂移 / 列全集未解析 / 可读集为空 → 返回空行集（各自带理由进日志）</li>
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
     * 对查询结果做列裁剪 —— <b>页面通道合流三通道唯一裁决入口</b>
     * （详设-01 C.1 [3] / F01-02，#142/B4-3d-2 收口差距 D-B4-15 / D-B4-16）。
     *
     * <p><b>为什么改这一条腿</b>：本方法此前调 {@code /cls/columns}，而导出通道调
     * {@code /decision}，同一个主体在同一张表上的列面由<b>两个端点</b>各答一次
     * （"绕过其中一条在架构上不可表达"的前提是先只有一条）。bundle 的列面在 B4-3d-2 之前
     * 又不可用（{@code mode} 恒 "allow"、{@code names} 是列面第三种叫法、无拒绝理由），
     * 所以合流必须先修 bundle —— 两件事同批做，否则合流就是又一次假收口。</p>
     *
     * <p><b>分态分面（§4.5.4「不可用 ≠ 无权限」在页面通道的落地）</b>：
     * 传输故障由客户端单点转 503 上抛（不改）；裁决束缺失 / 列面键面漂移 / CLS 源异常 /
     * 列全集未解析 / 可读集为空 五态一律返回空行集（fail-closed），
     * 但<b>各自带不同理由进日志</b> —— 前四种是缺陷/形态态，最后一种才是策略结论。
     * 禁把这些态折叠成同一条"无可见列"日志（那正是 D-B4-16 的病灶）。
     * 态的判定顺序是单源 {@link ColumnSurfaceContract#decide(Object)}，本类只做日志分派
     * （#142/B4-3d-6，差距 D-B4-28 同族的"手抄第 N 套"预防项）。</p>
     *
     * @param entityCode 实体编码（如 "Contract"）
     * @param rows       查询结果行列表
     * @return 裁剪后的行列表（新 List，不影响原数据）；列面不可用 → 空列表（默认 DENY）；
     *         security-engine 进程不可用 → 上抛 503 {@code ECOS-SEC-501}，禁空行集冒充
     */
    public List<Map<String, Object>> filterColumns(String entityCode, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return rows;

        List<String> allColumns = new ArrayList<>(rows.get(0).keySet());
        if (allColumns.isEmpty()) {
            // 宇宙为空＝无可裁剪对象，也不发起裁决：让 security 侧的 COLUMN_UNIVERSE_UNRESOLVED
            // 分支去回应"一行里连列名都没有"的请求，等于用一条拒绝掩盖调用侧缺陷。
            log.warn("ABAC列裁剪 DENY（结果行无列键，未发起裁决）: entity={}, rows={}", entityCode, rows.size());
            return List.of();
        }

        Map<String, Object> bundle = securityEngineClient.decide(
                entityCode, allColumns, DecisionChannelContract.PURPOSE_PAGE);
        // 四态分类单源化（#142/B4-3d-6，差距 D-B4-28 同族）：本方法此前手抄
        // read→drifted→universeUnresolved→visible.isEmpty 四段 if，是列面处置顺序的
        // 唯一一份实现；AI 通道接线时若照抄就是第二套真值。顺序本体已收进
        // ColumnSurfaceContract.decide，本类只保留"每态各自一条理由日志"的分派。
        ColumnSurfaceContract.Surface surface = ColumnSurfaceContract.decide(
                bundle == null ? null : bundle.get(DecisionChannelContract.KEY_BUNDLE_COLUMNS));
        switch (surface.state()) {
            case SEGMENT_ABSENT -> {
                // 裁决束/列面段缺失（应答无 data 段或列面非 Map）＝裁决无出处，与"策略拒绝"分态。
                log.warn("ABAC列裁剪 DENY（页面通道裁决束缺失，非策略结论）: entity={}, rows={} 被置空",
                        entityCode, rows.size());
                return List.of();
            }
            case SURFACE_DRIFT -> {
                log.warn("ABAC列裁剪 DENY（bundle 列面键面漂移，非策略结论）: entity={} reason={}",
                        entityCode, surface.reason());
                return List.of();
            }
            case UNIVERSE_UNRESOLVED -> {
                log.warn("ABAC列裁剪 DENY（列全集未解析，裁决未判定列面）: entity={} clsDenyReason={}",
                        entityCode, surface.reason());
                return List.of();
            }
            case SOURCE_ERROR -> {
                log.warn("ABAC列裁剪 DENY（CLS 源异常，裁决未得出结论）: entity={} clsDenyReason={}",
                        entityCode, surface.reason());
                return List.of();
            }
            case POLICY_DENY -> {
                // 明确 DENY（策略清空可读集）：默认拒绝，不返回任何列数据。
                // 「进程不可用」已在客户端单点转 503 上抛，不会走到本分支冒充空行集。
                log.warn("ABAC列裁剪 DENY（security-engine 明确拒绝）: entity={}, rows={} 被置空 reason={}",
                        entityCode, rows.size(), surface.reason());
                return List.of();
            }
            case TRIMMED -> {
                // 继续走下面的裁剪
            }
        }

        Set<String> allowedLower = new LinkedHashSet<>();
        for (String col : surface.visible()) {
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
        log.debug("ABAC列裁剪(security-engine /decision purpose={}): entity={}, allowed={}, blocked={}, rows={}",
                DecisionChannelContract.PURPOSE_PAGE, entityCode, surface.visible().size(),
                surface.blocked().size(), rows.size());
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
     * <p>本方法是"整表面裁决 + 范围证据"的出口（导出件需要 {@code rls.policyRefs} /
     * {@code columns.policyRefs} / 水印义务，分散面给不了）。workspace 侧"谁能调
     * security-engine"的清单保持单点，不新增第三个出口。</p>
     *
     * <p><b>#142/B4-3d-2 订正（D-B4-15 / D-B4-16 收口进度）</b>：原文记载"页面/AI 两通道合流
     * 归 B4-3c"未成立（B4-3c 只做了 AI 侧的护栏名合规与具名用例）。本批页面通道的列面已合流经
     * {@link #filterColumns} 走 {@code /decision}；仍留在分散面上的是<b>行过滤</b>
     * （{@link #buildRowFilter} 走 {@code /rls/apply}）与 <b>AI 通道</b>（归 B4-3d-3）。
     * 本方法刻意保持 {@code columns=[]}（整表面裁决）：列裁剪请用 {@link #filterColumns}，
     * 它会把结果行的实际列键作为 universe 一起送裁决 —— 本入口拿不到行数据，
     * 在此猜列名会造出第二个宇宙源。</p>
     *
     * @param entityCode 受保护资产定位符（与 {@code AssetRef.table} 同义）
     * @param purpose    通道字面量，取值只认 {@link DecisionChannelContract}
     *                   （{@code page} / {@code export} / {@code ai}）
     * @return {@code DecisionBundle} 的 REST 形态；security 明确拒绝/应答缺段 → {@code null}
     *         （调用方按 fail-closed 处理）；进程不可用 → 503 {@code ECOS-SEC-501} 上抛；
     *         未识别的 purpose ⇒ security 返回 400，由客户端归入"无裁决证据"的 {@code null} 态
     *         （D-B4-27：调用方词面失配不再被静默当成页面通道）
     */
    public Map<String, Object> decisionBundle(String entityCode, String purpose) {
        return securityEngineClient.decide(entityCode, List.of(), purpose);
    }

    /**
     * 策略缓存清空的兼容入口 — 裁决已收敛 security-engine，本地无缓存可刷（保留签名）。
     */
    public void invalidateCache() {
        log.info("ABAC 裁决已收敛 security-engine（无本地缓存需刷新）");
    }
}
