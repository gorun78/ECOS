package com.chinacreator.gzcm.engine.kb.nav.model;

import java.util.List;

/**
 * 知识导航·资产分页查询 DTO（{@code GET /api/v1/knowledge/nav/products}）。
 *
 * <p>强类型查询条件：keyword 模糊 + categoryIds 多对多 + tags 多标签，三者全空返回全量。
 * 命中语义：categoryIds 任一（子树展开）AND tags 任一 AND keyword ILIKE
 * （{@code knowledge_article.title/content}）。</p>
 */
public class NavFolderQuery {

    /** 模糊关键词，匹配 title OR content（ILIKE），可空 */
    private String keyword;

    /** 组织层级过滤（1/2/3），仅语义占位（实际匹配走 categoryIds） */
    private Integer level;

    /** 多租户预留（默认 "default"），可空 */
    private String domain;

    /** 类型过滤（如 ART/DOC/RULE），可空；由 impl 映射 status/source_type 组合 */
    private String type;

    /** 目录 ID 集合 — 任一命中（含子树展开），可空 */
    private List<String> categoryIds;

    /** 标签名集合 — 任一命中，可空 */
    private List<String> tags;

    /** 页码（从 1 起），默认 1 */
    private Integer pageNum;

    /** 每页条数，默认 20，上限 100 */
    private Integer pageSize;

    public NavFolderQuery() {
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<String> getCategoryIds() { return categoryIds; }
    public void setCategoryIds(List<String> categoryIds) { this.categoryIds = categoryIds; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
