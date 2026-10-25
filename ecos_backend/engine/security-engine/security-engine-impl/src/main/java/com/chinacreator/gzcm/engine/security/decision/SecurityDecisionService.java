package com.chinacreator.gzcm.engine.security.decision;

import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.common.security.decision.DataScopeContract;
import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.dao.SecurityDecisionDao;
import com.chinacreator.gzcm.engine.security.rls.RlsPredicate;
import com.chinacreator.gzcm.engine.security.rls.RlsSubjectContext;
import com.chinacreator.gzcm.engine.security.service.ColumnLevelSecurityServiceImpl;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 详细设计-01 C.1 [3] / C.2 / W33 — <b>三通道唯一裁决入口</b>。
 *
 * <pre>{@code
 *   decide(AssetRef{schema,table,columns[]}, Purpose{page|export|ai}, ctx)
 *       → DecisionBundle{ rls, columns, masks, opa, decisionId, traceId }
 * }</pre>
 *
 * <p>结构性目的（F01-02/W33）：现状三通道各自零散调 RLS/CLS（S-6 只 1/3 通道），
 * 漏一个即旁路。合并成单入口后，"绕过其中一条"在架构上<b>不可表达</b> ——
 * 页面/导出/AI 全部经此方法，{@code decide} 一次拿全四源裁决。</p>
 *
 * <p>四源职责映射（各源 fail-closed 语义见各自实现）：</p>
 * <ul>
 *   <li>RLS → {@link RowLevelSecurityServiceImpl#apply}（{@code predicateTemplate + bindings}，非裸字符串）</li>
 *   <li>CLS → {@link ColumnLevelSecurityServiceImpl#getColumns}（allow 空集 = 一列不可读 = DENY）</li>
 *   <li>mask → 敏感数据目录 {@code ecos_security_asset}（COLUMN 行 guard_combo 含 mask 的列 → 强制 mask）</li>
 *   <li>OPA → {@link OpaPolicyService#evaluate}（fail-close 常量化，DENY 语义自带）</li>
 * </ul>
 *
 * <p>#142/B4-3b 只增：RLS/CLS 两源的<b>命中策略名</b>以 {@code policyRefs[]} 落进 bundle
 * （{@link DecisionBundle#scopeEvidence} 的唯一出处）。此前 bundle 只携带谓词与列集，
 * 消费方（导出件权限自证）无出处可引，只能写常量 —— 见差距文档 D-B4-10/D-B4-11。</p>
 */
@Service
public class SecurityDecisionService {

    private static final Logger log = LoggerFactory.getLogger(SecurityDecisionService.class);

    private final RowLevelSecurityServiceImpl rls;
    private final ColumnLevelSecurityServiceImpl cls;
    private final OpaPolicyService opa;
    private final SecurityDecisionDao dao;

    public SecurityDecisionService(
            @Qualifier("ecosRlsService") RowLevelSecurityServiceImpl rls,
            @Qualifier("ecosClsService") ColumnLevelSecurityServiceImpl cls,
            OpaPolicyService opa,
            SecurityDecisionDao dao) {
        this.rls = rls;
        this.cls = cls;
        this.opa = opa;
        this.dao = dao;
    }

    public DecisionBundle decide(AssetRef asset, DecisionBundle.Purpose purpose, DecisionContext ctx) {
        if (asset == null || asset.getTable() == null || asset.getTable().isBlank()) {
            throw new IllegalArgumentException("assetRef.table 不能为空");
        }
        DecisionBundle.Purpose p = purpose == null ? DecisionBundle.Purpose.PAGE : purpose;
        String table = asset.getTable();
        String userId = ctx == null ? null : ctx.userId();

        // ── [1] RLS：参数化谓词（占位符 + bindings，禁字符串拼接）+ 可绑定 WHERE 片段 ──
        Map<String, Object> rlsRes = safeRls(table, ctx);
        Map<String, Object> rlsPart = asPredicatePart(rlsRes);

        // ── [2] CLS：列投影（allow 空集 = DENY 全部列）+ 列全集出处证据（#142/B4-3d-2，D-B4-16）──
        //
        // universe（列全集）在此显式定型，不在下游猜：本入口的列全集<b>只有一个合法出处</b>
        // —— 调用方经 AssetRef.columns 显式请求。给空集就是"未解析"，不是"该主体无列权限"；
        // 二者过去在 bundle 上完全同形（names=[]），消费方无法分态（§4.5.4「不可用 ≠ 无权限」）。
        // 刻意不引入"目录兜底 universe"：ecos_security_asset 现网 0 行（D-B4-19），
        // 拿它当宇宙等于用一条不可测分支冒充单源 —— 裁决依据已登记在差距文档 v1.0.19 行。
        List<String> universe = asset.getColumns();
        String universeSource = universe.isEmpty()
                ? ColumnSurfaceContract.UNIVERSE_NONE
                : ColumnSurfaceContract.UNIVERSE_REQUEST;
        Map<String, Object> clsRes = safeCls(table, userId, universe);
        Map<String, Object> columnsPart = asColumnsPart(clsRes, universeSource);

        // ── [3] mask：敏感数据目录 → 强制 mask 列集合 ──
        List<Map<String, Object>> masks = safeMasks(asset, purpose);

        // ── [4] OPA：裁决（fail-close 内建）──
        Map<String, Object> opaPart = safeOpa(asset, p, ctx);

        String decisionId = uuid();
        String traceId = ctx != null ? ctx.traceId() : null;
        log.debug("decide: asset={} purpose={} → decisionId={} rlsTemplate={} columnsMode={} "
                        + "universeSource={} visible={} clsDenyReason={} masks={} opaAllow={}",
                asset.key(), p, decisionId,
                rlsPart.get(RlsExecutionContract.KEY_PREDICATE_TEMPLATE), columnsPart.get("mode"),
                columnsPart.get(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_SOURCE),
                columnCountOf(columnsPart), columnsPart.get(ColumnSurfaceContract.KEY_CLS_DENY_REASON),
                masks.size(), opaPart.get(GuardrailDecisionContract.KEY_ALLOW));
        return new DecisionBundle(rlsPart, columnsPart, masks, opaPart, decisionId, traceId);
    }

    // ── 四源各自的 fail-safe 隔离：某源异常不放大成整条抛错，而是保守拒绝 ──

    private Map<String, Object> safeRls(String table, DecisionContext ctx) {
        try {
            return rls.apply(table, subjectContextOf(ctx));
        } catch (Exception e) {
            log.warn("decide RLS 源异常，按无策略 1=1 + denyIfEmpty=true 保守处理: table={}, err={}", table, e.getMessage());
            RlsPredicate p = new RlsPredicate();
            p.setPredicateTemplate(RlsExecutionContract.ALLOW_ALL_CLAUSE);
            p.setDenyIfEmpty(true);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put(RlsExecutionContract.KEY_PREDICATE_SEGMENT, p.toMap());
            r.put("params", Map.of());
            // 异常态必须与正常态同形：无命中策略 + 拒绝片段，消费端读不到 whereClause
            // 时会当成"字段缺失"，那正是 D-B4-17 的 fail-open 形态，故此处显式给 1=0。
            r.put(RlsExecutionContract.KEY_WHERE_CLAUSE, RlsExecutionContract.DENY_CLAUSE);
            r.put(RlsExecutionContract.KEY_WHERE_PARAMS, List.of());
            r.put(RlsExecutionContract.KEY_NO_POLICY_MATCHED, true);
            r.put(RlsExecutionContract.KEY_RLS_DENY_REASON, "RLS_SOURCE_ERROR");
            return r;
        }
    }

    /**
     * {@link DecisionContext} → RLS 绑定解析上下文（组织域/密级只服务端派生，
     * 现网无单源时为 {@code null}，由 {@code RlsPredicateRenderer} 转"未解析 ⇒ 拒绝"）。
     */
    private static RlsSubjectContext subjectContextOf(DecisionContext ctx) {
        if (ctx == null) {
            return new RlsSubjectContext(null, null, null, null);
        }
        return new RlsSubjectContext(ctx.userId(), ctx.tenantId(), ctx.orgId(), ctx.clearanceLevel());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asPredicatePart(Map<String, Object> rlsRes) {
        Object pred = rlsRes.get(RlsExecutionContract.KEY_PREDICATE_SEGMENT);
        // 复制而非改源：policyRefs 是 bundle 层追加的范围证据，不写回 RlsPredicate.toMap 的语义
        Map<String, Object> part = pred instanceof Map<?, ?> p
                ? new LinkedHashMap<>((Map<String, Object>) p)
                : fallbackPredicatePart();
        part.put("policyRefs", namesOf(rlsRes.get("policies")));
        // #142/B4-3c-2：bundle 的 rls 段必须自带可绑定片段，否则页面/AI 通道合流到本入口后
        // 仍要回去读 /rls/apply（等于第二条裁决腿）。透传而非重算，保持单源。
        // #142/B4-3d-5（D-B4-24）：透传的键名取契约常量——bundle 的 rls 段因此与
        // /rls/apply 的 data 段<b>同键同形</b>，消费者可用同一个 RlsExecutionContract.read 读两处。
        copyIfPresent(rlsRes, part, RlsExecutionContract.KEY_WHERE_CLAUSE);
        copyIfPresent(rlsRes, part, RlsExecutionContract.KEY_WHERE_PARAMS);
        copyIfPresent(rlsRes, part, RlsExecutionContract.KEY_UNRESOLVED_BINDINGS);
        copyIfPresent(rlsRes, part, RlsExecutionContract.KEY_NO_POLICY_MATCHED);
        copyIfPresent(rlsRes, part, RlsExecutionContract.KEY_RLS_DENY_REASON);
        return part;
    }

    private static void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
        Object value = from.get(key);
        if (value != null) to.put(key, value);
    }

    private static Map<String, Object> fallbackPredicatePart() {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put(RlsExecutionContract.KEY_PREDICATE_TEMPLATE, RlsExecutionContract.ALLOW_ALL_CLAUSE);
        fallback.put(RlsExecutionContract.KEY_DENY_IF_EMPTY, true);
        // RLS 段整体缺失＝裁决无出处，给拒绝形态而非"没过滤"（D-B4-17 fail-open 的最后一处）
        fallback.put(RlsExecutionContract.KEY_WHERE_CLAUSE, RlsExecutionContract.DENY_CLAUSE);
        fallback.put(RlsExecutionContract.KEY_WHERE_PARAMS, List.of());
        fallback.put(RlsExecutionContract.KEY_NO_POLICY_MATCHED, true);
        fallback.put(RlsExecutionContract.KEY_RLS_DENY_REASON, "RLS_PREDICATE_SEGMENT_ABSENT");
        return fallback;
    }

    /**
     * RLS/CLS 源返回的命中策略清单 → 策略名列表（范围证据单源）。
     *
     * <p>两源形态不同（{@code RowLevelSecurityServiceImpl} 给对象数组、
     * {@code ColumnLevelSecurityServiceImpl} 给字符串数组），此处统一成名字；
     * <b>清单缺失返回空列表而非 null</b>，由消费方按「无命中策略 + denyIfEmpty」判 DENY，
     * 禁把"读不到出处"折叠成"出处为空所以放行"。</p>
     */
    private static List<String> namesOf(Object policies) {
        if (!(policies instanceof List<?> list)) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Object name = m.get("name") != null ? m.get("name") : m.get("policy_name");
                if (name != null && !String.valueOf(name).isBlank()) out.add(String.valueOf(name));
            } else if (o != null && !String.valueOf(o).isBlank()) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    /**
     * CLS 源调用（#142/B4-3d-2 只增：异常兜底态必须带机读理由）。
     *
     * <p>此前异常兜底只写空 {@code visibleColumns}，与"策略把可读集清空"在线上形态上同形；
     * 消费方据此只能报 403，而真相是裁决腿自身失败（§4.5.4 应属可用性/缺陷态）。
     * 现按 {@code safeRls} 的 {@code rlsDenyReason} 同一pattern 写入
     * {@link ColumnSurfaceContract#DENY_SOURCE_ERROR}，由 {@link #asColumnsPart} 归入理由优先级。</p>
     */
    private Map<String, Object> safeCls(String table, String userId, List<String> columns) {
        try {
            return cls.getColumns(table, userId, columns);
        } catch (Exception e) {
            log.warn("decide CLS 源异常，按 fail-closed 拒绝全部列: table={}", table, e);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put(ColumnSurfaceContract.KEY_VISIBLE_COLUMNS, new ArrayList<String>());
            r.put(ColumnSurfaceContract.KEY_BLOCKED_COLUMNS,
                    columns == null ? new ArrayList<String>() : new ArrayList<>(columns));
            r.put(ColumnSurfaceContract.KEY_CLS_DENY_REASON, ColumnSurfaceContract.DENY_SOURCE_ERROR);
            return r;
        }
    }

    /**
     * 列面段（{@code columns}）单源成形 —— #142/B4-3d-2 收口差距 D-B4-16。
     *
     * <p><b>缺陷本质</b>：本段此前只有 {@code mode}/{@code names} 两键，是列面的<b>第三种叫法</b>
     * （生产者叫 {@code visibleColumns}，legacy 契约册叫 {@code allowedColumns}，bundle 又叫
     * {@code names}），且 {@code mode} 恒写 {@code "allow"} —— 一个取值恒定的键等于没有键。
     * 结果是页面通道合流到本入口后仍拿不到可用的列面，只能回读 {@code /cls/columns}
     * （第二条裁决腿，差距文档 D-B4-15）。</p>
     *
     * <p><b>修法</b>：列面读数改经 {@link ColumnSurfaceContract#read(Object)}（与三消费者同源，
     * 禁在此处再写一遍键名），并把"结论怎么来的"落成三键
     * （{@code columnUniverseSource} / {@code columnUniverseResolved} / {@code clsDenyReason}）。
     * {@code mode} 改为按 {@link com.chinacreator.gzcm.engine.security.controller.ClsController}
     * GET 别名的同一口径计算（空集＝{@code deny}），{@code names} 保留为逐字节等值的兼容别名
     * —— API 只增不改：既有读 {@code names} 的用例与消费方不受影响。</p>
     *
     * <p><b>拒绝理由优先级</b>（不可叠加，取最先命中者，保证一个列面只有一个解释）：
     * 源异常 &gt; 列全集未解析 &gt; 键面漂移 &gt; 策略拒绝全部列。前两者不是"用户无权限"，
     * 消费方必须分态处理（403 vs 503/告警），故排在最前。</p>
     */
    private Map<String, Object> asColumnsPart(Map<String, Object> clsRes, String universeSource) {
        boolean universeResolved = ColumnSurfaceContract.UNIVERSE_REQUEST.equals(universeSource);
        ColumnSurfaceContract.Reading reading = ColumnSurfaceContract.read(clsRes);
        List<String> visible = reading.visible();
        List<String> blocked = reading.blocked();

        String denyReason = null;
        if (visible.isEmpty()) {
            String sourceReason = ColumnSurfaceContract.denyReasonOf(clsRes);
            if (sourceReason != null) {
                denyReason = sourceReason;
            } else if (!universeResolved) {
                denyReason = ColumnSurfaceContract.DENY_UNIVERSE_UNRESOLVED;
            } else if (reading.drifted()) {
                denyReason = reading.reason();
            } else {
                denyReason = ColumnSurfaceContract.DENY_POLICY;
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        // C.2.3：allow 语义（返回可读列集合）；allow 空集 = 该主体不可读该资产任何列（非全放开）
        m.put("mode", visible.isEmpty() ? "deny" : "allow");
        m.put("names", visible);
        m.put(ColumnSurfaceContract.KEY_VISIBLE_COLUMNS, visible);
        m.put(ColumnSurfaceContract.KEY_BLOCKED_COLUMNS, blocked);
        m.put("policyRefs", namesOf(clsRes.get("policies")));
        m.put(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_SOURCE, universeSource);
        m.put(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_RESOLVED, universeResolved);
        if (denyReason != null) m.put(ColumnSurfaceContract.KEY_CLS_DENY_REASON, denyReason);
        return m;
    }

    /** 日志专用：列面段可读列数（形态异常时记 0，禁因日志而抛错）。 */
    private static int columnCountOf(Map<String, Object> columnsPart) {
        Object raw = columnsPart.get(ColumnSurfaceContract.KEY_VISIBLE_COLUMNS);
        return raw instanceof List<?> list ? list.size() : 0;
    }

    private List<Map<String, Object>> safeMasks(AssetRef asset, DecisionBundle.Purpose purpose) {
        try {
            // asset_key 形如 [schema.]table.column；本表列目录的 asset_key 前缀 = asset.key()
            String prefix = asset.key();
            List<Map<String, Object>> rows = dao.masksForTable(asset.getTable(), prefix);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> mm = new LinkedHashMap<>();
                // 写的是裁决线上的 mask 义务形态（键面单源）；row 侧同名键属 DAO 结果集别名面，不混用。
                mm.put(GuardrailDecisionContract.KEY_MASK_COLUMN, row.get("column"));
                mm.put(GuardrailDecisionContract.KEY_MASK_STRATEGY, String.valueOf(row.get("strategy")));
                out.add(mm);
            }
            // 只返回目录中显式登记 mask 的列，不做全表放大（无登记 = 无强制脱敏）
            return out;
        } catch (Exception e) {
            log.warn("decide mask 目录读取异常，返回空 mask 集（不放大权限）: table={}, err={}",
                    asset.getTable(), e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * [4] OPA 护栏源（B4-3c 名合规 + 载荷补齐 + #142/B4-3d-3 数据域腿，D-B4-20）。
     *
     * <p><b>策略名取单源常量</b> {@link OpaPolicyService#POLICY_DATA_ACCESS}：此前写
     * {@code "data.access"}，含点号即不过 {@code SAFE_NAME} 白名单，校验在 try 之外抛出 ⇒
     * <b>OPA 从未被咨询</b>，三通道每次都拿到异常兜底（差距文档 D-B4-14/D-B4-18）。
     * 名字改对之后，护栏态才第一次承载真实语义，{@code policyState} 也因此成为
     * 「策略拒绝(403/DENY)」与「护栏未评估(503/FAIL_CLOSED)」分态的唯一依据。</p>
     *
     * <p><b>载荷必须带角色与动作</b>：rego 侧的角色×通道矩阵（§4.7.2）读
     * {@code input.subject.roles / subject.department / action / purpose}；此前只发
     * subjectId/tenantId ⇒ "按角色裁决"在结构上不可表达（D-B4-22）。角色与组织域都取自
     * {@link DecisionContext}（分册 00 信任链：客户端 X-Org-Id 已被剥离，orgId 只服务端派生）。</p>
     *
     * <p><b>#142/B4-3d-3 只增：AI 请求数据域（{@code input.resource.dataScope}）</b>。
     * §4.7.2 的 AI 列（项目负责人 p1 域内 ALLOW ／ p2 越域 DENY、未声明域
     * {@code AI_SCOPE_NOT_DECLARED}）完全由该段表达，而本入口此前<b>从不写</b> {@code resource}
     * ⇒ AI 列语义在"三通道唯一入口"上不可表达，带 dataScope 的验收例只能绕道护栏源直连
     * （差距文档 v1.0.16 行登记的那条裁决）。现按 {@link DataScopeContract} 单源写出：
     * 未声明时<b>整段不写</b>（页面/导出通道的载荷逐字节不变，既有 9 例回放的线路钉不受影响），
     * 声明时键序与录制器同形（oracle 可比性钉按 JSON 文本深比）。</p>
     *
     * <p><b>组织域归一为 {@code ""} 而不是 {@code null}</b>：rego 的
     * {@code object.get(..., "department", "")} 默认值只在<b>键缺失</b>时生效，写了
     * {@code null} 就读成 {@code null} —— 于是「主体组织域未解析」不会命中
     * {@code AI_SUBJECT_DEPARTMENT_UNRESOLVED}，而落到「声明域 ≠ 主体域」的越权规则：
     * 结果同为 DENY，但理由是伪造的，运维会去查越权而不是去查上下文解析（D-B4-23 的真实形态）。
     * 该语义差已由 dev 容器 {@code /opa eval} 离线只读求值实测（差距文档 v1.0.20 行凭证）。</p>
     */
    private Map<String, Object> safeOpa(AssetRef asset, DecisionBundle.Purpose purpose, DecisionContext ctx) {
        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("userId", ctx != null ? ctx.userId() : null);
        subject.put("roles", ctx == null ? List.of() : ctx.roles());
        subject.put("department", DataScopeContract.textOrEmpty(ctx == null ? null : ctx.orgId()));
        subject.put("tenantId", ctx != null ? ctx.tenantId() : null);
        String purposeWire = DecisionChannelContract.normalize(purpose.name());
        if (purposeWire == null) {
            // 枚举名与通道词表失配＝生产侧漂移（新增 Purpose 却忘了词表 / 词表改名）。
            // 此处必须抛错而非发 null：rego 收到 null purpose 会走"不匹配任何通道"分支，
            // 那等于把一次配置错误伪装成一次策略拒绝（D-B4-27 同源风险的镜像面）。
            throw new IllegalArgumentException("DecisionBundle.Purpose 与通道词表失配: " + purpose
                    + "，合法取值=" + DecisionChannelContract.known());
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("purpose", purposeWire);
        input.put("action", "data:" + purposeWire);
        input.put("asset", asset.key());
        input.put("subject", subject);
        // 调用方声明的请求域（AI 通道）：未声明即不写，禁写空段冒充"声明了空域"
        Map<String, Object> resource = DataScopeContract.toResourceSegment(
                ctx == null ? null : ctx.dataScope());
        if (resource != null) {
            input.put(DataScopeContract.KEY_RESOURCE, resource);
        }
        // #142/B4-3e-78 只增（D-B4-30）：AI 主裁决腿并入 /decision 后的通道推断面 +
        // SQL 结构派生值。仅 purpose==AI 且键存在时写出——页面/导出通道的载荷
        // 逐字节不变（三键全未携带）。键面取 DecisionChannelContract 单源。
        // environment 段只承 channel（策略侧 object.get 链据此判 agent_channel）；
        // 未携带时整段不写，禁写空对象冒充"声明过环境"。
        if (purpose == DecisionBundle.Purpose.AI && ctx != null) {
            if (ctx.environmentChannel() != null) {
                Map<String, Object> environment = new LinkedHashMap<>();
                environment.put(DecisionChannelContract.KEY_CHANNEL, ctx.environmentChannel());
                input.put(DecisionChannelContract.KEY_ENVIRONMENT, environment);
            }
            if (ctx.tables() != null) {
                input.put(DecisionChannelContract.KEY_TABLES, ctx.tables());
            }
            if (ctx.sqlDigest() != null) {
                input.put(DecisionChannelContract.KEY_SQL_DIGEST, ctx.sqlDigest());
            }
        }
        String policyRef = "ecos." + OpaPolicyService.POLICY_DATA_ACCESS;
        try {
            Map<String, Object> r = opa.evaluate(OpaPolicyService.POLICY_DATA_ACCESS, input);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put(GuardrailDecisionContract.KEY_ALLOW,
                    r.getOrDefault(GuardrailDecisionContract.KEY_ALLOW, false));
            out.put(GuardrailDecisionContract.KEY_POLICY_STATE,
                    r.getOrDefault(GuardrailDecisionContract.KEY_POLICY_STATE,
                            OpaPolicyService.STATE_UNAVAILABLE));
            out.put(GuardrailDecisionContract.KEY_POLICY_REF,
                    r.getOrDefault(GuardrailDecisionContract.KEY_POLICY_REF, policyRef));
            Object ob = r.get(GuardrailDecisionContract.KEY_OBLIGATIONS);
            out.put(GuardrailDecisionContract.KEY_OBLIGATIONS, ob instanceof List<?> l ? l : new ArrayList<>());
            return out;
        } catch (Exception e) {
            // OpaPolicyService.evaluate 内部已 fail-close；这里再兜一层：绝不因 OPA 抛错而放行。
            // 状态记 UNAVAILABLE 而非 DENY —— 本分支恰恰意味着"护栏没被咨询"，禁伪装成策略裁决。
            log.warn("decide OPA 源异常，fail-close → allow=false: {}", e.getMessage());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put(GuardrailDecisionContract.KEY_ALLOW, false);
            out.put(GuardrailDecisionContract.KEY_POLICY_STATE, OpaPolicyService.STATE_UNAVAILABLE);
            out.put(GuardrailDecisionContract.KEY_POLICY_REF, policyRef);
            out.put(GuardrailDecisionContract.KEY_OBLIGATIONS, List.of(Map.of(
                    GuardrailDecisionContract.KEY_OBLIGATION, GuardrailDecisionContract.OBLIGATION_DENIED,
                    GuardrailDecisionContract.KEY_DENIED_REASON, "OPA_ERROR_FAIL_CLOSED")));
            return out;
        }
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
