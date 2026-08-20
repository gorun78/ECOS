package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.model.Decision;
import com.chinacreator.gzcm.engine.cognitive2.model.ProvenanceEntry;

import java.util.List;
import java.util.Map;

/**
 * 决策智能服务 — 五步生命周期：record → link → query → govern → audit
 *
 * <p>对齐 Semantica decision_methods.py，翻译成 Java。
 * 决策是 KAG reasoner 推理的落地产物，决策落地在 KAG 推理出口触发。</p>
 */
public interface DecisionService {

    /**
     * 记录一条决策（record 步骤）。
     *
     * @param category      决策分类（如 vendor_selection / compliance / diagnostic）
     * @param scenario      场景描述
     * @param reasoning     推理依据
     * @param outcome       决策结果
     * @param confidence    置信度 0~1
     * @param decisionMaker 决策者（人/Agent）
     * @return 新建的决策 ID
     */
    String recordDecision(String category, String scenario, String reasoning,
                          String outcome, double confidence, String decisionMaker);

    /**
     * 添加因果关系（link 步骤）。
     *
     * @param sourceId    源决策 ID
     * @param targetId    目标决策 ID
     * @param relationship 关系类型：triggers/enables/causes/precedes
     */
    void addCausalRelationship(String sourceId, String targetId, String relationship);

    /**
     * 查找相似决策（query 步骤）。
     * 降级策略：无 pgvector 时按 category 精确匹配 + 关键词。
     *
     * @param query     查询文本
     * @param maxResults 最大返回数
     * @return 相似决策列表
     */
    List<Decision> findSimilarDecisions(String query, int maxResults);

    /**
     * 追溯决策因果祖先链（query 步骤）。
     *
     * @param decisionId 决策 ID
     * @return 从该决策向上的祖先链列表
     */
    List<Decision> traceDecisionChain(String decisionId);

    /**
     * 分析决策的下游影响图（govern 步骤）。
     *
     * @param decisionId 决策 ID
     * @return 影响图结构（nodes + edges）
     */
    Map<String, Object> analyzeDecisionImpact(String decisionId);

    /**
     * 检查决策的策略合规门（govern 步骤）。
     *
     * @param decisionId 决策 ID
     * @return 合规检查结果（compliant + violations）
     */
    Map<String, Object> checkDecisionRules(String decisionId);

    /**
     * 记录溯源条目（audit 步骤）。
     *
     * @param entityType 实体类型：decision/fact/rule
     * @param entityId   实体 ID
     * @param sourceType 来源类型：KG/RULE/RAG/LLM/MANUAL
     * @param sourceRef  来源引用
     * @param agent      执行 Agent
     * @param activity   活动：record/link/reason
     * @return 溯源条目 ID
     */
    String recordProvenance(String entityType, String entityId, String sourceType,
                            String sourceRef, String agent, String activity);

    /**
     * 查询溯源记录（audit 步骤）。
     *
     * @param entityType 实体类型
     * @param entityId   实体 ID
     * @return 溯源条目列表
     */
    List<ProvenanceEntry> queryProvenance(String entityType, String entityId);
}
