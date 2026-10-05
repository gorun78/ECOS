package com.chinacreator.gzcm.ai.wagent.tool;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolCategory;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分册10 F10-17 · Tool Contract 内存六态生命周期管理器：
 * {@code DRAFT → REGISTERED → VALIDATED → ACTIVE → DEPRECATED → RETIRED}。
 *
 * <p>选择策略（只读语义）：{@link #select(String)} / {@link #isActiveTool(String)}
 * <b>仅</b> 返回 ACTIVE 版本；同 toolName 多 version 中取版本最高者；非 ACTIVE 永不被选中。
 * {@link #validateFiveGates(ToolContract)} 五关：schema_valid / example_call /
 * timeout_and_error / idempotent / policy_denial；全过 ⇒ 空 list（非空 = 失败关声明，审计需回显每关）。</p>
 */
public final class ToolContractRegistry {

    public static final ToolStatus[] LIFECYCLE = {
            ToolStatus.DRAFT, ToolStatus.REGISTERED, ToolStatus.VALIDATED,
            ToolStatus.ACTIVE, ToolStatus.DEPRECATED, ToolStatus.RETIRED
    };

    /** 合法迁移表（铁律 6 态顺序 + DRAFT 上修）。 */
    private static final Map<ToolStatus, List<ToolStatus>> TRANS = Map.of(
            ToolStatus.DRAFT,      List.of(ToolStatus.REGISTERED),
            ToolStatus.REGISTERED, List.of(ToolStatus.VALIDATED, ToolStatus.DRAFT),
            ToolStatus.VALIDATED,  List.of(ToolStatus.ACTIVE,    ToolStatus.DRAFT),
            ToolStatus.ACTIVE,     List.of(ToolStatus.DEPRECATED),
            ToolStatus.DEPRECATED, List.of(ToolStatus.RETIRED),
            ToolStatus.RETIRED,    List.of()
    );

    private final Map<String, CopyOnWriteArrayList<ToolContract>> byName = new ConcurrentHashMap<>();

    /** 出厂种子注册（INITIAL_TWELVE 全部入表，version 由种子固定）。 */
    public ToolContractRegistry() {
        for (ToolContract tc : BusinessToolCatalog.INITIAL_TWELVE) {
            ToolContract.validateDefaults(tc);
            byName.computeIfAbsent(tc.name(), k -> new CopyOnWriteArrayList<>()).add(tc);
        }
    }

    /** 注册：仅 DRAFT 入表；同名不同 version 共存（选择策略仅挑 ACTIVE 且版本最高者）。 */
    public synchronized ToolContract register(ToolContract tc) {
        ToolContract.validateDefaults(tc);
        if (tc.status() != ToolStatus.DRAFT) {
            throw new IllegalArgumentException("register 只接受 DRAFT，status=" + tc.status());
        }
        byName.computeIfAbsent(tc.name(), k -> new CopyOnWriteArrayList<>()).add(tc);
        return tc;
    }

    /** 状态迁移（沿合法链；跳态/反向 ⇒ IAE，审计留痕由调用方负责）。 */
    public synchronized ToolContract transition(String name, String version, ToolStatus from, ToolStatus to) {
        List<ToolStatus> next = TRANS.getOrDefault(from, List.of());
        if (!next.contains(to)) {
            throw new IllegalArgumentException("非法生命周期迁移 " + from + " -> " + to + " for " + name);
        }
        ToolContract target = find(name, version);
        if (target == null) throw new IllegalArgumentException("契约缺失 " + name + " v" + version);
        ToolContract updated = new ToolContract(
                target.name(), target.version(), target.engine(), target.category(), target.sideEffect(),
                target.minLevel(), target.requiredPermission(), target.inputSchemaJson(), target.outputSchemaJson(),
                target.timeoutMs(), target.isIdempotent(), target.costClass(), target.isEvidenceOutput(),
                target.dataClassificationText(), target.rollbackPlanText(), target.endpointKind(), target.endpointUrl(),
                target.endpointMethod(), target.owner(), target.slaText(), to, target.deprecatedBy(),
                target.toolset(), target.summary(), target.disclosure(), target.capabilityDepsText());
        List<ToolContract> list = byName.get(name);
        list.removeIf(t -> t.version().equals(version));
        list.add(updated);
        return updated;
    }

    /** 只读选择：仅 ACTIVE，多 version 取版本最高；无 ACTIVE ⇒ null（编排/搜索/校验统一入口）。 */
    public ToolContract select(String name) {
        return isActiveTool(name);
    }

    /** {@link com.chinacreator.gzcm.ai.wagent.orchestrator.PlanValidator} #3 / #7 引用。 */
    public ToolContract isActiveTool(String name) {
        List<ToolContract> list = byName.get(name);
        if (list == null || list.isEmpty()) return null;
        ToolContract best = null;
        for (ToolContract tc : list) {
            if (tc.status() != ToolStatus.ACTIVE) continue;
            if (best == null || compareVersions(tc.version(), best.version()) > 0) best = tc;
        }
        return best;
    }

    /** 生命周期数量健康计数（测试断言六态链覆盖）。 */
    public Map<ToolStatus, AtomicInteger> statusCounts() {
        Map<ToolStatus, AtomicInteger> m = new EnumMap<>(ToolStatus.class);
        for (ToolStatus s : ToolStatus.values()) m.put(s, new AtomicInteger(0));
        for (CopyOnWriteArrayList<ToolContract> list : byName.values()) {
            for (ToolContract tc : list) m.get(tc.status()).incrementAndGet();
        }
        return m;
    }

    /**
     * 契约五关：schema_valid / example_call / timeout_and_error / idempotent / policy_denial。
     * 全过 ⇒ 空 list（非空 = 失败关声明，审计需回显每关）。
     */
    public static List<String> validateFiveGates(ToolContract tc) {
        List<String> fails = new ArrayList<>();
        ToolContract.validateDefaults(tc);
        boolean schemaOk = tc.inputSchemaJson() != null && !tc.inputSchemaJson().isBlank()
                && tc.outputSchemaJson() != null && !tc.outputSchemaJson().isBlank();
        if (!schemaOk) fails.add("schema_valid");
        boolean exampleOk = tc.endpointKind() != null
                && tc.endpointUrl() != null && !tc.endpointUrl().isBlank()
                && tc.endpointMethod() != null;
        if (!exampleOk) fails.add("example_call");
        if (tc.timeoutMs() <= 0 || tc.timeoutMs() > 30_000) fails.add("timeout_and_error");
        if (tc.category() == ToolCategory.COMMIT && !tc.isIdempotent()
                && (tc.rollbackPlanText() == null || tc.rollbackPlanText().isBlank())) {
            fails.add("idempotent");
        }
        if (tc.requiredPermission() == null || tc.requiredPermission().isBlank()) fails.add("policy_denial");
        return List.copyOf(fails);
    }

    /** 版本比较：数值 → 数字，否则字符串自然序（禁抛异常）。 */
    private static int compareVersions(String a, String b) {
        try {
            return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
        } catch (NumberFormatException e) {
            return (a == null ? "" : a).compareTo(b == null ? "" : b);
        }
    }

    private ToolContract find(String name, String version) {
        List<ToolContract> list = byName.get(name);
        if (list == null) return null;
        for (ToolContract tc : list) if (tc.version().equals(version)) return tc;
        return null;
    }
}
