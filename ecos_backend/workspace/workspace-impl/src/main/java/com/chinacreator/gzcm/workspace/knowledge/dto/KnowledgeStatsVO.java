package com.chinacreator.gzcm.workspace.knowledge.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识工作台统计 VO — E-A#2 产物。
 *
 * <p>12 项只读计数 + lastUpdatedAt，全部来自 PG（ecos_knowledge schema + public schema），
 * 单表 COUNT 失败时按 0 占位（不 500、不抛错），前端展示 ~ 提示表未建。
 *
 * @author ecos-factory
 * @since PMO-55 批次 E-A
 */
public class KnowledgeStatsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 图谱节点数（ecos_knowledge.graph_node） */
    private Long graphNodeCount;

    /** 图谱关系/边数（ecos_knowledge.graph_edge） */
    private Long graphEdgeCount;

    /** 知识文章数（ecos_knowledge.knowledge_article） */
    private Long articleCount;

    /** 向量嵌入块数（ecos_knowledge.knowledge_embedding） */
    private Long embeddingCount;

    /** 合规规则总数（public.sys_compliance_rule） */
    private Long complianceRuleCount;

    /** 已激活合规规则数（status = 'ACTIVE'） */
    private Long activeComplianceRuleCount;

    /** 规则版本快照数（public.sys_rule_version） */
    private Long ruleVersionCount;

    /** 本体快照数（ecos_knowledge.kb_ontology_snapshot, is_deleted = 0） */
    private Long ontologySnapshotCount;

    /** 本体版本数（distinct ontology_id, is_deleted = 0） */
    private Long ontologyVersionCount;

    /** KG 同步日志行数（ecos_knowledge.kg_sync_log, kgSyncJobCount 复用 BASE） */
    private Long kgSyncLogCount;

    /** 认知管线定义数（kb_cognitive_pipeline, is_deleted = 0） */
    private Long cognitivePipelineCount;

    /** 血缘事件数（kb_lineage_event, is_deleted = 0） */
    private Long lineageEventCount;

    /** 已发布本体版本数（is_deleted = 0 的不同 ontology_id 数） */
    private Long publishedOntologyVersionCount;

    /** 最近一次成功同步时间（kg_sync_log 中 elapsed_at 或 created_at 的 MAX） */
    private String lastKgSyncAt;

    /** 最新本体快照 created_at */
    private String lastOntologySnapshotAt;

    /** 最近一次数据变更时间（9 张表 created_at/updated_at 的 MAX 聚合） */
    private String lastUpdatedAt;

    /** 向量维数（knowledge_embedding.embedding_vec 列维数，pgvector 未启用时为 null） */
    private Integer embeddingDim;

    /** 向量库覆盖文档数（knowledge_embedding 去重 document_id / article_id） */
    private Long docCount;

    public Long getGraphNodeCount() {
        return graphNodeCount;
    }

    public void setGraphNodeCount(Long graphNodeCount) {
        this.graphNodeCount = graphNodeCount;
    }

    public Long getGraphEdgeCount() {
        return graphEdgeCount;
    }

    public void setGraphEdgeCount(Long graphEdgeCount) {
        this.graphEdgeCount = graphEdgeCount;
    }

    public Long getArticleCount() {
        return articleCount;
    }

    public void setArticleCount(Long articleCount) {
        this.articleCount = articleCount;
    }

    public Long getEmbeddingCount() {
        return embeddingCount;
    }

    public void setEmbeddingCount(Long embeddingCount) {
        this.embeddingCount = embeddingCount;
    }

    public Long getComplianceRuleCount() {
        return complianceRuleCount;
    }

    public void setComplianceRuleCount(Long complianceRuleCount) {
        this.complianceRuleCount = complianceRuleCount;
    }

    public Long getActiveComplianceRuleCount() {
        return activeComplianceRuleCount;
    }

    public void setActiveComplianceRuleCount(Long activeComplianceRuleCount) {
        this.activeComplianceRuleCount = activeComplianceRuleCount;
    }

    public Long getRuleVersionCount() {
        return ruleVersionCount;
    }

    public void setRuleVersionCount(Long ruleVersionCount) {
        this.ruleVersionCount = ruleVersionCount;
    }

    public Long getOntologySnapshotCount() {
        return ontologySnapshotCount;
    }

    public void setOntologySnapshotCount(Long ontologySnapshotCount) {
        this.ontologySnapshotCount = ontologySnapshotCount;
    }

    public Long getOntologyVersionCount() {
        return ontologyVersionCount;
    }

    public void setOntologyVersionCount(Long ontologyVersionCount) {
        this.ontologyVersionCount = ontologyVersionCount;
    }

    public Long getKgSyncLogCount() {
        return kgSyncLogCount;
    }

    public void setKgSyncLogCount(Long kgSyncLogCount) {
        this.kgSyncLogCount = kgSyncLogCount;
    }

    public Long getCognitivePipelineCount() {
        return cognitivePipelineCount;
    }

    public void setCognitivePipelineCount(Long cognitivePipelineCount) {
        this.cognitivePipelineCount = cognitivePipelineCount;
    }

    public Long getLineageEventCount() {
        return lineageEventCount;
    }

    public void setLineageEventCount(Long lineageEventCount) {
        this.lineageEventCount = lineageEventCount;
    }

    public Long getPublishedOntologyVersionCount() {
        return publishedOntologyVersionCount;
    }

    public void setPublishedOntologyVersionCount(Long publishedOntologyVersionCount) {
        this.publishedOntologyVersionCount = publishedOntologyVersionCount;
    }

    public String getLastKgSyncAt() {
        return lastKgSyncAt;
    }

    public void setLastKgSyncAt(String lastKgSyncAt) {
        this.lastKgSyncAt = lastKgSyncAt;
    }

    public String getLastOntologySnapshotAt() {
        return lastOntologySnapshotAt;
    }

    public void setLastOntologySnapshotAt(String lastOntologySnapshotAt) {
        this.lastOntologySnapshotAt = lastOntologySnapshotAt;
    }

    public String getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(String lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public Integer getEmbeddingDim() {
        return embeddingDim;
    }

    public void setEmbeddingDim(Integer embeddingDim) {
        this.embeddingDim = embeddingDim;
    }

    public Long getDocCount() {
        return docCount;
    }

    public void setDocCount(Long docCount) {
        this.docCount = docCount;
    }
}
