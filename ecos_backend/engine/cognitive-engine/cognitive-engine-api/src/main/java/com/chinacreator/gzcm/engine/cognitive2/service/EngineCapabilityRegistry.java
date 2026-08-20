package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.CognitivePipelineNode;

import java.util.Map;

/**
 * 引擎能力注册表接口 — KAG 推理链环节 → 可编排节点的跨引擎能力映射。
 *
 * <p>每个 NodeType 注册一个执行端点（跨引擎走 REST）：</p>
 * <ul>
 *   <li>INGEST  → kb-engine 文档解析</li>
 *   <li>EXTRACT → kb-engine KnowledgeExtractionService</li>
 *   <li>KG      → kb-engine KGWriterService</li>
 *   <li>REASON  → cognitive-engine KnowledgeReasonerService</li>
 *   <li>DECISION → PMO-32 DecisionService（KAG 推理出口触发）</li>
 * </ul>
 */
public interface EngineCapabilityRegistry {

    /**
     * 执行单个编排节点，调用对应引擎能力。
     *
     * @param node    编排节点定义
     * @param context 上游节点输出的上下文数据
     * @return 节点执行结果（含输出数据供下游消费）
     * @throws Exception 执行失败时抛出（由 Executor 的 RetryPolicy 处理）
     */
    Map<String, Object> executeNode(CognitivePipelineNode node, Map<String, Object> context) throws Exception;
}
