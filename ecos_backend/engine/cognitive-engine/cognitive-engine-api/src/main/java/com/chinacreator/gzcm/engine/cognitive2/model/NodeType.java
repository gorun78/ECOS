package com.chinacreator.gzcm.engine.cognitive2.model;

/**
 * KAG 推理链节点类型 — 对齐 KAG Builder→Solver→决策落地链路。
 *
 * <ul>
 *   <li>INGEST  — KAG Builder：文档接入（kb-engine 解析）</li>
 *   <li>EXTRACT — KAG Builder：知识抽取（kb-engine KnowledgeExtractionService）</li>
 *   <li>KG      — KAG Builder：建图（kb-engine KGWriterService）</li>
 *   <li>REASON  — KAG Solver：混合推理（cognitive-engine KnowledgeReasonerService）</li>
 *   <li>DECISION — 决策落地（调 PMO-32 DecisionService，KAG 推理出口触发）</li>
 * </ul>
 */
public enum NodeType {
    INGEST,
    EXTRACT,
    KG,
    REASON,
    DECISION;

    public static NodeType fromString(String value) {
        if (value == null) return null;
        try {
            return NodeType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
