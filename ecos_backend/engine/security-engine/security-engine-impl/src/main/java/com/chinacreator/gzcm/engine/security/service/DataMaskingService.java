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
    /** 16~19 位纯数字（拒绝 1+0 开头的 0~8 位长度 → 非银行卡） */
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("^\\d{16,19}$");
    /** 形如 "1,234.56" / "1234.5" / "¥123.45" 的货币金额 */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^(\\D*\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)$");
    /** 中文省份/城市/区县 + 详细地址（兜底用：长度<=50 的中文字符串 + 街道/路/号） */
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5]{2,3}(省|市|区|县)[\\u4e00-\\u9fa5\\d]+(街道|路|号|巷|栋|室)$");

    private final Map<String, MaskFunction> maskFunctions = new ConcurrentHashMap<>();

    public DataMaskingService() {
        /* 内置 7 类（V154 字段级 data_type 全量对齐，AGENTS 承诺的 5+2 类补齐） */
        maskFunctions.put("email", this::maskEmail);
        maskFunctions.put("phone", this::maskPhone);
        maskFunctions.put("idCard", this::maskIdCard);
        maskFunctions.put("bankCard", this::maskBankCard);
        maskFunctions.put("amount", this::maskAmount);
        maskFunctions.put("address", this::maskAddress);
        maskFunctions.put("none", r -> r);  // 占位（data_type=GENERAL 走 none）
    }

    public List<String> getSupportedRules() {
        // 保持老规则在前，向前兼容（vocab 顺序 = demo 顺序）
        return List.of("email", "phone", "idCard", "bankCard", "amount", "address");
    }

    public List<Map<String, Object>> getDemoSamples() {
        List<Map<String, Object>> samples = new ArrayList<>();
        samples.add(buildSample("email", "john@example.com", maskEmail("john@example.com")));
        samples.add(buildSample("email", "alice.smith@company.cn", maskEmail("alice.smith@company.cn")));
        samples.add(buildSample("phone", "13812345678", maskPhone("13812345678")));
        samples.add(buildSample("phone", "18900001111", maskPhone("18900001111")));
        samples.add(buildSample("idCard", "320123199001011234", maskIdCard("320123199001011234")));
        samples.add(buildSample("idCard", "11010119850607789X", maskIdCard("11010119850607789X")));
        /* 新增 3 类（PMO-data10 补齐 AGENTS 承诺） */
        samples.add(buildSample("bankCard", "6222021234561234567", maskBankCard("6222021234561234567")));
        samples.add(buildSample("bankCard", "4532015112830366", maskBankCard("4532015112830366")));
        samples.add(buildSample("amount", "12345.67", maskAmount("12345.67")));
        samples.add(buildSample("amount", "¥1,234.50", maskAmount("¥1,234.50")));
        samples.add(buildSample("address", "上海市浦东新区世纪大道100号", maskAddress("上海市浦东新区世纪大道100号")));
        return samples;
    }

    /**
     * 按 data_type 选规则（V154 字段级兜底）。
     * ID_CARD/PHONE/EMAIL/BANK_CARD/AMOUNT/ADDRESS 直接映射；其它/GENERAL → "none"。
     */
    public String ruleForDataType(String dataType) {
        if (dataType == null || dataType.isBlank()) return "none";
        return switch (dataType.trim().toUpperCase()) {
            case "ID_CARD" -> "idCard";
            case "PHONE" -> "phone";
            case "EMAIL" -> "email";
            case "BANK_CARD" -> "bankCard";
            case "AMOUNT" -> "amount";
            case "ADDRESS" -> "address";
            default -> "none";
        };
    }

    /** 按 mask_strategy 选 rule 并应用（mask_strategy = "none" 时不脱敏）。 */
    public String applyMaskingByStrategy(String maskStrategy, String dataType, String raw) {
        if (raw == null) return raw;
        if ("none".equals(maskStrategy)) return raw; // 显式不脱敏
        String rule = ruleForDataType(dataType);
        if ("none".equals(rule)) return raw;
        return mask(rule, raw);
    }

    private String maskBankCard(String raw) {
        if (raw == null) return raw;
        if (!BANK_CARD_PATTERN.matcher(raw).matches()) return raw;
        // 保留前 6 + 后 4（银联 BIN 6 位 + Luhn 末 4 位）
        return raw.substring(0, 6) + "****" + raw.substring(raw.length() - 4);
    }

    private String maskAmount(String raw) {
        if (raw == null) return raw;
        // PMO-data10  widen: 去尾随 K/kM/m 标记 + 货币符号 + 千分位 + 任何逗号/撇号
        String normalized = raw.trim();
        String suffix = null;
        if (normalized.matches(".*[KkMm]$") && normalized.length() > 1) {
            char last = Character.toLowerCase(normalized.charAt(normalized.length() - 1));
            suffix = (last == 'k') ? "K" : "M";
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        // 去可见货币符号 + 拼接空
        normalized = normalized.replaceAll("[\\s¥$€£\\u00a5]", "");
        // 去千分位（前/后会留一坨，最后还能 parse 用）
        normalized = normalized.replaceAll("[,']", "");
        if (normalized.isEmpty() || !normalized.matches("^[\\d.,]+$")) return raw;
        // 去尾部多余 0 / 空位
        try {
            double v = Double.parseDouble(normalized);
            if (suffix != null) {
                if ("K".equals(suffix)) v *= 1_000;
                else v *= 1_000_000;
            }
            if (v >= 10000) return "¥10,000+";
            if (v >= 1000) return "¥999.99+";
            if (v >= 100) return "¥99.99+";
            if (v >= 10) return "¥9.99+";
            return "¥9.99";
        } catch (NumberFormatException e) {
            return "***";
        }
    }

    private String maskAddress(String raw) {
        if (raw == null) return raw;
        if (ADDRESS_PATTERN.matcher(raw).matches()) {
            // 仅保留省级 + 市 + 区段，详细地址打码
            int end = raw.indexOf("省") >= 0 ? raw.indexOf("省") + 1 : raw.indexOf("市");
            int after = raw.indexOf("区", end >= 0 ? end : 0);
            int visibleEnd = (after >= 0) ? after + 1 : raw.length();
            if (visibleEnd <= 2) return "*";
            return raw.substring(0, Math.max(visibleEnd, 2)) + "****";
        }
        // 兜底：长度 > 8 → 首 4 + **
        if (raw.length() > 8) {
            return raw.substring(0, 4) + "****";
        }
        return raw;
    }

    public List<Map<String, Object>> applyMasking(List<String> data, List<String> rules) {
        boolean singleRule = rules.size() == 1;
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            String raw = data.get(i);
            String rule = singleRule ? rules.get(0) : rules.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", i);
            // SEC-P0-4: 不返回原始明文，仅返回脱敏结果
            MaskFunction fn = maskFunctions.get(rule);
            if (fn == null) {
                item.put("masked", "***");  // 未知规则也不回传原文
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
        // SEC-P0-4: demo 样例也移除明文原始值
        sample.put("input", "****");
        sample.put("masked", masked);
        return sample;
    }

    @FunctionalInterface
    public interface MaskFunction {
        String apply(String raw);
    }
}
