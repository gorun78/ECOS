package com.chinacreator.gzcm.engine.cognitive2.service.calc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 认知模型发布内容规范化哈希（REQ-COG-07 规格 3；V261 {@code content_hash VARCHAR(64) SHA-256 hex}）。
 *
 * <p>§C.8"新增纯函数"纪律：无 Spring / 无 DB 依赖，输入 = 版本行 Map，输出 = 64 位小写 hex。
 * 规范化 = 固定字段清单按声明序取值（与 Map 迭代/列序无关）+ {@code features} 列
 * （features_json 读别名）深度键排序（同内容不同字段序 ⇒ 同哈希，见
 * {@code CognitiveModelRegistryTest#contentHashIsStableAcrossFieldOrder}）；
 * {@code backtest_score} 去尾零规范化（0.50 与 0.5 同哈希）。
 * 发布升格以该哈希冻结不可变判据（V261 头注：不可变性判据 = PUBLISHED 且哈希非空，应用侧守卫）。</p>
 */
public final class ModelContentHasher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 参与哈希的内容字段清单（DB 列序固定；features/features_json 两键取其一，内容同）。 */
    private static final List<String> HASHED_FIELDS = List.of(
            "model_id", "model_type", "cognitive_model_type", "version", "status");

    private static final String NULL_TOKEN = "␀";   // 显式空值标记：null 与空串不得同哈希
    private static final char SEP = '\u001F';       // 字段分隔（单元分隔符，禁出现在内容里）

    private ModelContentHasher() {}

    /** 行的规范化内容串（公开以便单测直接断言"字段序无关"）。 */
    public static String canonicalContent(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder();
        for (String f : HASHED_FIELDS) {
            sb.append(f).append('=').append(scalar(row.get(f))).append(SEP);
        }
        Object features = row.containsKey("features_json") ? row.get("features_json") : row.get("features");
        sb.append("features=").append(canonicalJson(features)).append(SEP);
        sb.append("backtest_score=").append(canonicalNumber(
                row.get("backtest_score"))).append(SEP);
        return sb.toString();
    }

    /** SHA-256 hex(64, 小写) of {@link #canonicalContent(Map)}。 */
    public static String sha256Hex(Map<String, Object> row) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonicalContent(row).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                                    .append(Character.forDigit(b & 0xF, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必带 SHA-256；缺失属环境缺陷，禁静默降级为其他摘要
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String scalar(Object v) {
        return v == null ? NULL_TOKEN : String.valueOf(v).trim();
    }

    /** 数值规范化：去尾零/科学计数差异（BigDecimal plain string）；非数值按标量文本。 */
    private static String canonicalNumber(Object v) {
        if (v == null) return NULL_TOKEN;
        if (v instanceof Number n) {
            return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
        }
        try {
            return new BigDecimal(String.valueOf(v).trim()).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return scalar(v);   // 数据缺陷由门禁 contract/backtest 判 FAIL，哈希只如实反映文本
        }
    }

    /** JSON 内容深度排序重序列化（键序无关）；非 JSON 文本原样 trim（fail-loud 属门禁面，不属哈希面）。 */
    private static String canonicalJson(Object v) {
        if (v == null) return NULL_TOKEN;
        String raw = String.valueOf(v).trim();
        if (raw.isEmpty()) return NULL_TOKEN;
        try {
            JsonNode node = MAPPER.readTree(raw);
            return sortKeys(node).toString();
        } catch (Exception notJson) {
            return raw;
        }
    }

    private static JsonNode sortKeys(JsonNode node) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            Map<String, JsonNode> sorted = new TreeMap<>();
            while (names.hasNext()) {
                String n = names.next();
                sorted.put(n, sortKeys(node.get(n)));
            }
            ObjectNode out = MAPPER.createObjectNode();
            sorted.forEach(out::set);
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = MAPPER.createArrayNode();
            node.forEach(item -> out.add(sortKeys(item)));
            return out;
        }
        return node;
    }
}
