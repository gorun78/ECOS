package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedEntity;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedRelation;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedRule;
import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.scheduler.AgentResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * KAG Extractor 的 ECOS 实现 — 从文本中通过 LLM 一次调用抽取实体+关系+规则。
 *
 * <h3>核心链路</h3>
 * <ol>
 *   <li>从知识库内容加载源文本（通过传入的 content 或回调 sourceLoader）</li>
 *   <li>调用 LLM (LLMGatewayService) 进行 schema-free 抽取</li>
 *   <li>实体+关系 → 自动写入 Neo4j/PostgreSQL（通过 KnowledgeGraphService）</li>
 *   <li>规则 → 随 ExtractedSubGraph 返回，由前端进行人工审核</li>
 * </ol>
 *
 * <h3>SyncMode</h3>
 * <ul>
 *   <li>AUTO (默认): 实体/关系直接写图</li>
 *   <li>MANUAL: 跳过自动写入，仅返回抽取结果</li>
 * </ul>
 */
@Service
public class KnowledgeExtractorService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractorService.class);

    /** LLM 调用子系统 — ECOS 知识抽取专用 */
    private static final String SUBSYSTEM = "ecos-knowledge";
    /** LLM 调用 profile — KAG extractor 专用 profile */
    private static final String PROFILE_NAME = "kag-extractor";

    private final LLMGatewayService llmGateway;
    private final KnowledgeGraphService kgService;
    private final ObjectMapper objectMapper;

    public KnowledgeExtractorService(LLMGatewayService llmGateway,
                                     KnowledgeGraphService kgService,
                                     ObjectMapper objectMapper) {
        this.llmGateway = llmGateway;
        this.kgService = kgService;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * 从文本内容中抽取知识子图。
     *
     * @param content 待抽取的源文本（已由调用方从知识库/文档加载）
     * @param config  抽取配置（domain, syncMode, confidenceThreshold 等）
     * @return 抽取出的子图（实体+关系+规则）。AUTO 模式下实体/关系已写入图数据库。
     */
    public ExtractedSubGraph extract(String content, ExtractionConfig config) {
        if (content == null || content.isBlank()) {
            log.warn("extract called with empty content, returning empty subgraph");
            return new ExtractedSubGraph();
        }
        if (config == null) {
            config = new ExtractionConfig();
        }

        // 1. 构建抽取 prompt
        String prompt = buildExtractionPrompt(content, config);

        // 2. 调用 LLM
        String llmResponse;
        try {
            AgentResult result = llmGateway.execute(SUBSYSTEM, PROFILE_NAME, prompt);
            if (!result.isSuccess()) {
                throw new RuntimeException("LLM extraction failed: " + result.getErrorMsg());
            }
            llmResponse = result.getContent();
            log.info("LLM extraction succeeded: {} input tokens, {} output tokens, {}ms",
                    result.getTokensInput(), result.getTokensOutput(), result.getDurationMs());
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("LLM extraction call failed", e);
        }

        // 3. 解析 LLM 响应 → ExtractedSubGraph
        ExtractedSubGraph subGraph = parseLlmResponse(llmResponse, config);

        // 4. 实体/关系自动写入图数据库 (AUTO 模式)
        if ("AUTO".equalsIgnoreCase(config.getSyncMode())) {
            writeToGraph(subGraph, config.getDomain());
        } else {
            log.info("SyncMode=MANUAL — skipping auto graph write ({} entities, {} relations)",
                    subGraph.getEntities().size(), subGraph.getRelations().size());
        }

        return subGraph;
    }

    /**
     * 便捷方法：仅抽取实体和关系，不抽取规则。
     */
    public ExtractedSubGraph extractEntitiesAndRelations(String content, ExtractionConfig config) {
        if (config == null) {
            config = new ExtractionConfig();
        }
        config.setExtractRules(false);
        return extract(content, config);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Prompt building
    // ═══════════════════════════════════════════════════════════════

    /**
     * 构建 KAG 抽取 prompt。
     */
    private String buildExtractionPrompt(String content, ExtractionConfig config) {
        StringBuilder sb = new StringBuilder(2048);

        sb.append("你是一个知识图谱构建专家。请从以下文本中抽取结构化知识。\n\n");

        // domain 上下文
        if (config.getDomain() != null && !config.getDomain().isBlank()) {
            sb.append("【业务域】").append(config.getDomain()).append("\n");
        }

        // schema 指引
        if (config.getSchema() != null && !config.getSchema().isEmpty()) {
            sb.append("【本体Schema指引】\n");
            for (Map.Entry<String, Object> entry : config.getSchema().entrySet()) {
                sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("【抽取要求】\n");
        sb.append("1. 抽取所有重要实体（人物、组织、概念、事件、指标、规则条件等）。每个实体包含 name/type/properties/confidence。\n");
        sb.append("2. 抽取实体间的关系。每条关系包含 sourceEntity/targetEntity/relationType/confidence。\n");

        if (config.isExtractRules()) {
            sb.append("3. 抽取业务规则。每条规则包含 name/domain/condition(SpEL表达式)/action/applicableObjectTypes/confidence/sourceExcerpt。\n");
        }

        sb.append("\n【输出格式】严格按以下 JSON 结构输出（不要包含 markdown 代码块标记）：\n");
        sb.append("{\n");
        sb.append("  \"entities\": [\n");
        sb.append("    { \"name\": \"实体名称\", \"type\": \"实体类型\", \"properties\": {\"key\": \"value\"}, \"confidence\": 0.95 }\n");
        sb.append("  ],\n");
        sb.append("  \"relations\": [\n");
        sb.append("    { \"sourceEntity\": \"源实体名\", \"targetEntity\": \"目标实体名\", \"relationType\": \"关系类型\", \"confidence\": 0.90 }\n");
        sb.append("  ]");

        if (config.isExtractRules()) {
            sb.append(",\n");
            sb.append("  \"rules\": [\n");
            sb.append("    { \"name\": \"规则名\", \"domain\": \"域\", \"condition\": \"SpEL表达式\", \"action\": \"动作\", \"applicableObjectTypes\": [\"TypeA\"], \"confidence\": 0.85, \"sourceExcerpt\": \"原文片段\" }\n");
            sb.append("  ]\n");
        } else {
            sb.append("\n");
        }
        sb.append("}\n\n");

        sb.append("置信度阈值: ").append(config.getConfidenceThreshold())
          .append("，实体数量上限: ").append(config.getMaxEntities()).append("\n\n");

        sb.append("【待抽取文本】\n");
        // 截断过长文本
        String truncated = content.length() > 12000 ? content.substring(0, 12000) + "\n...(文本被截断)" : content;
        sb.append(truncated);

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    //  LLM response parsing
    // ═══════════════════════════════════════════════════════════════

    /**
     * 将 LLM JSON 响应解析为 ExtractedSubGraph。
     */
    private ExtractedSubGraph parseLlmResponse(String llmResponse, ExtractionConfig config) {
        ExtractedSubGraph subGraph = new ExtractedSubGraph();

        try {
            // 清理可能的 markdown 代码块包裹
            String json = cleanJson(llmResponse);

            Map<String, Object> raw = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            double threshold = config.getConfidenceThreshold();

            // 解析实体
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawEntities = (List<Map<String, Object>>) raw.getOrDefault("entities", Collections.emptyList());
            for (Map<String, Object> e : rawEntities) {
                double conf = toDouble(e.get("confidence"), 1.0);
                if (conf < threshold) continue;

                ExtractedEntity entity = new ExtractedEntity();
                entity.setName((String) e.get("name"));
                entity.setType((String) e.get("type"));
                entity.setConfidence(conf);

                @SuppressWarnings("unchecked")
                Map<String, Object> props = (Map<String, Object>) e.get("properties");
                entity.setProperties(props != null ? props : Collections.emptyMap());

                subGraph.getEntities().add(entity);
            }

            // 解析关系
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawRelations = (List<Map<String, Object>>) raw.getOrDefault("relations", Collections.emptyList());
            for (Map<String, Object> r : rawRelations) {
                double conf = toDouble(r.get("confidence"), 1.0);
                if (conf < threshold) continue;

                ExtractedRelation relation = new ExtractedRelation();
                relation.setSourceEntity((String) r.get("sourceEntity"));
                relation.setTargetEntity((String) r.get("targetEntity"));
                relation.setRelationType((String) r.get("relationType"));
                relation.setConfidence(conf);

                subGraph.getRelations().add(relation);
            }

            // 解析规则
            if (config.isExtractRules() && raw.containsKey("rules")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rawRules = (List<Map<String, Object>>) raw.get("rules");
                for (Map<String, Object> rl : rawRules) {
                    double conf = toDouble(rl.get("confidence"), 1.0);
                    if (conf < threshold) continue;

                    ExtractedRule rule = new ExtractedRule();
                    rule.setName((String) rl.get("name"));
                    rule.setDomain((String) rl.get("domain"));
                    rule.setCondition((String) rl.get("condition"));
                    rule.setAction((String) rl.get("action"));
                    rule.setConfidence(conf);
                    rule.setSourceExcerpt((String) rl.get("sourceExcerpt"));

                    @SuppressWarnings("unchecked")
                    List<String> types = (List<String>) rl.get("applicableObjectTypes");
                    rule.setApplicableObjectTypes(types != null ? types : Collections.emptyList());

                    subGraph.getRules().add(rule);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse LLM extraction response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse LLM extraction response: " + e.getMessage(), e);
        }

        log.info("Parsed subgraph: {} entities, {} relations, {} rules",
                subGraph.getEntities().size(),
                subGraph.getRelations().size(),
                subGraph.getRules().size());

        return subGraph;
    }

    /**
     * 清理 LLM 响应中可能的 markdown 代码块包裹。
     */
    private String cleanJson(String raw) {
        String trimmed = raw.trim();
        // 移除 ```json 和 ``` 包裹
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n");
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Graph writing
    // ═══════════════════════════════════════════════════════════════

    /**
     * 将抽取出的实体和关系批量写入知识图谱。
     * <p>
     * 策略：先创建所有实体节点（去重），记录 name→nodeId 映射，再创建关系。
     * </p>
     */
    private void writeToGraph(ExtractedSubGraph subGraph, String domain) {
        List<ExtractedEntity> entities = subGraph.getEntities();
        List<ExtractedRelation> relations = subGraph.getRelations();

        if (entities.isEmpty() && relations.isEmpty()) {
            log.info("No entities or relations to write — skipping graph write");
            return;
        }

        log.info("Writing {} entities and {} relations to knowledge graph (domain={})",
                entities.size(), relations.size(), domain);

        // 实体去重：通过 name+type 组合去重
        Map<String, String> nameToNodeId = new LinkedHashMap<>(); // "name::type" → nodeId
        int entityWritten = 0;
        int entitySkipped = 0;

        for (ExtractedEntity entity : entities) {
            String key = entity.getName() + "::" + entity.getType();
            if (nameToNodeId.containsKey(key)) {
                entitySkipped++;
                continue;
            }
            try {
                String propsJson = toJson(entity.getProperties());
                String description = buildDescription(entity.getName(), entity.getType(), entity.getProperties());
                var node = kgService.createNode(entity.getName(), entity.getType(), description, propsJson);
                nameToNodeId.put(key, node.getId());
                entityWritten++;
            } catch (Exception e) {
                log.warn("Failed to create node [{}] type={}: {}", entity.getName(), entity.getType(), e.getMessage());
            }
        }

        // 写关系：通过 name→nodeId 映射查找源/目标节点
        int relationWritten = 0;
        int relationSkipped = 0;

        for (ExtractedRelation rel : relations) {
            String sourceKey = rel.getSourceEntity() + "::";
            String targetKey = rel.getTargetEntity() + "::";

            // 尝试匹配已创建的节点（模糊查找：仅精确 name 匹配）
            String sourceId = findNodeId(nameToNodeId, rel.getSourceEntity());
            String targetId = findNodeId(nameToNodeId, rel.getTargetEntity());

            if (sourceId == null || targetId == null) {
                log.debug("Skipping relation [{}]-[{}]->[{}]: missing node reference",
                        rel.getSourceEntity(), rel.getRelationType(), rel.getTargetEntity());
                relationSkipped++;
                continue;
            }

            try {
                kgService.createEdge(sourceId, targetId, rel.getRelationType(), rel.getConfidence());
                relationWritten++;
            } catch (Exception e) {
                log.warn("Failed to create edge [{}]-[{}]->[{}]: {}",
                        rel.getSourceEntity(), rel.getRelationType(), rel.getTargetEntity(), e.getMessage());
            }
        }

        log.info("Graph write complete: entities written={} skipped={}, relations written={} skipped={}",
                entityWritten, entitySkipped, relationWritten, relationSkipped);
    }

    /**
     * 在 nameToNodeId 映射中查找节点 ID（仅 name 精确匹配）。
     */
    private String findNodeId(Map<String, String> nameToNodeId, String entityName) {
        if (entityName == null) return null;
        for (Map.Entry<String, String> entry : nameToNodeId.entrySet()) {
            if (entry.getKey().startsWith(entityName + "::")) {
                return entry.getValue();
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    private double toDouble(Object val, double defaultVal) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); }
            catch (NumberFormatException ignored) { }
        }
        return defaultVal;
    }

    private String toJson(Object obj) {
        if (obj == null) return "{}";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildDescription(String name, String type, Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return type != null ? type + ": " + name : name;
        }
        String propStr = properties.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        return (type != null ? type + ": " : "") + name + " [" + propStr + "]";
    }
}
