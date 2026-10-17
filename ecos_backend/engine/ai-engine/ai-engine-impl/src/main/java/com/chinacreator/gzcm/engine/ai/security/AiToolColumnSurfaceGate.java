package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AI 通道<b>列面消费闸</b> —— 工具结果回 LLM 之前的最后一道列裁剪（详设-01 C.2.3
 * 「消费：QueryExecutor 投影裁剪 + 导出列白名单 + AI 工具结果字段裁剪（三处同源）」，
 * #142/B4-3d-6 收口差距 <b>D-B4-28</b>）。
 *
 * <h3>缺陷本体（为什么"接线"而不是"改读数"）</h3>
 * <p>{@code AiSecurityEngineClient#allowedColumns} 在 B4-3d-1 修好键面之后仍然
 * <b>零生产调用方</b>（仓根实证：只命中自身与 {@code AiClsColumnSurfaceTest}），
 * {@code SecurityCheckerNode} 恒写 {@code setClsColumns(new LinkedHashMap<>())} ——
 * 即 §4.7.2 AI 列的"可见列"在生产形态下根本不产出。键面读对了而没人读，效果与键面读错
 * 完全一致（列面恒不生效），所以本批的工作量在<b>接线</b>与<b>证据面</b>，不在读数位。</p>
 *
 * <h3>列全集从哪来（裁决登记，差距文档 v1.0.23 行）</h3>
 * <p>AI 通道唯一"有真实数据行"的腿是工具执行结果（{@code ToolExecutorService#executeSql}
 * 返回 {@code {"rows":[{col:val}], "count":n}}），宇宙＝结果行的实际列键，
 * 与页面通道（workspace {@code AbacQueryFilter}）同一取宇宙口径。
 * OAG 管道没有数据腿（{@code KnowledgeRetrieverNode} 的 {@code knowledgeChunks} 是常量桩，
 * 零行零列），因此<b>不给它造宇宙</b>，而是由本类的证据面口径写"未判定"
 * （{@link ColumnSurfaceContract#notEvaluatedFace(String)}）——
 * 拿一个凭空造的列清单去裁决，等于用一条不可测分支冒充单源（同 D-B4-19 的 CATALOG 判定）。</p>
 *
 * <h3>表资产基数（本类唯一自判的形态问题，禁把不可归因的裁剪当成功）</h3>
 * <ul>
 *   <li>0 表 ⇒ {@link Verdict#NOT_APPLICABLE}：非 SQL 工具（REST/场景）结果没有表资产可归属，
 *       不裁决、不阻断（该缺口作为"AI 通道列面覆盖面"的一半登记为新差距，见 v1.0.23 行）；</li>
 *   <li>1 表 ⇒ 交 {@code /decision} 裁决并分态消费；</li>
 *   <li>≥2 表（join）⇒ {@link Verdict#FAIL_CLOSED}：结果列到表的归属在本进程内不可推导，
 *       猜一张表去裁决等于<b>用错主体检出放行集</b>（fail-open 的新形态），
 *       宁丢弃结果也不猜（与 {@code RlsExecutionContract} 的"无绑参通道宁 DENY 不猜值"同一取向）。</li>
 * </ul>
 *
 * <h3>三态不折叠</h3>
 * <p>态的判定顺序在单源 {@link ColumnSurfaceContract#decide(Object)}，本类只做
 * "态 → 咽喉分类"的映射（详设-01 §4.5.4 + F06-02 要点 2）：</p>
 * <ul>
 *   <li>{@link Verdict#TRIM} —— 列面已判定且可读集非空，按 {@link Screen#allowed()} 裁剪；</li>
 *   <li>{@link Verdict#DENIED} —— {@code CLS_POLICY_DENY} 是<b>策略结论</b>，
 *       映射 403 {@code GUARDRAIL_DENIED}（禁重试、禁降级返回）；</li>
 *   <li>{@link Verdict#FAIL_CLOSED} —— 源异常 / 宇宙未解析 / 键面漂移 / 应答缺段 /
 *       表资产不可归因 / 客户端未装配 / 传输故障：都是"没有可用结论"，
 *       映射 503 {@code GUARDRAIL_FAIL_CLOSED}。禁与 {@link Verdict#DENIED} 折叠 ——
 *       把"护栏没答"报成"用户无权限"会让一次 security 故障在审计里变成授权问题（D-B4-26 同族）。</li>
 * </ul>
 *
 * <p>算法边界（铁律 §2.4-1/2/3）：本类只做"取宇宙 → 委托 security → 按返回集筛键"，
 * 不含任何列权限推导；策略语义只在 security-engine 侧。</p>
 */
@Component
public class AiToolColumnSurfaceGate {

    private static final Logger log = LoggerFactory.getLogger(AiToolColumnSurfaceGate.class);

    private final AiSecurityEngineClient securityClient;

    public AiToolColumnSurfaceGate(AiSecurityEngineClient securityClient) {
        this.securityClient = securityClient;
    }

    /** 列面闸四态。 */
    public enum Verdict {
        /** 无表资产可归属（非 SQL 工具），本层不裁决、不阻断。 */
        NOT_APPLICABLE,
        /** 列面已判定，按 {@code allowed} 裁剪后放行。 */
        TRIM,
        /** 策略明确拒绝该主体读此资产的任何列（403 面）。 */
        DENIED,
        /** 没有可用结论（不可用/形态/未判定/不可归因），整条结果丢弃（503 面）。 */
        FAIL_CLOSED
    }

    /**
     * 表资产基数判定结果。
     *
     * <p>由 SQL 表引用提取器（{@code AgentToolPolicyGate#controlledTables}，与本地地板
     * 同一个提取器，禁第二套解析）产出，随裁决中间结果一路带到阶段 4 ——
     * 这样"能不能裁剪、该问哪张表"在 dispatch 之前就已定形，结果回传段不再回头重解析 SQL。</p>
     */
    public enum AssetKind {
        /** 无表资产（非 SQL 工具或提取为零）。 */
        NONE,
        /** 恰好一张受控表 ⇒ 唯一可裁决形态。 */
        SINGLE,
        /** 两张及以上（join）⇒ 列到表不可归因，必须 fail-closed。 */
        AMBIGUOUS
    }

    /**
     * 表资产（不可变值对象）。
     *
     * @param kind   基数态
     * @param table  {@link AssetKind#SINGLE} 时的表名；其余态为 {@code null}
     * @param tables 提取到的全部表名（观测与日志用，永不为 {@code null}）
     */
    public record Asset(AssetKind kind, String table, Set<String> tables) {

        /** 基数归一：0 表＝NONE，1 表＝SINGLE，≥2 表＝AMBIGUOUS。 */
        public static Asset of(Collection<String> tables) {
            Set<String> copy = new LinkedHashSet<>();
            if (tables != null) {
                for (String t : tables) {
                    if (t != null && !t.isBlank()) copy.add(t.trim());
                }
            }
            if (copy.isEmpty()) return new Asset(AssetKind.NONE, null, copy);
            if (copy.size() == 1) {
                return new Asset(AssetKind.SINGLE, copy.iterator().next(), copy);
            }
            return new Asset(AssetKind.AMBIGUOUS, null, copy);
        }

        /** 阶段 4 是否需要走列面闸（含 AMBIGUOUS：不可归因也要拦，不能静默跳过）。 */
        public boolean requiresScreening() {
            return kind == AssetKind.SINGLE || kind == AssetKind.AMBIGUOUS;
        }
    }

    /**
     * 列面闸输出。
     *
     * @param verdict 四态
     * @param allowed {@link Verdict#TRIM} 时的可读列集（小写归一）；其余态为空集
     * @param reason  机读理由（单源理由码或本类常量）；只入日志与台账，禁随响应体上浮（X-49）
     * @param table   被裁决的表（观测用）
     */
    public record Screen(Verdict verdict, Set<String> allowed, String reason, String table) {

        /** 咽喉判据：非 {@link Verdict#TRIM} 也非 {@link Verdict#NOT_APPLICABLE} 即阻断。 */
        public boolean blocks() {
            return verdict == Verdict.DENIED || verdict == Verdict.FAIL_CLOSED;
        }

        /** 策略结论（403 面）。 */
        public boolean denied() {
            return verdict == Verdict.DENIED;
        }

        /** 本列是否允许出现在回传结果里；{@link Verdict#NOT_APPLICABLE} 时不裁剪（原列全留）。 */
        public boolean permits(String column) {
            if (verdict != Verdict.TRIM) return verdict == Verdict.NOT_APPLICABLE;
            return column != null && allowed.contains(column.toLowerCase(Locale.ROOT));
        }

        static Screen notApplicable(String table) {
            return new Screen(Verdict.NOT_APPLICABLE, Set.of(), REASON_ASSET_ABSENT, table);
        }

        static Screen trim(Set<String> allowed, String table) {
            return new Screen(Verdict.TRIM, allowed, null, table);
        }

        static Screen denied(String reason, String table) {
            return new Screen(Verdict.DENIED, Set.of(), reason, table);
        }

        static Screen failClosed(String reason, String table) {
            return new Screen(Verdict.FAIL_CLOSED, Set.of(), reason, table);
        }
    }

    // ── 已知态串（单点定义，供分态断言与日志引用） ──

    /** 无表资产（非 SQL 工具），本层不裁决 —— 覆盖面缺口见差距文档 v1.0.23 行。 */
    public static final String REASON_ASSET_ABSENT = "AI_TOOL_COLUMN_ASSET_NOT_APPLICABLE";

    /** 结果行没有列键 ⇒ 宇宙为空，无法裁决（调用侧缺陷态，不占 security 的拒绝语义）。 */
    public static final String REASON_UNIVERSE_EMPTY = "AI_TOOL_COLUMN_UNIVERSE_EMPTY";

    /** ≥2 张受控表（join）⇒ 列到表归属不可推导，宁丢弃不猜。 */
    public static final String REASON_ASSET_AMBIGUOUS = "AI_TOOL_COLUMN_ASSET_AMBIGUOUS";

    /**
     * 列面执行器（{@code ToolObligationApplier}）未装配 ⇒ 与限流腿同一"未接线即红"口径。
     * 常量在本类持有、由 {@code ToolExecutorService} 引用（禁调用点自写文案），
     * 因为本闸的客户端是构造期必需依赖，"未装配"只可能发生在咽喉侧。
     */
    public static final String REASON_GATE_UNWIRED = "AI_TOOL_COLUMN_GATE_UNWIRED";

    /** security 传输/应答故障（含 ECOS-SEC-501 信封）⇒ 不可用态，禁折成"策略拒绝"。 */
    public static final String REASON_SOURCE_UNAVAILABLE = "AI_TOOL_COLUMN_SOURCE_UNAVAILABLE";

    /**
     * 裁剪后所有行都空了（结果宇宙与可读集不相交）⇒ 执行侧形态异常。
     *
     * <p>这不是"策略允许了零列"（那已由 {@code ColumnSurfaceContract} 的
     * {@code POLICY_DENY} 态表达），而是"策略给了非空可读集、实际结果一列都不沾"，
     * 两种情况的处置不同：前者是拒绝（403），后者说明结果集与裁决资产不匹配，
     * 按空结果返回会让调用方把一条执行链故障读成"用户确实没数据"。
     * 故本态归 503 面（{@code GUARDRAIL_FAIL_CLOSED}），由
     * {@code ToolObligationApplier#trimRows} 发出。</p>
     */
    public static final String REASON_SURFACE_DISJOINT = "AI_TOOL_COLUMN_SURFACE_DISJOINT";

    /**
     * 咽喉调用面：对单张工具结果表做列面裁决。
     *
     * @param toolKey 工具标识（仅日志用）
     * @param asset   表资产基数（{@link Asset#of(Collection)} 归一后的值）
     * @param universe 结果行的实际列键并集（空集＝{@link #REASON_UNIVERSE_EMPTY}）
     * @param userId  <b>服务端解析</b>的裁决主体（禁取 LLM 自报入参）
     */
    public Screen screen(String toolKey, Asset asset, List<String> universe, String userId) {
        if (asset == null || asset.kind() == AssetKind.NONE) {
            // 非 SQL 工具：没有表资产就没有可裁决的列面。这里刻意"放行不裁"，
            // 但把态写进日志与 Screen —— 覆盖面缺口靠证据面暴露，不靠假装拦了。
            log.debug("H10 列面闸 不适用（无表资产）: tool={} reason={}", toolKey, REASON_ASSET_ABSENT);
            return Screen.notApplicable(null);
        }
        if (asset.kind() == AssetKind.AMBIGUOUS) {
            log.warn("H10 列面闸 FAIL_CLOSED（受控表 {} 张，列到表归属不可推导）: tool={} tables={} traceId={}",
                    asset.tables().size(), toolKey, asset.tables(), AiTraceSupport.currentTraceId());
            return Screen.failClosed(REASON_ASSET_AMBIGUOUS + ": " + asset.tables(), asset.table());
        }
        if (universe == null || universe.isEmpty()) {
            log.warn("H10 列面闸 FAIL_CLOSED（结果行无列键，未发起裁决）: tool={} table={}",
                    toolKey, asset.table());
            return Screen.failClosed(REASON_UNIVERSE_EMPTY, asset.table());
        }
        ColumnSurfaceContract.Surface surface;
        try {
            surface = securityClient.columnSurface(userId, asset.table(), universe);
        } catch (Exception e) {
            // 传输/应答故障保持独立态（§4.5.4「不可用 ≠ 无权限」），禁学旧
            // allowedColumns 把它折成空集＝冒充策略拒绝
            log.warn("H10 列面闸 FAIL_CLOSED（security 不可用）: tool={} table={} reason={} detail={}",
                    toolKey, asset.table(), REASON_SOURCE_UNAVAILABLE, e.getMessage());
            return Screen.failClosed(REASON_SOURCE_UNAVAILABLE + ": " + e.getMessage(), asset.table());
        }

        switch (surface.state()) {
            case TRIMMED: {
                Set<String> allowed = new LinkedHashSet<>();
                for (String col : surface.visible()) {
                    if (col != null) allowed.add(col.toLowerCase(Locale.ROOT));
                }
                log.debug("H10 列面闸 TRIM: tool={} table={} allowed={} blocked={}",
                        toolKey, asset.table(), allowed.size(), surface.blocked().size());
                return Screen.trim(allowed, asset.table());
            }
            case POLICY_DENY:
                log.info("H10 列面闸 DENIED（策略结论）: tool={} table={} reason={}",
                        toolKey, asset.table(), surface.reason());
                return Screen.denied(surface.reason(), asset.table());
            case SOURCE_ERROR:
            case UNIVERSE_UNRESOLVED:
            case SURFACE_DRIFT:
            case SEGMENT_ABSENT:
            default:
                log.warn("H10 列面闸 FAIL_CLOSED（state={}，非策略结论）: tool={} table={} reason={}",
                        surface.state(), toolKey, asset.table(), surface.reason());
                return Screen.failClosed(surface.state() + ":" + surface.reason(), asset.table());
        }
    }
}
