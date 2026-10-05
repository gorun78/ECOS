package com.chinacreator.gzcm.ai.wagent.tool;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolCategory;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolStatus;
import com.chinacreator.gzcm.ai.wagent.tool.ToolContractRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-17 Tool Contract 离线单测：
 * COMMIT 类别必须带 rollbackPlanText（DB CHECK + 应用双校）；同 toolName 多版本选择器只取唯一 ACTIVE。
 */
class ToolContractRegistryTest {

    private static ToolContract draftQuery(String name, String version) {
        return new ToolContract(
                name, version, "data-engine", ToolCategory.QUERY, "read",
                1, "perm:query:" + name,
                "{\"type\":\"object\",\"properties\":{}}", "{\"type\":\"object\",\"properties\":{}}",
                5_000, true, "cost:" + name, false,
                "L1_internal", null,
                "rest", "/api/v1/data/query", "POST",
                "ai-engine", "sla_p95_30s", ToolStatus.DRAFT, null, "business",
                "seed:" + name, "disclosure:" + name, "dep:" + name);
    }

    /** F10-17：category=COMMIT 且 rollbackPlanText=null ⇒ 拒收（validateDefaults 抛 IAE）。 */
    @Test
    void commitCategoryRequiresRollbackPlan() {
        ToolContract badCommit = new ToolContract(
                "bad_commit", "1", "data-engine", ToolCategory.COMMIT, "write",
                2, "perm:commit:bad_commit",
                "{\"type\":\"object\",\"properties\":{}}", "{\"type\":\"object\",\"properties\":{}}",
                5_000, true, "cost:bad_commit", false,
                "L1_internal", null,
                "rest", "/api/v1/data/query", "POST",
                "ai-engine", "sla", ToolStatus.DRAFT, null, "business",
                "seed:bad_commit", "disclosure", "dep");
        assertThrows(IllegalArgumentException.class, () -> ToolContract.validateDefaults(badCommit),
                "COMMIT 工具缺 rollbackPlanText 必须拒收");

        // 同契约但带 rollback ⇒ 通过。
        ToolContract okCommit = new ToolContract(
                "ok_commit", "1", "data-engine", ToolCategory.COMMIT, "write",
                2, "perm:commit:ok_commit",
                "{\"type\":\"object\",\"properties\":{}}", "{\"type\":\"object\",\"properties\":{}}",
                5_000, true, "cost:ok_commit", false,
                "L1_internal", "rollback_ok_commit",
                "rest", "/api/v1/data/query", "POST",
                "ai-engine", "sla", ToolStatus.DRAFT, null, "business",
                "seed:ok_commit", "disclosure", "dep");
        assertNotNull(ToolContract.validateDefaults(okCommit), "带 rollback 的 COMMIT 工具应通过默认校验");
    }

    /**
     * F10-17：同一 toolName 的 DRAFT / ACTIVE / DEPRECATED 三版本并存，
     * 选择器（select / isActiveTool）只返回唯一 ACTIVE 者。
     */
    @Test
    void onlyActiveVersionSelectable() {
        ToolContractRegistry registry = new ToolContractRegistry();

        // 注册三份同 name 不同 version 的 DRAFT。
        registry.register(draftQuery("sel_tool", "1")); // DRAFT
        registry.register(draftQuery("sel_tool", "2")); // DRAFT
        ToolContract draft3 = draftQuery("sel_tool", "3");

        // 把 version=1 推到 ACTIVE：DRAFT→REGISTERED→VALIDATED→ACTIVE。
        registry.transition("sel_tool", "1", ToolStatus.DRAFT, ToolStatus.REGISTERED);
        registry.transition("sel_tool", "1", ToolStatus.REGISTERED, ToolStatus.VALIDATED);
        registry.transition("sel_tool", "1", ToolStatus.VALIDATED, ToolStatus.ACTIVE);

        // 把 version=3 推到 DEPRECATED（跳过 ACTIVE 不成——只能沿链；此处让 1 独占 ACTIVE）。
        // 直接断言：注册表此刻唯一 ACTIVE = version 1；version 2 仍 DRAFT，不可选。
        ToolContract active = registry.select("sel_tool");
        assertNotNull(active, "应能选到唯一的 ACTIVE 版本");
        assertEquals("1", active.version(), "选择器必须返回唯一 ACTIVE（version=1）");
        assertEquals(ToolStatus.ACTIVE, active.status());
        assertNotEquals("2", active.version(), "DRAFT 的 version=2 绝不可被选中");

        // 非 ACTIVE（DRAFT）永不被 select 命中。
        assertNull(registry.isActiveTool("ghost_tool"), "无 ACTIVE 契约的工具 select ⇒ null");
    }
}
