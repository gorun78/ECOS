package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.*;

import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionDiffVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyVersionService;

/**
 * PMO-39 批次2 T3 — 本体版本 diff 简化端点（对应检查报告 §2.1 #21）。
 *
 * <h3>端点（双路径）：</h3>
 * <ul>
 *   <li>GET /api/v1/ontology/versions/diff?v1={versionId1}&v2={versionId2}</li>
 *   <li>GET /api/ontology/versions/diff?...</li>
 * </ul>
 *
 * <p>调用现有 {@link OntologyVersionService#diff}（不传 ontologyId 时按版本 ID 定位），
 * 在内存中把两个 snapshot 对比为 added/removed/modified 三数组，无 DB 写入。
 * v1==v2 → 空 diff；缺失版本 → 404 + 错误信息。</p>
 *
 * <p><b>T16-4 说明</b>：返回体由 Map 改强类型 {@link OntologyVersionDiffVO}
 * （复用 T16-4 已定义的 DTO，字段/键名与既有 Map 输出契约等价）；
 * diff 计算逻辑（added/removed/modified 逐 key 比对）不变，
 * snapshot 快照与条目集合为动态结构（T16-4: 版本 diff raw 动态结构豁免 Map）。
 */
@RestController
@RequestMapping({"/api/v1/ontology", "/api/ontology"})
public class VersionDiffController {

    private final OntologyVersionService versionService;

    public VersionDiffController(OntologyVersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping("/versions/diff")
    public ApiResponse<OntologyVersionDiffVO> diff(@RequestParam String v1, @RequestParam String v2) {
        if (v1 == null || v1.isBlank() || v2 == null || v2.isBlank()) {
            return ApiResponse.badRequest("v1 和 v2 参数不能为空");
        }
        if (v1.equals(v2)) {
            return ApiResponse.success(emptyDiff(v1, v2));
        }

        Map<String, Object> full;
        try {
            full = versionService.diff(null, v1, v2);
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound("版本不存在: " + e.getMessage());
        }
        if (full == null) {
            return ApiResponse.notFound("版本不存在");
        }

        OntologyVersionDiffVO result = new OntologyVersionDiffVO();
        result.setVersion1(str(full.get("version1")));
        result.setVersion2(str(full.get("version2")));
        Object snapshot1 = full.get("snapshot1");
        Object snapshot2 = full.get("snapshot2");
        result.setSnapshot1(snapshot1);
        result.setSnapshot2(snapshot2);
        result.setAdded(diffField(snapshot1, snapshot2, true));
        result.setRemoved(diffField(snapshot1, snapshot2, false));
        result.setModified(diffModified(snapshot1, snapshot2));
        return ApiResponse.success(result);
    }

    private static String str(Object val) {
        return val == null ? null : String.valueOf(val);
    }

    // ── 内存 diff（无 DB 写入）────────────────────────

    private static OntologyVersionDiffVO emptyDiff(String v1, String v2) {
        OntologyVersionDiffVO result = new OntologyVersionDiffVO();
        result.setVersion1Id(v1);
        result.setVersion2Id(v2);
        result.setAdded(List.of());
        result.setRemoved(List.of());
        result.setModified(List.of());
        return result;
    }

    /** added: 在 v2 存在而 v1 不存在的条目；removed: 反向（元素 {field, value} 动态结构，T16-4 豁免） */
    private static List<Object> diffField(Object s1, Object s2, boolean added) {
        Map<String, Object> from = asMap(added ? s2 : s1);
        Map<String, Object> to = asMap(added ? s1 : s2);
        List<Object> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : from.entrySet()) {
            if (!to.containsKey(e.getKey()) || to.get(e.getKey()) == null) {
                out.add(entry(e.getKey(), e.getValue()));
            }
        }
        return out;
    }

    private static List<Object> diffModified(Object s1, Object s2) {
        Map<String, Object> m1 = asMap(s1);
        Map<String, Object> m2 = asMap(s2);
        List<Object> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : m1.entrySet()) {
            if (m2.containsKey(e.getKey()) && !Objects.equals(e.getValue(), m2.get(e.getKey()))) {
                Map<String, Object> m = entry(e.getKey(), e.getValue());
                m.put("newValue", m2.get(e.getKey()));
                out.add(m);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object s) {
        if (s instanceof Map) {
            return (Map<String, Object>) s;
        }
        return Map.of();
    }

    private static Map<String, Object> entry(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("field", key);
        m.put("value", value);
        return m;
    }
}
