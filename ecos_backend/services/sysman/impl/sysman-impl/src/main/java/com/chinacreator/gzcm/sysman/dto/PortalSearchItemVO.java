package com.chinacreator.gzcm.sysman.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 搜索中间项（含内部分数，出 wire 前丢弃）：`type/id/name/url + score`。
 *
 * <p>打分规则与原实现一致：标题/名称命中 2.0、描述/定义命中 1.0；
 * {@code name} 为 null 的行（如 workflow 仅有 description）不因 null 被判 0 档。</p>
 */
public record PortalSearchItemVO(String type, Object id, Object name, String url, double score) {

    public static List<PortalSearchItemVO> segments(List<Map<String, Object>> rows,
                                                    String keyword, String type, String url) {
        List<PortalSearchItemVO> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> r : rows) {
            Object name = r.get("name") != null ? r.get("name") : r.get("term");
            Object desc = r.get("description") != null ? r.get("description") : r.get("definition");
            double s = score(name, desc, keyword);
            if (s <= 0) {
                continue;
            }
            Object id = r.get("id");
            if (id == null || (id instanceof String sid && sid.isBlank())) {
                continue;
            }
            out.add(new PortalSearchItemVO(type, id, name, url, s));
        }
        return out;
    }

    private static double score(Object nameObj, Object descObj, String keyword) {
        double s = 0.0;
        String kw = keyword.toLowerCase();
        if (nameObj != null && nameObj.toString().toLowerCase().contains(kw)) s += 2.0;
        if (descObj != null && descObj.toString().toLowerCase().contains(kw)) s += 1.0;
        return s;
    }
}
