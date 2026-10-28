package com.chinacreator.gzcm.engine.kb.dto;

import java.util.List;

/**
 * 混合召回应答（附录 A 第 14 行 {@code {hits[],vectorDegraded,maskedColumns[]}}）。
 *
 * <p><b>如实声明（禁假值）</b>：契约列的 {@code maskedColumns} 要求安全侧返回列级脱敏结果，
 * 而 kb 检索链只做 ABAC 操作裁决（{@code kb.retrieval.search}），既未接 RLS/列过滤也未持
 * 列面载体 ⇒ 本字段恒为<b>空表</b>，并以 {@link #maskedColumnsApplicable}＝{@code false}
 * 显式标注"本端点不做列脱敏"，避免消费方把空表读成"已脱敏、无敏感列"。</p>
 */
public class KnowledgeRetrievalSearchResultVO {

    /** 命中列表（按召回顺序）。 */
    private List<KnowledgeRetrievalHitVO> hits;

    /** 向量形态是否降级（湖规"降级须显式标记"，K-33）。 */
    private boolean vectorDegraded;

    /** 降级原因（未降级时为空）；与 {@link #vectorDegraded} 同帧透出，禁把降级洗成零命中。 */
    private String vectorDegradedReason;

    /** 列级脱敏结果（本端点无载体 ⇒ 恒空表，见类注释）。 */
    private List<String> maskedColumns;

    /** 本端点是否具备列脱敏能力（恒 {@code false}，如实标注而非以空表冒充）。 */
    private boolean maskedColumnsApplicable;

    /** 本端点是否具备引用锚能力（恒 {@code false}，逐条 {@code citations} 亦恒空）。 */
    private boolean citationsApplicable;

    public List<KnowledgeRetrievalHitVO> getHits() { return hits; }

    public void setHits(List<KnowledgeRetrievalHitVO> hits) { this.hits = hits; }

    public boolean isVectorDegraded() { return vectorDegraded; }

    public void setVectorDegraded(boolean vectorDegraded) { this.vectorDegraded = vectorDegraded; }

    public String getVectorDegradedReason() { return vectorDegradedReason; }

    public void setVectorDegradedReason(String vectorDegradedReason) {
        this.vectorDegradedReason = vectorDegradedReason;
    }

    public List<String> getMaskedColumns() { return maskedColumns; }

    public void setMaskedColumns(List<String> maskedColumns) { this.maskedColumns = maskedColumns; }

    public boolean isMaskedColumnsApplicable() { return maskedColumnsApplicable; }

    public void setMaskedColumnsApplicable(boolean maskedColumnsApplicable) {
        this.maskedColumnsApplicable = maskedColumnsApplicable;
    }

    public boolean isCitationsApplicable() { return citationsApplicable; }

    public void setCitationsApplicable(boolean citationsApplicable) {
        this.citationsApplicable = citationsApplicable;
    }
}
