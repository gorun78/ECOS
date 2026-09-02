package com.chinacreator.gzcm.engine.cognitive2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OAG 需求解读节点 — 把原始需求结构化为 slot_map + intent_id（03 文档 §三 s1_intake）。
 *
 * <p>输入 raw_request + domain，输出 intent_id / slot_map / structured_request。
 * 规则：从中文/英文文本中抽取 metric/deviation/domain，纯规则解析（不依赖 LLM，
 * 兜底：抽取不到则 raw_request 作为 raw metric）。</p>
 *
 * <p>对齐 03 文档：
 * [s1_intake | OAG_INTAKE | raw_request+domain → intent_id+slot_map]</p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-3.2)
 */
@Component
public class OagIntakeService {

    private static final Logger log = LoggerFactory.getLogger(OagIntakeService.class);

    /** 抽取百分比数字的正则（如 -12%, +5%） */
    private static final Pattern DEVIATION_PATTERN =
            Pattern.compile("([-+]?(?:\\d+\\.?\\d*)|\\d+\\.?\\d*)\\s*[%％]");

    /** 中文指标名简单模式（粗抽取）：以"率/比/额/数/值/成本"结尾的相邻 CJK 串 */
    private static final Pattern METRIC_HINTS =
            Pattern.compile("[\\u4e00-\\u9fa5]{1,8}(?:率|比|额|数|值|成本|金额)");

    /**
     * 解析原始请求，输出结构化 slots。
     *
     * @param rawRequest 原始字符串（如"首创 2025-05 销售同比 -12%"）
     * @param domain     业务域
     * @param config     节点 config（可空），可含 raw_request/domain/max_depth
     * @return 输出 Map 含 intent_id / slot_map / structured_request
     */
    public Map<String, Object> handle(String rawRequest, String domain, Map<String, Object> config) {
        Map<String, Object> result = new LinkedHashMap<>();
        String intentId = "int-" + UUID.randomUUID().toString().substring(0, 8);
        result.put("intent_id", intentId);

        // fallback：从 config 里再取一次 raw_request
        if (rawRequest == null || rawRequest.isBlank()) {
            if (config != null && config.get("raw_request") != null) {
                rawRequest = String.valueOf(config.get("raw_request"));
            }
        }
        if (domain == null || domain.isBlank() && config != null && config.get("domain") != null) {
            domain = String.valueOf(config.get("domain"));
        }

        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("raw_request", rawRequest == null ? "" : rawRequest);
        slots.put("domain", domain == null ? "default" : domain);

        // 抽取 deviation（百分比数字）
        double deviation = 0.0;
        if (rawRequest != null) {
            Matcher m = DEVIATION_PATTERN.matcher(rawRequest);
            if (m.find()) {
                try {
                    deviation = Double.parseDouble(m.group(1));
                } catch (NumberFormatException ignored) {
                    // 非法格式保留 0.0
                }
            }
        }
        slots.put("deviation", deviation);

        // 抽取 metric 候选
        String metric = "";
        if (rawRequest != null) {
            Matcher mm = METRIC_HINTS.matcher(rawRequest);
            if (mm.find()) {
                metric = mm.group(0);
            }
        }
        slots.put("metric", metric);
        result.put("slot_map", slots);

        // structured_request：诊断请求的脚手架（供下游 s2_plan / s6_reason 消费）
        Map<String, Object> structured = new LinkedHashMap<>(slots);
        structured.put("request_kind", "diagnosis");
        result.put("structured_request", structured);

        log.info("OAG_INTAKE handled: intent_id={}, metric={}, deviation={}, domain={}",
                intentId, metric, deviation, domain);
        return result;
    }
}
