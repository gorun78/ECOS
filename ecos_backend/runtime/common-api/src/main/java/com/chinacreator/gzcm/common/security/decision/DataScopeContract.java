package com.chinacreator.gzcm.common.security.decision;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 详设-01 §4.7.2 / C.2 — AI 通道「请求数据域」（{@code resource.dataScope}）的<b>单源键面与读数</b>
 * 契约（#142/B4-3d-3，差距 D-B4-20）。
 *
 * <p><b>为什么需要这个类（缺陷本质）</b>：护栏策略 {@code ecos.data_access} 的 AI 列语义
 * （项目负责人 p1 域内 ALLOW ／ p2 越域 DENY）完全由 {@code input.resource.dataScope} 的
 * {@code departmentId} / {@code projectIds} 两键表达，但这两键在仓内是<b>各写各的</b>：
 * AI 侧 {@code ToolAdjudicationRequest#resourceMap} 内联字面量拼载荷，而三通道唯一入口
 * {@code /decision} 的 {@code safeOpa} <b>从不发 {@code resource} 段</b> —— 于是
 * ①同一份键面有两套字面量（D-B4-25 同族的漂移温床），②AI 的数据域语义在唯一入口上
 * <b>结构上不可表达</b>，带 dataScope 的验收例只能绕道「护栏源直连」回放
 * （差距文档 v1.0.16 行裁决 D-B4-20 的原文）。</p>
 *
 * <p><b>本类做三件事</b>：把键面收敛成一组常量；把"从 REST 请求体读声明域"做成类型安全、
 * 白名单化的单点读数；把"往护栏输入写声明域"做成与录制形态逐字同形的单点写出。
 * 生产者（{@code SecurityDecisionService}）、入口（{@code DecisionController}）与
 * AI 载荷（{@code ToolAdjudicationRequest}）一律只做委托，禁再写字面量。</p>
 *
 * <p><b>信任边界（必须写在这里，否则会被读成"客户端可信输入"）</b>：{@code departmentId}
 * 是<b>调用方声明</b>的请求域，不是主体所属域。策略侧的横向越权判据是
 * 「声明域 ≠ 服务端派生的 {@code subject.department}」，而 {@code subject.department}
 * 只来自服务端（分册 00 信任链：客户端 {@code X-Org-Id} 已被剥离）。因此本键<b>扩不得</b>成
 * 主体属性：调用方谎报域的结果是 DENY（越域），不是放行。</p>
 *
 * <p><b>刻意不做的事</b>：不建模 {@code periods}（策略全文不读它，AI 工具载荷照旧自带，
 * 本契约只管策略实际判定的两键）；不给"未声明域"兜任何默认值 —— 未声明就是未声明，
 * 由策略侧 {@code AI_SCOPE_NOT_DECLARED} 拒绝，禁在这里伪装成"全域"。</p>
 */
public final class DataScopeContract {

    /** 护栏输入的顶层资源段键。 */
    public static final String KEY_RESOURCE = "resource";

    /** 资源段内的请求数据域键（AI 通道专用；§4.7.2 的 p1/p2 判据出处）。 */
    public static final String KEY_DATA_SCOPE = "dataScope";

    /**
     * 请求所声明的所属域（与 {@code subject.department} 做横向越权比对，非主体属性）。
     *
     * <p><b>信任边界（#142/B4-3d-11，D-B4-29 取证）</b>：本键只允许<b>服务端真值解析</b>写入。
     * AI 工具通道的入参白名单刻意排除它 —— 该通道的 SQL 执行腿没有 RLS 行级下推，
     * 本比对是唯一行范围闸，让被裁决者自报即形同放行（见 {@code ToolPayloadDataScopeNailTest}）。
     * 未声明时策略落 {@code AI_DEPARTMENT_NOT_DECLARED}，禁伪装成跨域。</p>
     */
    public static final String KEY_DEPARTMENT_ID = "departmentId";

    /** 调用方声明的项目集（空集＝未声明域，策略侧落 {@code AI_SCOPE_NOT_DECLARED}）。 */
    public static final String KEY_PROJECT_IDS = "projectIds";

    private DataScopeContract() {
    }

    /**
     * 一次请求声明的数据域（不可变）。
     *
     * <p>两键都允许"未声明"（{@code null} / 空表），且<b>必须</b>能与"声明为空值"区分：
     * 写出时未声明的键一律不写（禁写 {@code ""} 或 {@code []} 冒充声明过 —— 那会让策略侧
     * 的 {@code count(requested_project_ids) == 0} 与"声明了空集"同形）。</p>
     */
    public record Declared(String departmentId, List<String> projectIds) {

        public Declared {
            projectIds = projectIds == null ? List.of() : List.copyOf(projectIds);
        }

        /** 是否声明了项目集（策略侧 AI 列判据的必要条件）。 */
        public boolean hasProjectScope() {
            return !projectIds.isEmpty();
        }

        /** 是否声明了所属域。 */
        public boolean hasDepartment() {
            return departmentId != null && !departmentId.isBlank();
        }

        /** 整段声明是否为空（空 ⇒ 写出侧不得产生 {@code resource} 段）。 */
        public boolean empty() {
            return !hasProjectScope() && !hasDepartment();
        }
    }

    /** 空声明（{@code /decision} 缺省形态：页面/导出通道不发数据域）。 */
    public static Declared none() {
        return new Declared(null, List.of());
    }

    /**
     * 从请求体的 {@code resource} 段读声明域 —— 白名单化、类型安全、缺键不猜。
     *
     * <p>非 {@code Map} 的 {@code resource}、非 {@code Map} 的 {@code dataScope}、
     * 非文本的 {@code departmentId}、非 {@code List} 的 {@code projectIds}、
     * 列表里的 {@code null}/空白元素，一律<b>丢弃而不是转换</b>：请求体是外部输入，
     * 把 {@code 123} 读成 {@code "123"} 等于替调用方伪造一个它没声明的域。</p>
     */
    public static Declared read(Object resourceSegment) {
        if (!(resourceSegment instanceof Map<?, ?> resource)) {
            return none();
        }
        if (!(resource.get(KEY_DATA_SCOPE) instanceof Map<?, ?> scope)) {
            return none();
        }
        String department = text(scope.get(KEY_DEPARTMENT_ID));
        List<String> projects = new ArrayList<>();
        if (scope.get(KEY_PROJECT_IDS) instanceof List<?> raw) {
            for (Object o : raw) {
                String v = text(o);
                if (v != null && !projects.contains(v)) {
                    projects.add(v);
                }
            }
        }
        return new Declared(department, projects);
    }

    /**
     * 写成护栏输入的 {@code resource} 段 —— <b>未声明的键不写</b>。
     *
     * <p>键序固定为 {@code departmentId → projectIds}，与 {@code data_access} 的录制器
     * （{@code record_data_access_oracles.mjs} 的 {@code SCOPE} 工厂）逐字同形：
     * oracle 回放的可比性钉（{@code assertRequestMatches}）是按序列化后的 JSON 文本深比的，
     * 多一个空键或换个键序都会让"同一条链"读起来不像同一条链。这里的顺序是<b>契约</b>，
     * 不是风格，改动必须与重录同批。</p>
     *
     * @return 可直接放进 {@code input.resource} 的段；整段未声明时返回 {@code null}
     *         （调用方据此<b>不写</b> {@code resource} 键，保持既有页面/导出载荷形态不变）
     */
    public static Map<String, Object> toResourceSegment(Declared declared) {
        if (declared == null || declared.empty()) {
            return null;
        }
        Map<String, Object> scope = new java.util.LinkedHashMap<>();
        if (declared.hasDepartment()) {
            scope.put(KEY_DEPARTMENT_ID, declared.departmentId());
        }
        if (declared.hasProjectScope()) {
            scope.put(KEY_PROJECT_IDS, declared.projectIds());
        }
        Map<String, Object> resource = new java.util.LinkedHashMap<>();
        resource.put(KEY_DATA_SCOPE, scope);
        return resource;
    }

    /** 空白安全取值（非文本 / 空白串 ⇒ {@code null}，即"未声明"）。 */
    public static String text(Object raw) {
        if (raw instanceof String s && !s.isBlank()) return s;
        return null;
    }

    /**
     * 主体/上下文文本归一：{@code null} 与空白一律成 {@code ""}。
     *
     * <p>存在理由（本批实测的语义坑）：策略侧的 {@code subject_department} 用
     * {@code object.get(subject 段, "department", "")} 取值，而默认值<b>只在键缺失时生效</b>；
     * 若生产者写 {@code "department": null}，读数是 {@code null} 而不是 {@code ""}，于是
     * 「组织域未解析」那条拒绝规则（要求 {@code == ""}）不会命中，
     * 反而落到「声明域 ≠ 主体域」这条 —— 把"我们还没解析出主体域"（D-B4-23）
     * 伪装成"该主体越权访问别的域"。结果仍是 DENY，但理由是假的，运维据此查错方向。
     * 故组织域在进入护栏输入前必须归一为 {@code ""}。</p>
     */
    public static String textOrEmpty(Object raw) {
        if (raw == null) return "";
        String s = String.valueOf(raw);
        return s.isBlank() ? "" : s;
    }
}
