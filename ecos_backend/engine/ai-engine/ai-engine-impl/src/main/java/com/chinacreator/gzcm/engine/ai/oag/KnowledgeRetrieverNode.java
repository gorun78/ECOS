package com.chinacreator.gzcm.engine.ai.oag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Node 5: 知识检索器 — 从知识库/图谱中检索相关上下文。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>基于改写后的查询检索知识片段</li>
 *   <li>对 QUERY 意图检索表结构和 schema</li>
 *   <li>融入 RLS/CLS 过滤条件</li>
 * </ul>
 *
 * <p>外部端点：</p>
 * <ul>
 *   <li>POST /api/v1/knowledge/reason (cognitive-engine)</li>
 * </ul>
 */
@Component
public class KnowledgeRetrieverNode implements OagNode {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrieverNode.class);

    @Override
    public OagPipelineContext execute(OagPipelineContext ctx) {
        ctx.setCurrentNode("KnowledgeRetriever");

        String query = ctx.getRewrittenQuery() != null ? ctx.getRewrittenQuery() : ctx.getUserQuery();
        String intent = ctx.getIntent();
        Map<String, Object> rlsFilters = ctx.getRlsFilters();

        Map<String, Object> result = new LinkedHashMap<>();

        // 基于意图的检索策略
        switch (intent) {
            case "QUERY":
                result.put("strategy", "schema-aware");
                result.put("includeSchema", true);
                result.put("includeSampleData", true);
                break;
            case "ANALYSIS":
                result.put("strategy", "analytics");
                result.put("includeHistorical", true);
                result.put("includeTrends", true);
                break;
            case "ACTION":
                result.put("strategy", "action-context");
                result.put("includeProcedures", true);
                break;
            default:
                result.put("strategy", "general");
                break;
        }

        // 融入 RLS 过滤条件
        if (rlsFilters != null && !rlsFilters.isEmpty()) {
            result.put("rlsFilters", rlsFilters);
        }

        // 模拟知识片段（生产环境应调用 cognitive-engine /api/v1/knowledge/reason）
        result.put("knowledgeChunks", List.of(
                Map.of("source", "ecos-kb", "relevance", 0.92, "content",
                        "ECOS 系统包含数据治理、知识图谱、大模型Agent三大核心能力。DIKW 四层架构：数据(D)→信息(I)→知识(K)→智能(W)。"),
                Map.of("source", "schema-cache", "relevance", 0.87, "content",
                        "sys_agent_session 表结构: id, agent_id, user_id, tenant_id, status, message_count, created_at, last_active_at"),
                Map.of("source", "context-docs", "relevance", 0.75, "content",
                        "Agent Loop 最多 5 轮迭代（think→act→observe→think），每次迭代支持多个工具调用，30s 超时")
        ));

        ctx.setKnowledgeResult(result);
        ctx.getMetadata().put("knowledgeChunks", result.get("knowledgeChunks"));
        ctx.getMetadata().put("retrievalStrategy", result.get("strategy"));

        log.info("[OAG:{}] 知识检索完成 strategy={} chunks={}",
                ctx.getTraceId(), result.get("strategy"),
                ((List<?>) result.get("knowledgeChunks")).size());

        return ctx;
    }
}
