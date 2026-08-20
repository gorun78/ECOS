package com.chinacreator.gzcm.engine.cognitive2.service;

import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SpelConditionEvaluator {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    public EvalResult evaluate(String expression, Map<String, Object> facts) {
        // Try SpEL evaluation first
        EvalResult spelResult = trySpel(expression, facts);
        if (spelResult != null) {
            return spelResult;
        }

        // Fallback to contains matching
        return containsMatch(expression, facts);
    }

    private EvalResult trySpel(String expression, Map<String, Object> facts) {
        // Quick heuristic: if expression doesn't look like SpEL, skip
        if (!looksLikeSpel(expression)) {
            return null;
        }

        try {
            SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            Map<String, Object> usedVars = new HashMap<>();

            // Inject facts as variables (setVariable takes name without '#' prefix)
            for (Map.Entry<String, Object> entry : facts.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }

            Boolean result = parser.parseExpression(expression).getValue(context, Boolean.class);
            if (result == null) {
                return null;
            }

            // Determine which variables were actually used in the expression
            // Simple approach: check which fact keys appear in the expression string
            for (Map.Entry<String, Object> entry : facts.entrySet()) {
                if (expression.contains("#" + entry.getKey())) {
                    usedVars.put(entry.getKey(), entry.getValue());
                }
            }

            String detail = buildDetail(expression, usedVars, result);
            return new EvalResult(result, detail, usedVars);
        } catch (Exception e) {
            // Fall back to contains matching
            return null;
        }
    }

    private boolean looksLikeSpel(String expression) {
        if (expression == null || expression.isEmpty()) {
            return false;
        }
        // Heuristic: contains '#' or any of the common operators
        return expression.contains("#")
                || expression.contains(">")
                || expression.contains("<")
                || expression.contains("==")
                || expression.contains("!=")
                || expression.contains("&&")
                || expression.contains("||");
    }

    private EvalResult containsMatch(String expression, Map<String, Object> facts) {
        Map<String, Object> usedVars = new HashMap<>();
        boolean satisfied = false;
        String detail = "";

        for (Map.Entry<String, Object> entry : facts.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String valueStr = String.valueOf(value);

            if (expression.contains(key) && expression.contains(valueStr)) {
                usedVars.put(key, value);
                satisfied = true;
                detail = "Matched key '" + key + "' with value '" + valueStr + "'";
                break;
            }
        }

        if (!satisfied) {
            detail = "No contains match found";
        }

        return new EvalResult(satisfied, detail, usedVars);
    }

    private String buildDetail(String expression, Map<String, Object> usedVars, boolean result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Expression: ").append(expression).append(" -> ").append(result);
        if (!usedVars.isEmpty()) {
            sb.append("; used variables: ");
            usedVars.forEach((k, v) -> sb.append("#").append(k).append("=").append(v).append(" "));
        }
        return sb.toString();
    }

    public static class EvalResult {
        private boolean satisfied;
        private String detail;
        private Map<String, Object> evaluatedVars;

        public EvalResult() {
        }

        public EvalResult(boolean satisfied, String detail, Map<String, Object> evaluatedVars) {
            this.satisfied = satisfied;
            this.detail = detail;
            this.evaluatedVars = evaluatedVars;
        }

        public boolean isSatisfied() {
            return satisfied;
        }

        public void setSatisfied(boolean satisfied) {
            this.satisfied = satisfied;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }

        public Map<String, Object> getEvaluatedVars() {
            return evaluatedVars;
        }

        public void setEvaluatedVars(Map<String, Object> evaluatedVars) {
            this.evaluatedVars = evaluatedVars;
        }
    }
}
