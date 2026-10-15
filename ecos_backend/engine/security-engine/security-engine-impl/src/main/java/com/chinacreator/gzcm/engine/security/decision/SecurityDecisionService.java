package com.chinacreator.gzcm.engine.security.decision;

import com.chinacreator.gzcm.engine.security.dao.SecurityDecisionDao;
import com.chinacreator.gzcm.engine.security.rls.RlsPredicate;
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

        // ── [1] RLS：参数化谓词（占位符 + bindings，禁字符串拼接）──
        Map<String, Object> rlsRes = safeRls(table, userId);
        Map<String, Object> rlsPart = asPredicatePart(rlsRes);

        // ── [2] CLS：列投影（allow 空集 = DENY 全部列）──
        Map<String, Object> clsRes = safeCls(table, userId, asset.getColumns());
        Map<String, Object> columnsPart = asColumnsPart(clsRes);

        // ── [3] mask：敏感数据目录 → 强制 mask 列集合 ──
        List<Map<String, Object>> masks = safeMasks(asset, purpose);

        // ── [4] OPA：裁决（fail-close 内建）──
        Map<String, Object> opaPart = safeOpa(asset, p, ctx);

        String decisionId = uuid();
        String traceId = ctx != null ? ctx.traceId() : null;
        log.debug("decide: asset={} purpose={} → decisionId={} rlsTemplate={} columnsMode={} masks={} opaAllow={}",
                asset.key(), p, decisionId,
                rlsPart.get("predicateTemplate"), columnsPart.get("mode"),
                masks.size(), opaPart.get("allow"));
        return new DecisionBundle(rlsPart, columnsPart, masks, opaPart, decisionId, traceId);
    }

    // ── 四源各自的 fail-safe 隔离：某源异常不放大成整条抛错，而是保守拒绝 ──

    private Map<String, Object> safeRls(String table, String userId) {
        try {
            return rls.apply(table, userId);
        } catch (Exception e) {
            log.warn("decide RLS 源异常，按无策略 1=1 + denyIfEmpty=true 保守处理: table={}, err={}", table, e.getMessage());
            RlsPredicate p = new RlsPredicate();
            p.setPredicateTemplate("1=1");
            p.setDenyIfEmpty(true);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("predicate", p.toMap());
            r.put("params", Map.of());
            return r;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asPredicatePart(Map<String, Object> rlsRes) {
        Object pred = rlsRes.get("predicate");
        // 复制而非改源：policyRefs 是 bundle 层追加的范围证据，不写回 RlsPredicate.toMap 的语义
        Map<String, Object> part = pred instanceof Map<?, ?> p
                ? new LinkedHashMap<>((Map<String, Object>) p)
                : fallbackPredicatePart();
        part.put("policyRefs", namesOf(rlsRes.get("policies")));
        return part;
    }

    private static Map<String, Object> fallbackPredicatePart() {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("predicateTemplate", "1=1");
        fallback.put("denyIfEmpty", true);
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

    private Map<String, Object> safeCls(String table, String userId, List<String> columns) {
        try {
            return cls.getColumns(table, userId, columns);
        } catch (Exception e) {
            log.warn("decide CLS 源异常，按 fail-closed 拒绝全部列: table={}", table, e);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("visibleColumns", new ArrayList<String>());
            r.put("blockedColumns", columns == null ? new ArrayList<String>() : new ArrayList<>(columns));
            return r;
        }
    }

    private Map<String, Object> asColumnsPart(Map<String, Object> clsRes) {
        Object vis = clsRes.get("visibleColumns");
        List<?> visible = vis instanceof List<?> l ? l : new ArrayList<>();
        Map<String, Object> m = new LinkedHashMap<>();
        // C.2.3：allow 语义（返回可读列集合）；allow 空集 = 该主体不可读该资产任何列（非全放开）
        m.put("mode", "allow");
        m.put("names", visible);
        m.put("policyRefs", namesOf(clsRes.get("policies")));
        return m;
    }

    private List<Map<String, Object>> safeMasks(AssetRef asset, DecisionBundle.Purpose purpose) {
        try {
            // asset_key 形如 [schema.]table.column；本表列目录的 asset_key 前缀 = asset.key()
            String prefix = asset.key();
            List<Map<String, Object>> rows = dao.masksForTable(asset.getTable(), prefix);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("column", row.get("column"));
                mm.put("strategy", String.valueOf(row.get("strategy")));
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
     * [4] OPA 护栏源（B4-3c 名合规 + 载荷补齐）。
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
     */
    private Map<String, Object> safeOpa(AssetRef asset, DecisionBundle.Purpose purpose, DecisionContext ctx) {
        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("userId", ctx != null ? ctx.userId() : null);
        subject.put("roles", ctx == null ? List.of() : ctx.roles());
        subject.put("department", ctx != null ? ctx.orgId() : null);
        subject.put("tenantId", ctx != null ? ctx.tenantId() : null);
        String purposeWire = purpose.name().toLowerCase();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("purpose", purposeWire);
        input.put("action", "data:" + purposeWire);
        input.put("asset", asset.key());
        input.put("subject", subject);
        String policyRef = "ecos." + OpaPolicyService.POLICY_DATA_ACCESS;
        try {
            Map<String, Object> r = opa.evaluate(OpaPolicyService.POLICY_DATA_ACCESS, input);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("allow", r.getOrDefault("allow", false));
            out.put("policyState", r.getOrDefault("policyState", OpaPolicyService.STATE_UNAVAILABLE));
            out.put("policyRef", r.getOrDefault("policyRef", policyRef));
            Object ob = r.get("obligations");
            out.put("obligations", ob instanceof List<?> l ? l : new ArrayList<>());
            return out;
        } catch (Exception e) {
            // OpaPolicyService.evaluate 内部已 fail-close；这里再兜一层：绝不因 OPA 抛错而放行。
            // 状态记 UNAVAILABLE 而非 DENY —— 本分支恰恰意味着"护栏没被咨询"，禁伪装成策略裁决。
            log.warn("decide OPA 源异常，fail-close → allow=false: {}", e.getMessage());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("allow", false);
            out.put("policyState", OpaPolicyService.STATE_UNAVAILABLE);
            out.put("policyRef", policyRef);
            out.put("obligations", List.of(Map.of("obligation", "DENIED", "deniedReason", "OPA_ERROR_FAIL_CLOSED")));
            return out;
        }
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
