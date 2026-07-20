package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.GuardrailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Service
public class GuardrailsServiceImpl implements GuardrailsService {

    private static final Logger log = LoggerFactory.getLogger(GuardrailsServiceImpl.class);
    private final AtomicLong idSeq = new AtomicLong(0);

    private static final Pattern PII_PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern PII_ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
    private static final Pattern PII_EMAIL = Pattern.compile("[\\w.-]+@[\\w.-]+\\.\\w+");

    private final JdbcTemplate jdbc;

    public GuardrailsServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> validate(Map<String, Object> req) {
        String llmOutput = String.valueOf(req.getOrDefault("llmOutput", ""));
        List<String> contextFacts = req.get("contextFacts") instanceof List
                ? (List<String>) req.get("contextFacts") : Collections.emptyList();

        List<String> violations = new ArrayList<>();
        boolean passed = true;

        if (PII_PHONE.matcher(llmOutput).find()) {
            violations.add("PII_DETECTED: 手机号");
            passed = false;
        }
        if (PII_ID_CARD.matcher(llmOutput).find()) {
            violations.add("PII_DETECTED: 身份证号");
            passed = false;
        }
        if (PII_EMAIL.matcher(llmOutput).find()) {
            violations.add("PII_DETECTED: 邮箱");
            passed = false;
        }

        if (!contextFacts.isEmpty()) {
            boolean allFactsMissing = true;
            for (String fact : contextFacts) {
                if (fact != null && llmOutput.contains(fact)) {
                    allFactsMissing = false;
                    break;
                }
            }
            if (allFactsMissing) {
                violations.add("HALLUCINATION: 输出不包含任何上下文事实");
                passed = false;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", passed);
        result.put("violations", violations);
        result.put("checkedAt", Instant.now().toString());
        return result;
    }

    @Override
    public List<Map<String, Object>> listPolicies() {
        try {
            return jdbc.queryForList("SELECT * FROM ecos_guardrail_policy ORDER BY created_at DESC");
        } catch (Exception e) {
            log.debug("Guardrail policies table not available: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> createPolicy(Map<String, Object> policy) {
        String name = String.valueOf(policy.getOrDefault("name", ""));
        if (name.isEmpty()) throw new IllegalArgumentException("name is required");

        String id = String.valueOf(idSeq.incrementAndGet());
        long now = Instant.now().toEpochMilli();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", name);
        result.put("type", policy.getOrDefault("type", "block"));
        result.put("severity", policy.getOrDefault("severity", "medium"));
        result.put("isEnabled", policy.getOrDefault("isEnabled", true));
        result.put("parameters", policy.getOrDefault("parameters", Collections.emptyMap()));
        result.put("createdAt", now);
        result.put("updatedAt", now);

        try {
            jdbc.update(
                "INSERT INTO ecos_guardrail_policy (id, name, type, severity, is_enabled, parameters, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
                id, name, result.get("type"), result.get("severity"),
                result.get("isEnabled"), "[]");
        } catch (Exception e) {
            log.debug("Persist guardrail policy failed (using memory): {}", e.getMessage());
        }

        log.info("Guardrails policy created: id={} name={}", id, name);
        return result;
    }

    @Override
    public void deletePolicy(String id) {
        try {
            jdbc.update("DELETE FROM ecos_guardrail_policy WHERE id = ?", id);
        } catch (Exception e) {
            log.debug("Delete guardrail policy from DB failed: {}", e.getMessage());
        }
        log.info("Guardrails policy deleted: id={}", id);
    }
}
