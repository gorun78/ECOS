package com.chinacreator.gzcm.ai.wagent.tool;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolStatus;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 分册10 F10-11 · 工具渐进披露搜索：按 caller 权限 / 自动化天花板 / 数据分类 /
 * 工具 status 四重过滤；退役（RETIRED）工具永不再召回（铁律：检索不可见）。
 *
 * <p>实现：仅 ACTIVE 进 select；DRAFT/VALIDATED/DEPRECATED 由 {@link ToolContractRegistry#select}
 * 已过滤，此处再叠加 caller 权限 / 天花板 / 数据分类三横切。metadata 常驻（§5.1
 * metadata-only，恒 1，禁超 10）。metadata 工具集经 {@link #ALWAYS_LOADED_FIXED} 锁定。</p>
 */
public final class ToolSearchService {

    private final ToolContractRegistry registry;

    /** 调用上下文（caller ident + 权限集 + 自动化天花板）。 */
    public record CallerContext(String userId, String tenantId,
                                Set<String> permissions, int maxAutomationLevel) {
        public CallerContext {
            if (permissions == null) permissions = Set.of();
        }
    }

    public ToolSearchService(ToolContractRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("registry null");
        this.registry = registry;
    }

    /**
     * 关键搜索：命中的工具需满足
     * ① status=ACTIVE（registry.select 只返 ACTIVE，双层 L3 硬闸）；
     * ② caller.permissions 包含 requiredPermission（或 requiredPermission 为空 = 公开工具）；
     * ③ caller.maxAutomationLevel ≥ tool.minLevel；
     * ④ dataClassif = internal 或空（严格下限，禁 public 数据下沉内部工具）。
     *
     * @param query   意图查询串（空 = 全量候选；非空按 name/summary/toolset 子串匹配）
     * @param toolset 可选 toolset 限定（空 = 不限）
     * @param caller  调用上下文（禁 null）
     */
    public List<ToolContract> search(String query, String toolset, CallerContext caller) {
        if (caller == null) throw new IllegalArgumentException("caller null");
        String q = query == null ? "" : query.toLowerCase();
        String ts = toolset == null ? "" : toolset.toLowerCase();
        List<ToolContract> out = new ArrayList<>();
        for (String name : BusinessToolCatalog.toolNames()) {
            ToolContract c = registry.select(name);
            if (c == null || c.status() != ToolStatus.ACTIVE) continue;
            if (!ts.isEmpty() && (c.toolset() == null || !ts.equals(c.toolset().toLowerCase()))) continue;
            if (!q.isEmpty() && !nameMatches(c, q)) continue;
            if (!permissionOk(c, caller)) continue;
            if (!levelOk(c, caller)) continue;
            if (!classificationOk(c)) continue;
            out.add(c);
        }
        return List.copyOf(out);
    }

    /** 常驻 metadata 工具计数（§5.1 恒 1，禁 > 10 排它；measure for 治理告警）。 */
    public static final int ALWAYS_LOADED_FIXED = 1;
    public static final int ALWAYS_LOADED_MAX   = 10;

    public static int alwaysLoadedCount() { return ALWAYS_LOADED_FIXED; }

    private boolean nameMatches(ToolContract c, String q) {
        return (c.name() != null && c.name().toLowerCase().contains(q))
                || (c.summary() != null && c.summary().toLowerCase().contains(q))
                || (c.toolset() != null && c.toolset().toLowerCase().contains(q));
    }
    private boolean permissionOk(ToolContract c, CallerContext caller) {
        return c.requiredPermission() == null
                || caller.permissions().contains(c.requiredPermission());
    }
    private boolean levelOk(ToolContract c, CallerContext caller) {
        return caller.maxAutomationLevel() >= c.minLevel();
    }
    private boolean classificationOk(ToolContract c) {
        String dc = c.dataClassificationText();
        if (dc == null || dc.isBlank()) return true;
        // 严格下限：只放行内部数据（"L1_internal" / "internal" 均视为内部；公域拒绝）
        return dc.toLowerCase().contains("internal");
    }

    /** 便捷：把某分类工具的 ACTIVE 集捞出来（治理/诊断侧）。 */
    public List<ToolContract> byCategory(ToolCategory cat) {
        List<ToolContract> out = new ArrayList<>();
        for (String name : BusinessToolCatalog.toolNames()) {
            ToolContract c = registry.select(name);
            if (c != null && c.category() == cat) out.add(c);
        }
        return List.copyOf(out);
    }
}
