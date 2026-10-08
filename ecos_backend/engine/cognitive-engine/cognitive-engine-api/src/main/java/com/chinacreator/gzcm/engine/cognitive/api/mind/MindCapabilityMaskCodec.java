package com.chinacreator.gzcm.engine.cognitive.api.mind;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MindCapabilityMask 单序列化（设计-05 F05-11 / Cg-8 / D.3）。
 *
 * <p>DB 列 {@code capability_mask TEXT} 存 JSON 数组字符串（{@code ["DETECT","FORECAST"]}）；
 * 内存 {@link List<MindCapabilityMask>} 反向序列化为同一串（防两个类型/两种形态并存）。</p>
 *
 * <p>禁 {@code "DETECT,FORECAST"} 逗号串形态；禁前端 {@code string[]} 隐式转换。</p>
 */
public final class MindCapabilityMaskCodec {

    private MindCapabilityMaskCodec() {}

    /** 空掩码序列化为 「[]」，与其他 JSON 形态一致。 */
    public static String encode(List<MindCapabilityMask> caps) {
        if (caps == null || caps.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < caps.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(caps.get(i).name()).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * 从库中 TEXT JSON 数组字符串解析；空白/空串返回空 List；
     * 非法 token 忽略但不抛异常（读侧容错；写侧由 Service 层拒绝）。
     */
    public static List<MindCapabilityMask> decode(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return new ArrayList<>();
        String s = json.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
        List<MindCapabilityMask> out = new ArrayList<>();
        Set<MindCapabilityMask> seen = new LinkedHashSet<>();
        // 极简 JSON 数组解析：token 间用 ',' 分，token 内容包双引号
        int i = 0;
        while (i < s.length()) {
            int start = s.indexOf('"', i);
            if (start < 0) break;
            int end = s.indexOf('"', start + 1);
            if (end < 0) break;
            String token = s.substring(start + 1, end);
            MindCapabilityMask m = MindCapabilityMask.fromName(token);
            if (m != null && seen.add(m)) out.add(m);
            i = end + 1;
        }
        return out;
    }
}
