package com.chinacreator.gzcm.ai.wagent.tool;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolCategory;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolStatus;
import com.chinacreator.gzcm.ai.wagent.tool.ToolContract;
import com.chinacreator.gzcm.ai.wagent.tool.ToolContractRegistry;
import com.chinacreator.gzcm.ai.wagent.tool.ToolSearchService;
import com.chinacreator.gzcm.ai.wagent.tool.ToolSearchService.CallerContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-11 工具渐进披露离线单测：
 * 常驻 metadata 计数恒 1（≤ 10）；RETIRED 工具永不再是 ACTIVE 因而永不被召回；
 * caller 权限不含 requiredPermission ⇒ 工具对其不可见（对照权限够 ⇒ 可见）。
 */
class ToolSearchTest {

    /** 复用既有种子名（search 只遍历 BusinessToolCatalog.toolNames() 的固定名）。 */
    private static final String NAME = "query_project_profit";

    /** F10-11：常驻 metadata 工具计数恒 1，禁超 10。 */
    @Test
    void alwaysLoadedCountBelowTen() {
        assertEquals(1, ToolSearchService.ALWAYS_LOADED_FIXED, "常驻 metadata 计数恒 1");
        assertTrue(ToolSearchService.alwaysLoadedCount() <= ToolSearchService.ALWAYS_LOADED_MAX,
                "常驻计数必须 ≤ 10（治理告警上限）");
        assertEquals(1, ToolSearchService.alwaysLoadedCount());
    }

    /** F10-11：种子 v1 从 ACTIVE 退化到 RETIRED 后，select 命中 null，search 不再召回（检索不可见）。 */
    @Test
    void retiredToolNeverRecalled() {
        ToolContractRegistry registry = new ToolContractRegistry();
        ToolSearchService svc = new ToolSearchService(registry);

        assertNotNull(registry.select(NAME), "种子 v1 初始为 ACTIVE");
        // ACTIVE → DEPRECATED → RETIRED（沿生命周期链）。
        registry.transition(NAME, "1", ToolStatus.ACTIVE, ToolStatus.DEPRECATED);
        registry.transition(NAME, "1", ToolStatus.DEPRECATED, ToolStatus.RETIRED);

        assertNull(registry.select(NAME), "RETIRED 后无 ACTIVE ⇒ select 命中 null");
        List<ToolContract> results = svc.search("", null, callersFull());
        assertFalse(results.stream().anyMatch(c -> NAME.equals(c.name())),
                "RETIRED 工具绝不得出现在 search 结果（铁律：检索不可见）");
    }

    /**
     * F10-11：caller 权限不含 requiredPermission ⇒ 工具对其不可见；权限够 ⇒ 可见（对照）。
     * 方法：在同一种子名下注册更高版本（v2, dataClassif=internal 过分类关）并推到 ACTIVE，
     * 使 select 命中的 ACTIVE 是 v2；权限过滤成为决定性关。
     */
    @Test
    void permissionFilteredCallerLacksPermission() {
        ToolContractRegistry registry = new ToolContractRegistry();
        ToolSearchService svc = new ToolSearchService(registry);

        // v2（版本高于种子 v1，dataClassif=internal 过分类关，requiredPermission=perm:secret）。
        registry.register(new ToolContract(
                NAME, "2", "data-engine", ToolCategory.QUERY, "read",
                1, "perm:secret",
                "{\"t\":\"o\"}", "{\"t\":\"o\"}", 5_000, true, "cost:x", false,
                "internal", null,
                "rest", "/api/v1/data/query", "POST",
                "ai-engine", "sla", ToolStatus.DRAFT, null, "business",
                "seed2", "disc", "dep"));
        registry.transition(NAME, "2", ToolStatus.DRAFT, ToolStatus.REGISTERED);
        registry.transition(NAME, "2", ToolStatus.REGISTERED, ToolStatus.VALIDATED);
        registry.transition(NAME, "2", ToolStatus.VALIDATED, ToolStatus.ACTIVE);

        ToolContract selected = registry.select(NAME);
        assertNotNull(selected, "应选到 v2（同 name 多 ACTIVE 取版本最高者）");
        assertEquals("2", selected.version());

        // 无权限 caller ⇒ 不可见。
        CallerContext noPerm = new CallerContext("u", "t", Set.of(), 3);
        boolean visibleNoPerm = svc.search("", null, noPerm).stream().anyMatch(c -> NAME.equals(c.name()));
        assertFalse(visibleNoPerm, "无 perm:secret 的 caller 必须看不见该工具");

        // 有权限 caller ⇒ 可见（证明是权限关在过滤而非其它横切关）。
        boolean visibleWithPerm = svc.search("", null, callersFull()).stream().anyMatch(c -> NAME.equals(c.name()));
        assertTrue(visibleWithPerm, "带 perm:secret 的 caller 必须看见该工具");
    }

    private static CallerContext callersFull() {
        return new CallerContext("u", "t", Set.of("perm:secret"), 3);
    }
}
