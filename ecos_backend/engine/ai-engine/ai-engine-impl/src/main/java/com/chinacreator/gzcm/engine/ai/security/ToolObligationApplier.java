package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * obligations 执行器（详细设计-06 F06-02 要点 2）：在工具结果<b>回 LLM 之前</b>
 * 对 ALLOW 裁决返回的 {@code mask[]} / {@code rowFilter} 逐条落实，并在<b>最末一步</b>
 * 施加 CLS 列面裁剪（#142/B4-3d-6 接线，收口差距 <b>D-B4-28</b>）。
 *
 * <p>铁律 §2.4-1/2/3 的硬约束：<b>算法只在 security 侧</b>。本类只做四件事 ——
 * 拆结果集、按列/按行调 security 端点、按表调列面端点、把 security 返回值写回结果结构。
 * 类内不得出现任何脱敏/行过滤/列权限推导（正则、字符替换、谓词求值、列名白名单推导），
 * 验收 {@code ObligationsApplyTest#engineDoesNotSelfImplementMasking}。</p>
 *
 * <p><b>为什么列面裁剪排在最后（详设-01 C.2.4 的顺序是推导出来的，不是随手放的）</b>：
 * 该条要求「OPA obligations 若含 mask 指令则优先级高于 CLS 允许集」。若先裁列再脱敏，
 * 一次"策略只放行 {@code id} 列"的裁决会让 {@code phone} 的 mask 义务<b>找不到目标列</b>
 * ——义务被自己的前置步骤掏空，审计上仍记"已脱敏"，实际是假执行。
 * 顺序固定为 rowFilter → mask → <b>列面裁剪＝回 LLM 前最后一道</b>。</p>
 *
 * <p>fail-closed 取向（F06-02 要点 2「禁先返回再说明」）：</p>
 * <ul>
 *   <li>security 不可用 / 响应不合契约 → 整条结果丢弃，返回 {@link Outcome#failClosed}；</li>
 *   <li>结果文本不可解析成行结构而义务又确实要求处理 → 丢弃（不能假设"没有敏感列"）；</li>
 *   <li>行数超 {@link #MAX_FILTER_ROWS} → 丢弃（逐行裁决超预算时不得"抽样过滤后放行"）；</li>
 *   <li>{@code mask} 项缺规则名 → 丢弃（无规则名即无从调 security 脱敏端点）；</li>
 *   <li>列面策略明确拒绝 → {@link Outcome#denied}（403 面，与不可用的 503 面分派，
 *       禁折叠：把"护栏没答"报成"用户无权限"会把一次故障写成一次授权问题）。</li>
 * </ul>
 */
@Component
public class ToolObligationApplier {

    private static final Logger log = LoggerFactory.getLogger(ToolObligationApplier.class);

    /** 逐行 security 裁决的行数上限；超限按 fail-closed 丢弃，禁抽样放行。 */
    public static final int MAX_FILTER_ROWS = 200;

    private final AiSecurityEngineClient securityClient;
    private final ObjectMapper objectMapper;
    private final AiToolColumnSurfaceGate columnSurfaceGate;

    public ToolObligationApplier(AiSecurityEngineClient securityClient, ObjectMapper objectMapper,
                                 AiToolColumnSurfaceGate columnSurfaceGate) {
        this.securityClient = securityClient;
        this.objectMapper = objectMapper;
        this.columnSurfaceGate = columnSurfaceGate;
    }

    /**
     * 执行结果。
     *
     * @param applied    义务是否已落实（无义务时也为 true，json 原样返回）
     * @param json       落实义务后的结果文本；applied=false 时为 null（调用方必须丢弃）
     * @param failReason fail-closed 或策略拒绝的原因；applied=true 时为 null
     * @param filtered   行过滤后保留行数；未做行过滤时为 -1
     * @param denied     是否为<b>策略结论</b>（列面明确拒绝）—— 仅在 {@code applied=false} 时有意义，
     *                   调用方据此走 403 {@code GUARDRAIL_DENIED} 而非 503
     *                   {@code GUARDRAIL_FAIL_CLOSED}（§4.5.4 分态）
     */
    public record Outcome(boolean applied, String json, String failReason, int filtered,
                          boolean denied) {

        static Outcome passThrough(String json) {
            return new Outcome(true, json, null, -1, false);
        }

        static Outcome applied(String json, int filtered) {
            return new Outcome(true, json, null, filtered, false);
        }

        static Outcome failClosed(String reason) {
            return new Outcome(false, null, reason, -1, false);
        }

        /** 列面策略拒绝（不可重试的授权结论，与"执行不可用"必须分派）。 */
        static Outcome denied(String reason) {
            return new Outcome(false, null, reason, -1, true);
        }
    }

    /**
     * 对工具结果施加 obligations 与列面裁剪。
     *
     * @param toolKey      工具标识（仅日志用）
     * @param resultJson   工具原始结果文本
     * @param obligations  ALLOW 裁决携带的义务
     * @param subject      服务端解析的裁决主体（行过滤需要 userRole，禁取入参自报值）
     * @param asset        表资产基数（{@link AiToolColumnSurfaceGate.Asset}，由裁决阶段
     *                     的 SQL 表引用单源提取器带过来；{@code null}＝按无表资产处理）
     */
    public Outcome apply(String toolKey, String resultJson, ToolObligations obligations,
                         ToolAdjudicationRequest.Subject subject,
                         AiToolColumnSurfaceGate.Asset asset) {
        if (resultJson == null || resultJson.isBlank()) {
            return Outcome.passThrough(resultJson);
        }
        // 阶段 4 只落实<b>结果回传面</b>的列/行义务（mask/rowFilter）与 CLS 列面裁剪。
        // 限流义务（D-B4-21）已计进 {@code isEmpty()}，但它属阶段 2.5「这次调用能否发起」，
        // 在 dispatch 前就已消费 —— 绝不能在此把"仅限流"读成"有待落实的结果义务"：
        // 否则一条结果恰好非行结构的正常调用，会因被强行解释成行结构而 fail-closed
        // （B4-3d-4 已登记过的本批引入新回归形态，#157 收口）。
        boolean hasRowOrColumnObligations = obligations != null
                && (!obligations.mask().isEmpty()
                        || (obligations.rowFilter() != null && !obligations.rowFilter().isBlank()));
        boolean needsColumnScreen = asset != null && asset.requiresScreening();
        if (!hasRowOrColumnObligations && !needsColumnScreen) {
            return Outcome.passThrough(resultJson);
        }
        List<ToolObligations.Mask> masks = obligations == null ? List.of() : obligations.mask();
        String rowFilter = obligations == null ? null : obligations.rowFilter();
        if (asset != null && asset.kind() == AiToolColumnSurfaceGate.AssetKind.AMBIGUOUS) {
            // 多表 join：有没有行/列义务都不等于没有列泄露。列到表归属不可推导时必须拦下（宁丢不猜），
            // 且拦在解析/脱敏<b>之前</b> —— 否则同一缺陷会因义务有无而报出不同理由，分态断言无法钉死。
            return Outcome.failClosed(AiToolColumnSurfaceGate.REASON_ASSET_AMBIGUOUS
                    + ": " + asset.tables());
        }

        Object root;
        try {
            root = objectMapper.readValue(resultJson, Object.class);
        } catch (Exception e) {
            // 无论哪条腿需要解释行结构，取不出宇宙/列值就只能丢弃（fail-closed，禁"假设没有敏感列"）
            return Outcome.failClosed(hasRowOrColumnObligations
                    ? "结果不可解析为行结构，无法执行脱敏/行过滤义务"
                    : "结果不可解析为行结构，无法执行 CLS 列面裁决");
        }
        List<Map<String, Object>> rows = asRows(root);
        if (rows == null) {
            return Outcome.failClosed("结果不含可过滤的行结构（对象数组/rows 数组）");
        }

        int kept = -1;
        try {
            if (rowFilter != null && !rowFilter.isBlank()) {
                if (rows.size() > MAX_FILTER_ROWS) {
                    return Outcome.failClosed(
                            "行数 " + rows.size() + " 超逐行裁决上限 " + MAX_FILTER_ROWS + "，丢弃结果");
                }
                rows = filterRows(toolKey, rows, rowFilter, subject);
                kept = rows.size();
            }
            for (ToolObligations.Mask mask : masks) {
                if (mask.rule() == null || mask.rule().isBlank()) {
                    return Outcome.failClosed("mask 义务缺少规则名，无法交 security 执行: column=" + mask.column());
                }
                maskColumn(toolKey, rows, mask);
            }
            // ── 最后一道：CLS 列面裁剪（回 LLM 之前） ──
            Outcome screened = applyColumnSurface(toolKey, rows, asset, subject);
            if (screened != null) {
                return screened;
            }
        } catch (Exception e) {
            log.warn("H10 obligations 执行失败（fail-closed，整条结果丢弃）: tool={} reason={}",
                    toolKey, e.getMessage(), e);
            return Outcome.failClosed("security obligations 执行不可用: " + e.getMessage());
        }

        Object rebuilt = rebuild(root, rows);
        try {
            return Outcome.applied(objectMapper.writeValueAsString(rebuilt), kept);
        } catch (Exception e) {
            return Outcome.failClosed("义务执行后结果重新序列化失败: " + e.getMessage());
        }
    }

    /**
     * 列面裁决的执行位（本类唯一接触"哪些列可以回传"的地方，语义全部在 security 侧）。
     *
     * @return {@code null}＝本层无阻断（不适用或已就地裁剪完）；非空＝调用方必须丢弃整条结果
     */
    private Outcome applyColumnSurface(String toolKey, List<Map<String, Object>> rows,
                                       AiToolColumnSurfaceGate.Asset asset,
                                       ToolAdjudicationRequest.Subject subject) {
        if (asset == null || asset.kind() == AiToolColumnSurfaceGate.AssetKind.NONE) {
            return null;
        }
        // 本类三个依赖都是构造期必需（Spring 无法把 null 注进必需构造参数），
        // 因此这里不设"执行器自身缺失"分支 —— 那条分支不可测即不可信。
        // "列面腿未接线"的真实可达形态在咽喉：obligationApplier 是 @Autowired(required=false)
        // 的 setter 注入，未装配由 ToolExecutorService 用 REASON_GATE_UNWIRED 归 503。
        List<String> universe = universeOf(rows);
        String userId = subject == null ? null : subject.userId();
        AiToolColumnSurfaceGate.Screen screen =
                columnSurfaceGate.screen(toolKey, asset, universe, userId);
        switch (screen.verdict()) {
            case NOT_APPLICABLE:
                return null;
            case DENIED:
                return Outcome.denied("CLS 列面策略拒绝: " + screen.reason());
            case FAIL_CLOSED:
                return Outcome.failClosed("CLS 列面不可用: " + screen.reason());
            case TRIM:
            default:
                break;
        }
        int remaining = trimRows(rows, screen);
        if (!universe.isEmpty() && remaining == 0) {
            // 宇宙非空却一列都不剩＝列面与结果列不相交（别名/大小写/资产定位符错配一类形态漂移）。
            // 返回"每行都是空对象"看起来像成功、实际什么都没交付，还把缺陷藏进 200；
            // 按 fail-closed 丢弃并把失配信息留在理由里（审计可见，响应体不外泄原文）。
            return Outcome.failClosed(AiToolColumnSurfaceGate.REASON_SURFACE_DISJOINT
                    + ": resultColumns=" + universe.size() + " allowed=" + screen.allowed().size()
                    + " table=" + screen.table());
        }
        return null;
    }

    /** 结果行的实际列键并集（列宇宙的唯一来源，禁在此之外猜列名）。 */
    private static List<String> universeOf(List<Map<String, Object>> rows) {
        Set<String> universe = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            if (row == null) continue;
            for (String key : row.keySet()) {
                if (key != null && !key.isBlank()) universe.add(key);
            }
        }
        return new ArrayList<>(universe);
    }

    /**
     * 按可读列集就地裁剪。
     *
     * @return 至少剩一列的行数（用于"全部行都被裁空"的失配判定）
     */
    private static int trimRows(List<Map<String, Object>> rows, AiToolColumnSurfaceGate.Screen screen) {
        int rowsWithColumns = 0;
        for (Map<String, Object> row : rows) {
            if (row == null) continue;
            row.keySet().removeIf(key -> !screen.permits(key));
            if (!row.isEmpty()) rowsWithColumns++;
        }
        return rowsWithColumns;
    }

    /** 逐行交 security evaluate-filter；不放行的行丢弃。 */
    private List<Map<String, Object>> filterRows(String toolKey, List<Map<String, Object>> rows,
                                                 String rowFilter,
                                                 ToolAdjudicationRequest.Subject subject) throws Exception {
        String userRole = subject != null && !subject.roles().isEmpty() ? subject.roles().get(0) : "viewer";
        List<Map<String, Object>> kept = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> verdict = securityClient.evaluateFilter(rowFilter, row, userRole);
            if (rowAllowed(verdict)) kept.add(row);
        }
        log.debug("H10 rowFilter 完成: tool={} before={} after={}", toolKey, rows.size(), kept.size());
        return kept;
    }

    /**
     * security evaluate-filter 放行判定：只认显式放行标记，缺字段一律视为不放行（fail-closed）。
     * 这里只做布尔读取，不做任何谓词求值 —— 谓词语义全在 security 侧。
     */
    private boolean rowAllowed(Map<String, Object> verdict) {
        if (verdict == null) return false;
        Object flag = verdict.get("allowed");
        if (flag == null) flag = verdict.get("pass");
        if (flag == null) flag = verdict.get("result");
        if (flag instanceof Boolean b) return b;
        if (flag instanceof Number n) return n.intValue() != 0;
        if (flag instanceof String s) {
            String v = s.trim();
            return "true".equalsIgnoreCase(v) || "ALLOW".equalsIgnoreCase(v) || "PASS".equalsIgnoreCase(v);
        }
        return false;
    }

    /** 单列脱敏：列值一次性交 security masking/apply，返回值按 index 写回。 */
    private void maskColumn(String toolKey, List<Map<String, Object>> rows,
                            ToolObligations.Mask mask) throws Exception {
        List<String> values = new ArrayList<>(rows.size());
        List<Integer> targets = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Object v = rows.get(i).get(mask.column());
            if (v == null) continue;
            values.add(String.valueOf(v));
            targets.add(i);
        }
        if (values.isEmpty()) return;

        List<Map<String, Object>> results = securityClient.applyMasking(values, List.of(mask.rule()));
        if (results.size() != values.size()) {
            throw new IllegalStateException(
                    "masking/apply 响应数量与请求不一致: request=" + values.size()
                            + " response=" + results.size());
        }
        for (Map<String, Object> item : results) {
            Object idxObj = item.get("index");
            if (!(idxObj instanceof Number n)) {
                throw new IllegalStateException("masking/apply 响应缺 index");
            }
            int pos = n.intValue();
            if (pos < 0 || pos >= targets.size()) {
                throw new IllegalStateException("masking/apply 响应 index 越界: " + pos);
            }
            Object masked = item.get("masked");
            if (masked == null) {
                throw new IllegalStateException("masking/apply 响应缺 masked 字段");
            }
            rows.get(targets.get(pos)).put(mask.column(), masked);
        }
        log.debug("H10 mask 完成: tool={} column={} rule={} cells={}",
                toolKey, mask.column(), mask.rule(), values.size());
    }

    /**
     * 把结果文本解释为行列表。
     *
     * @return 可原地修改的行列表；{@code null} = 结构不可解释（调用方 fail-closed）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asRows(Object root) {
        if (root instanceof Map<?, ?> map) {
            Map<String, Object> obj = (Map<String, Object>) map;
            Object rowsObj = obj.get("rows");
            if (rowsObj instanceof List<?> list) {
                return toRowList(list);
            }
            if (rowsObj == null) {
                // 单对象结果：视作一行，列级脱敏仍适用
                List<Map<String, Object>> single = new ArrayList<>(1);
                single.add(new LinkedHashMap<>(obj));
                return single;
            }
            return null;
        }
        if (root instanceof List<?> list) {
            return toRowList(list);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toRowList(List<?> list) {
        List<Map<String, Object>> rows = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                rows.add(new LinkedHashMap<>((Map<String, Object>) m));
            } else {
                // 非对象行（标量数组）无法按列脱敏/按行裁决
                return null;
            }
        }
        return rows;
    }

    /** 行列表写回原容器形态，保持 count 与 rows 的一致性。 */
    @SuppressWarnings("unchecked")
    private Object rebuild(Object root, List<Map<String, Object>> rows) {
        if (root instanceof Map<?, ?> map) {
            Map<String, Object> obj = new LinkedHashMap<>((Map<String, Object>) map);
            if (obj.containsKey("rows")) {
                obj.put("rows", rows);
                if (obj.containsKey("count")) obj.put("count", rows.size());
                return obj;
            }
            if (rows.size() == 1) return rows.get(0);
            return obj;
        }
        return rows;
    }
}
