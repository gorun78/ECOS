package com.chinacreator.gzcm.engine.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class DataMaskingService {

    private static final Logger log = LoggerFactory.getLogger(DataMaskingService.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^(.)[^@]*(@.*)$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\d{3})\\d{4}(\\d{4})$");
    private static final Pattern IDCARD_PATTERN = Pattern.compile("^(\\d{4})\\d{10}(\\d{4})$");

    private final Map<String, MaskFunction> maskFunctions = new ConcurrentHashMap<>();

    public DataMaskingService() {
        maskFunctions.put("email", this::maskEmail);
        maskFunctions.put("phone", this::maskPhone);
        maskFunctions.put("idCard", this::maskIdCard);
    }

    public List<String> getSupportedRules() {
        return List.of("email", "phone", "idCard");
    }

    public List<Map<String, Object>> getDemoSamples() {
        List<Map<String, Object>> samples = new ArrayList<>();
        samples.add(buildSample("email", "john@example.com", maskEmail("john@example.com")));
        samples.add(buildSample("email", "alice.smith@company.cn", maskEmail("alice.smith@company.cn")));
        samples.add(buildSample("phone", "13812345678", maskPhone("13812345678")));
        samples.add(buildSample("phone", "18900001111", maskPhone("18900001111")));
        samples.add(buildSample("idCard", "320123199001011234", maskIdCard("320123199001011234")));
        samples.add(buildSample("idCard", "11010119850607789X", maskIdCard("11010119850607789X")));
        return samples;
    }

    public List<Map<String, Object>> applyMasking(List<String> data, List<String> rules) {
        boolean singleRule = rules.size() == 1;
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            String raw = data.get(i);
            String rule = singleRule ? rules.get(0) : rules.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", i);
            item.put("raw", raw);
            MaskFunction fn = maskFunctions.get(rule);
            if (fn == null) {
                item.put("masked", raw);
                item.put("rule", rule);
                item.put("error", "不支持的脱敏规则: " + rule);
            } else {
                item.put("masked", fn.apply(raw));
                item.put("rule", rule);
            }
            results.add(item);
        }
        return results;
    }

    public boolean isRuleSupported(String rule) {
        return maskFunctions.containsKey(rule);
    }

    public String mask(String rule, String value) {
        MaskFunction fn = maskFunctions.get(rule);
        return fn != null ? fn.apply(value) : value;
    }

    private String maskEmail(String raw) {
        if (raw == null || !raw.contains("@")) return raw;
        var m = EMAIL_PATTERN.matcher(raw);
        if (m.matches()) {
            return m.group(1) + "***" + m.group(2);
        }
        return raw.charAt(0) + "***" + raw.substring(raw.indexOf('@'));
    }

    private String maskPhone(String raw) {
        if (raw == null) return raw;
        var m = PHONE_PATTERN.matcher(raw);
        if (m.matches()) {
            return m.group(1) + "****" + m.group(2);
        }
        if (raw.length() >= 7) {
            int maskLen = raw.length() - 6;
            return raw.substring(0, 3) + "*".repeat(maskLen) + raw.substring(raw.length() - 3);
        }
        return raw;
    }

    private String maskIdCard(String raw) {
        if (raw == null) return raw;
        var m = IDCARD_PATTERN.matcher(raw);
        if (m.matches()) {
            return m.group(1) + "**********" + m.group(2);
        }
        if (raw.length() == 18) {
            return raw.substring(0, 4) + "**********" + raw.substring(14);
        }
        if (raw.length() == 15) {
            return raw.substring(0, 4) + "*******" + raw.substring(11);
        }
        return raw;
    }

    private Map<String, Object> buildSample(String rule, String raw, String masked) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("rule", rule);
        sample.put("raw", raw);
        sample.put("masked", masked);
        return sample;
    }

    @FunctionalInterface
    public interface MaskFunction {
        String apply(String raw);
    }
}
