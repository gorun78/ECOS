package com.chinacreator.gzcm.engine.kb.nav.model;

import java.time.Instant;
import java.util.List;

/**
 * 知识导航·LLM 归类建议 VO（{@code GET /api/v1/knowledge/nav/recommend/{articleId}}）。
 *
 * <p><b>仅建议，不落库</b>：失败 / 不可用时返回 {@code tags=[] /
 * suggestedCategoryId=null / reason="llm-gateway 不可用 或 解析失败"}，
 * 调用方自行决定是否写入 {@code kb_nav_article_rel}。</p>
 */
public class NavRecommendVO {

    /** 建议的标签集合（≤5，LLM 失败 / 解析失败时为空列表） */
    private List<String> tags;

    /** 建议的目录 ID（按 name 命中 kb_nav_category 中存在节点才返回，未命中为 null） */
    private String suggestedCategoryId;

    /** 建议的目录名（与 {@link #suggestedCategoryId} 对偶） */
    private String suggestedCategoryName;

    /** 降级 / 错误说明（成功且无建议时为空） */
    private String reason;

    /** LLM 调用 token 数（不可用 / 降级时为 null） */
    private Integer tokens;

    /** 建议生成时间 */
    private Instant generatedAt;

    public NavRecommendVO() {
    }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getSuggestedCategoryId() { return suggestedCategoryId; }
    public void setSuggestedCategoryId(String suggestedCategoryId) { this.suggestedCategoryId = suggestedCategoryId; }

    public String getSuggestedCategoryName() { return suggestedCategoryName; }
    public void setSuggestedCategoryName(String suggestedCategoryName) { this.suggestedCategoryName = suggestedCategoryName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Integer getTokens() { return tokens; }
    public void setTokens(Integer tokens) { this.tokens = tokens; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
}
