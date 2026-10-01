package com.chinacreator.gzcm.services.buszhi.impl;

import com.chinacreator.gzcm.common.service.ICopilotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * F03-14 / W88 (O-20) 跨域收口 — buszhi 不再扫描 {@code engine.data.service}。
 *
 * <p>ontology-engine 的 {@code OntologyCopilotController}（被 buszhi 的
 * {@code com.chinacreator.gzcm.engine.ontology} 扫描纳管）通过构造器注入
 * {@link ICopilotService}（common-api 横切契约）。其唯一实现
 * {@code CopilotServiceImpl} 位于 data-engine（土·D），属另一引擎域。原先
 * buszhi 靠 {@code @ComponentScan("com.chinacreator.gzcm.engine.data.service")}
 * 把该 impl 撤入自身 JVM，属跨域越界（铁律 §0.3.1）。撤除该扫描条目后，
 * buszhi 已不再有 data-engine 的 Copilot bean，故在此提供本域
 * {@link ICopilotService} 的<b>不再接线</b>占位实现。</p>
 *
 * <p>标记 {@link Primary}，确保当 monolith 聚合态（gateway）同时把 data-engine
 * 真实 impl 与本子类装进同一容器时，ontology 侧注入优先命中本 shim，避免
 * {@code NoUniqueBeanDefinition}。所有方法返回统一「能力未接入」安全桩，
 * 不抛异常（copilot 建模建议属可降级 AI 能力）。</p>
 */
@Primary
@Component
public class CopilotServiceShim implements ICopilotService {

    private static final Logger log = LoggerFactory.getLogger(CopilotServiceShim.class);

    private static final String UNSUPPORTED_MESSAGE =
            "Copilot 能力未在本服务接入（F03-14/O-20：本体域不再扫描 data-engine 实现）";

    @Override
    public Map<String, Object> generateSql(String prompt, String schemaInfo) {
        return unsupported("generateSql");
    }

    @Override
    public Map<String, Object> generatePipeline(String description, String availableSources) {
        return unsupported("generatePipeline");
    }

    @Override
    public Map<String, Object> suggestExpression(String fieldName, String context) {
        return unsupported("suggestExpression");
    }

    @Override
    public Map<String, Object> generateUdf(String description, String language) {
        return unsupported("generateUdf");
    }

    @Override
    public Map<String, Object> diagnose(String runId, String errorLog) {
        return unsupported("diagnose");
    }

    private Map<String, Object> unsupported(String op) {
        log.warn("[CopilotServiceShim] {} invoked; copilot capability not wired in buszhi -> {}", op, UNSUPPORTED_MESSAGE);
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("supported", false);
        result.put("message", UNSUPPORTED_MESSAGE);
        return result;
    }
}
