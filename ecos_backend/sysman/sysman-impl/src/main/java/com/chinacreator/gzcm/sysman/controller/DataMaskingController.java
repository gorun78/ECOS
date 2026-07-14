package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 数据脱敏控制器。
 *
 * <h3>接口</h3>
 * <ul>
 *   <li>GET  /api/v1/data-masking/demo — 返回示例脱敏数据（email / phone / idCard）</li>
 *   <li>POST /api/v1/data-masking/apply — 按规则对数据批量脱敏</li>
 * </ul>
 *
 * <h3>内置脱敏模式</h3>
 * <table>
 *   <tr><th>rule</th><th>示例输入</th><th>输出</th></tr>
 *   <tr><td>email</td><td>john@example.com</td><td>j***@example.com</td></tr>
 *   <tr><td>phone</td><td>13812345678</td><td>138****5678</td></tr>
 *   <tr><td>idCard</td><td>320123199001011234</td><td>3201**********1234</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/data-masking")
public class DataMaskingController {

    private static final Logger log = LoggerFactory.getLogger(DataMaskingController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── 内置脱敏规则 ────────────────────────────────────────

    /** email: 保留首字符和@后域名 */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^(.)[^@]*(@.*)$");

    /** phone: 保留前3后4 */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^(\\d{3})\\d{4}(\\d{4})$");

    /** idCard: 保留前4后4 */
    private static final Pattern IDCARD_PATTERN =
            Pattern.compile("^(\\d{4})\\d{10}(\\d{4})$");

    /** 规则名 → 脱敏函数 */
    private final Map<String, MaskFunction> maskFunctions = new ConcurrentHashMap<>();

    public DataMaskingController() {
        maskFunctions.put("email", this::maskEmail);
        maskFunctions.put("phone", this::maskPhone);
        maskFunctions.put("idCard", this::maskIdCard);
    }

    // ── GET /demo ──────────────────────────────────────────

    /**
     * 返回脱敏示例数据（含原始值和脱敏值对照）。
     */
    @GetMapping("/demo")
    public ApiResponse<Map<String, Object>> demo() {
        List<Map<String, Object>> samples = new ArrayList<>();

        samples.add(buildSample("email", "john@example.com", maskEmail("john@example.com")));
        samples.add(buildSample("email", "alice.smith@company.cn", maskEmail("alice.smith@company.cn")));
        samples.add(buildSample("phone", "13812345678", maskPhone("13812345678")));
        samples.add(buildSample("phone", "18900001111", maskPhone("18900001111")));
        samples.add(buildSample("idCard", "320123199001011234", maskIdCard("320123199001011234")));
        samples.add(buildSample("idCard", "11010119850607789X", maskIdCard("11010119850607789X")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("samples", samples);
        result.put("supportedRules", List.of("email", "phone", "idCard"));
        result.put("description", "数据脱敏示例：展示三种内置规则的原始值与脱敏值对照");

        return ApiResponse.success(result);
    }

    // ── POST /apply ────────────────────────────────────────

    /**
     * 按规则对数据批量脱敏。
     *
     * <pre>
     * Body:
     * {
     *   "data": ["john@example.com", "13812345678", ...],
     *   "rules": ["email", "phone", ...]     // 与 data 一一对应，或长度=1 则统一规则
     * }
     * </pre>
     */
    @PostMapping("/apply")
    public ApiResponse<?> apply(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> data = (List<String>) body.get("data");
            @SuppressWarnings("unchecked")
            List<String> rules = (List<String>) body.get("rules");

            if (data == null || data.isEmpty()) {
                return ApiResponse.badRequest("data 不能为空");
            }
            if (rules == null || rules.isEmpty()) {
                return ApiResponse.badRequest("rules 不能为空");
            }

            // 如果 rules 长度为 1，视为统一规则应用于所有 data
            boolean singleRule = rules.size() == 1;
            if (!singleRule && rules.size() != data.size()) {
                return ApiResponse.badRequest(
                        "rules 长度必须为 1（统一规则）或与 data 一一对应，当前 data="
                                + data.size() + ", rules=" + rules.size());
            }

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

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", results.size());
            result.put("results", results);
            return ApiResponse.success(result);

        } catch (ClassCastException e) {
            log.error("数据脱敏请求格式错误", e);
            return ApiResponse.badRequest("请求格式错误: data 和 rules 必须为字符串数组");
        } catch (Exception e) {
            log.error("数据脱敏处理异常", e);
            return ApiResponse.internalError("脱敏处理失败: " + e.getMessage());
        }
    }

    // ── 内置脱敏实现 ────────────────────────────────────────

    private String maskEmail(String raw) {
        if (raw == null || !raw.contains("@")) return raw;
        var m = EMAIL_PATTERN.matcher(raw);
        if (m.matches()) {
            return m.group(1) + "***" + m.group(2);
        }
        // fallback: 保留首字符
        return raw.charAt(0) + "***" + raw.substring(raw.indexOf('@'));
    }

    private String maskPhone(String raw) {
        if (raw == null) return raw;
        var m = PHONE_PATTERN.matcher(raw);
        if (m.matches()) {
            return m.group(1) + "****" + m.group(2);
        }
        // fallback: 保留首尾各3位
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
        // 18位 fallback
        if (raw.length() == 18) {
            return raw.substring(0, 4) + "**********" + raw.substring(14);
        }
        // 15位 fallback
        if (raw.length() == 15) {
            return raw.substring(0, 4) + "*******" + raw.substring(11);
        }
        return raw;
    }

    // ── 工具方法 ────────────────────────────────────────────

    private Map<String, Object> buildSample(String rule, String raw, String masked) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("rule", rule);
        sample.put("raw", raw);
        sample.put("masked", masked);
        return sample;
    }

    /**
     * 脱敏函数式接口，便于后续注册自定义规则。
     */
    @FunctionalInterface
    private interface MaskFunction {
        String apply(String raw);
    }
}
