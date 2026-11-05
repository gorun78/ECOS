package com.chinacreator.gzcm.engine.cognitive2.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 决策策略合规检查（{@code POST /{id}/check-rules}）：
 * {@code decisionId + compliant + violations[] + policiesChecked}。
 */
public record DecisionCheckVO(
        String decisionId,
        boolean compliant,
        List<String> violations,
        int policiesChecked) {

    public static DecisionCheckVO of(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        List<String> violations = new ArrayList<>();
        if (source.get("violations") instanceof List<?> list) {
            for (Object item : list) {
                violations.add(item == null ? null : String.valueOf(item));
            }
        }
        Object checked = source.get("policiesChecked");
        return new DecisionCheckVO(
                source.get("decisionId") == null ? null : String.valueOf(source.get("decisionId")),
                Boolean.TRUE.equals(source.get("compliant")),
                violations,
                checked instanceof Number n ? n.intValue() : 0);
    }
}
