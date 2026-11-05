package com.chinacreator.gzcm.engine.kb.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本体树远端载荷解码（{@code GET /api/v1/ecos/ontologies[/{id}/entities]} 的 JSON 应答）。
 *
 * <p>远端形态随本体引擎演进（{@code data} 可能是数组或单对象；entity 行按
 * {@code code/name/entityType/domainId/sortOrder} 取值），属外部输入、不冻结 schema；
 * 解码集中在本类，控制器只消费 {@link OntologyTreeVO} 强类型树枝。</p>
 */
public final class OntologyTreeRemotePayload {

    private OntologyTreeRemotePayload() {
    }

    /** {@code data} 段 → 行列表（数组原样收；单对象包一层；其它 → 空表）。 */
    public static List<Map<String, Object>> asRows(Object data) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (data instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) m;
                    out.add(row);
                }
            }
            return out;
        }
        if (data instanceof Map<?, ?> single) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) single;
            out.add(row);
        }
        return out;
    }

    /** 从 raw entity 行提取 {@code code}（空白跳过）。 */
    public static List<String> extractCodes(List<Map<String, Object>> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (Map<String, Object> entity : raw) {
            Object code = entity.get("code");
            if (code != null && !String.valueOf(code).isBlank()) {
                out.add(String.valueOf(code));
            }
        }
        return out;
    }

    /** 从 raw entity 行提取 {@link OntologyTreeVO.OntologyItem.EntityBrief}（code 空白跳过）。 */
    public static List<OntologyTreeVO.OntologyItem.EntityBrief> extractBriefs(
            List<Map<String, Object>> raw) {
        List<OntologyTreeVO.OntologyItem.EntityBrief> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (Map<String, Object> entity : raw) {
            Object code = entity.get("code");
            if (code == null || String.valueOf(code).isBlank()) {
                continue;
            }
            OntologyTreeVO.OntologyItem.EntityBrief brief = new OntologyTreeVO.OntologyItem.EntityBrief();
            Object name = entity.get("name");
            Object entityType = entity.get("entityType");
            Object domainId = entity.get("domainId");
            Object sortOrder = entity.get("sortOrder");
            brief.setCode(String.valueOf(code));
            brief.setName(name == null ? null : String.valueOf(name));
            brief.setEntityType(entityType == null ? null : String.valueOf(entityType));
            brief.setDomainId(domainId == null ? null : String.valueOf(domainId));
            brief.setSortOrder(sortOrder instanceof Number n ? n.intValue()
                    : (sortOrder != null ? parseIntSafely(sortOrder) : null));
            out.add(brief);
        }
        return out;
    }

    private static Integer parseIntSafely(Object raw) {
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
