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
 *   <li>OAG_INTAKE   — OAG 需求解读（Wave-3.2 新增，cognitive 本地 OagIntakeService）</li>
 *   <li>OAG_PLAN     — OAG 任务拆解（Wave-3.2 新增，cognitive 本地 OagPlannerService）</li>
 *   <li>OAG_STRATEGY — OAG 策略生成（Wave-3.2 新增，cognitive 本地 StrategyGeneratorService）</li>
 * </ul>
 *
 * <p>Wave-3.2 增量（03 文档 03-跨引擎编排层设计.md §三）：
 * 把 OAG 8 步对齐到 8 节点，节点类型只增不改。</p>
 */
public enum NodeType {
    INGEST,
    EXTRACT,
    KG,
    REASON,
    DECISION,
    OAG_INTAKE,
    OAG_PLAN,
    OAG_STRATEGY;

    public static NodeType fromString(String value) {
        if (value == null) return null;
        try {
            return NodeType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
