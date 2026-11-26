package com.chinacreator.gzcm.workspace.scenario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.context.expression.MapAccessor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 场景规则求值（V367 批次 E）：只读规则中心，按绑定 id 取规则并用受限 SpEL 求值。
 *
 * <p>约束：表达式禁止类型引用/构造/反射/系统调用；变量表只含调用方给定事实。
 * 单条规则失败只记 {@code error}，不阻断其余规则与整轮运行；规则中心不可用由调用方降级。</p>
 */
@Component
public class ScenarioRuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRuleEvaluator.class);
    private static final List<String> DENY = List.of("T(", "new ", "Runtime", "getClass", ".class",
            "System.", "ProcessBuilder", "#this", "@");

    private final RestTemplate buszhiRest;
    private final ExpressionParser parser = new SpelExpressionParser();

    public ScenarioRuleEvaluator(@Qualifier("workspaceBuszhiRest") RestTemplate buszhiRest) {
        this.buszhiRest = buszhiRest;
    }

    /** 求值绑定规则；返回 {@code [{ruleId,name,matched,action,error}]}。 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> evaluate(List<String> ruleIds, Map<String, Object> facts) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (ruleIds == null || ruleIds.isEmpty()) return out;

        Map<String, Map<String, Object>> byId = loadRules();
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setRootObject(facts == null ? Map.of() : facts);
        ctx.addPropertyAccessor(new MapAccessor());

        for (String id : ruleIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ruleId", id);
            Map<String, Object> rule = byId.get(id);
            if (rule == null) {
                row.put("matched", false);
                row.put("error", "规则不存在或不可读");
                out.add(row);
                continue;
            }
            row.put("name", rule.get("name"));
            row.put("action", rule.get("action"));
            Object expr = rule.get("expression");
            if (expr == null || String.valueOf(expr).isBlank()) {
                row.put("matched", false);
                row.put("error", "规则无 expression");
                out.add(row);
                continue;
            }
            String text = String.valueOf(expr);
            String deny = DENY.stream().filter(text::contains).findFirst().orElse(null);
            if (deny != null) {
                row.put("matched", false);
                row.put("error", "表达式含受限片段: " + deny);
                out.add(row);
                continue;
            }
            try {
                Boolean matched = parser.parseExpression(text).getValue(ctx, Boolean.class);
                row.put("matched", Boolean.TRUE.equals(matched));
            } catch (RuntimeException e) {
                log.warn("场景规则求值失败 rule={} err={}", id, e.getMessage());
                row.put("matched", false);
                row.put("error", "求值失败: " + e.getMessage());
            }
            out.add(row);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> loadRules() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        CallerCredentials.apply(headers);
        try {
            ResponseEntity<Map> resp = buszhiRest.exchange("/ecos/rules", HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) return Map.of();
            Object code = body.get("code");
            if (code instanceof Number n && n.intValue() != 0) return Map.of();
            if (!(body.get("data") instanceof List<?> rows)) return Map.of();
            Map<String, Map<String, Object>> out = new LinkedHashMap<>();
            for (Object o : rows) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> rule = (Map<String, Object>) m;
                    Object id = rule.get("id");
                    if (id != null) out.put(String.valueOf(id), rule);
                }
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("场景规则：规则中心不可用，降级空表: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 凭据中继（避免直接依赖 common 的 CallerCredentialSource 造成测试摩擦）。 */
    private static final class CallerCredentials {
        static void apply(HttpHeaders headers) {
            com.chinacreator.gzcm.common.security.caller.CallerCredentialSource.applyTo(headers);
        }
    }
}
