package com.chinacreator.gzcm.engine.kb.profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 画像规范化键与语义版本工具（F04-06 / D.3）。
 * <ul>
 *   <li>profile_key 规范化：{@code metric|k1=v1|k2=v2}，维度键<b>排序后</b>拼接（可复现、幂等）；</li>
 *   <li>语义版本单调递增：同 profile_key 每次生成 patch+1（默认 1.0.0）。</li>
 * </ul>
 *
 * @author ECOS KB Team
 */
public final class ProfileKeys {

    private ProfileKeys() {
    }

    /** 五级退化链维度组合（PRD-04 §2.3-3 / 任务退化链，从细到粗）。 */
    public static final List<String[]> DEGRADE_CHAIN_GRAINS = List.of(
            new String[]{"project", "department", "stage"},       // 0 项目×部门×环节
            new String[]{"projectType", "department", "stage"},   // 1 项目类型×部门×环节
            new String[]{"projectType", "stage"},                 // 2 项目类型×环节
            new String[]{"department", "stage"},                  // 3 部门×环节
            new String[]{"stage"}                                 // 4 全局×环节
    );

    /** 退化链各级可读名（与 DEGRADE_CHAIN_GRAINS 下标对齐，D.3 degradeChain 展示）。 */
    public static final String[] DEGRADE_CHAIN_NAMES = new String[]{
            "项目×部门×环节", "项目类型×部门×环节", "项目类型×环节", "部门×环节", "全局×环节"
    };

    /**
     * 由 metric + 维度 (attrs→values) 构造规范化 profile_key。
     * 维度键按字典序排序后拼接为 {@code k=v}，以 {@code |} 连接，前缀 metric。
     */
    public static String buildKey(String metric, Map<String, String> dims) {
        List<String> parts = new ArrayList<>();
        if (dims != null) {
            dims.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getValue() != null)
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(e -> parts.add(e.getKey() + "=" + e.getValue()));
        }
        return metric + (parts.isEmpty() ? "" : "|" + String.join("|", parts));
    }

    /** 由 metric + grain 维度组合构造 key（用于退化链各级）。 */
    public static String buildKeyForGrain(String metric, String[] grain, Map<String, String> reqDims) {
        java.util.LinkedHashMap<String, String> used = new java.util.LinkedHashMap<>();
        for (String g : grain) {
            if (reqDims != null && reqDims.containsKey(g)) {
                used.put(g, reqDims.get(g));
            }
        }
        return buildKey(metric, used);
    }

    /**
     * 取某 profile_key 下一个单调递增的语义版本：max(现有版本)+1(patch)；无则 1.0.0 / 升级 patch。
     *
     * @param latestVersion 现有最大版本（可为 null）
     */
    public static String nextVersion(String latestVersion) {
        if (latestVersion == null || latestVersion.isBlank()) {
            return "1.0.0";
        }
        String[] segs = latestVersion.split("\\.");
        int major = segs.length > 0 ? parseIntSafe(segs[0]) : 1;
        int minor = segs.length > 1 ? parseIntSafe(segs[1]) : 0;
        int patch = segs.length > 2 ? parseIntSafe(segs[2]) : 0;
        return major + "." + minor + "." + (patch + 1);
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** 行数据四类维度取值 (project/projectType/department/stage) → k=v map（供 grain 匹配）。 */
    public static Map<String, String> rowDims(CuratedFactSource.CuratedFactRow row) {
        Map<String, String> m = new java.util.HashMap<>();
        if (row.getProject() != null) {
            m.put("project", row.getProject());
        }
        if (row.getProjectType() != null) {
            m.put("projectType", row.getProjectType());
        }
        if (row.getDepartment() != null) {
            m.put("department", row.getDepartment());
        }
        if (row.getStage() != null) {
            m.put("stage", row.getStage());
        }
        return m;
    }
}
