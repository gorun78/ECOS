package com.chinacreator.gzcm.engine.kb.nav.model;

/**
 * 知识导航·目录 Create/Update 请求 DTO（强类型，禁 Map）。
 *
 * <p>字段与 {@code ecos_knowledge.kb_nav_category} 对齐；create 时 level 由
 * parentId 推断（null → 1，一级目录下二级，二级下三级）；
 * update 时 level 不可变更，靠 {@link #sortOrder} 调序。</p>
 * <p>GETTER/SETTER 手写，保持 api 模块与 lombok 无关，避免传递依赖污染契约层。</p>
 */
public class NavCategorySaveDTO {

    /** 多租户预留，默认 "default"；不可为空 */
    private String domain;

    /** 父目录 ID；根（一级）目录为 null 或空串 */
    private String parentId;

    /** 目录名（同 domain 下唯一） */
    private String name;

    /** 同级排序（小者先），默认 0 */
    private Integer sortOrder;

    public NavCategorySaveDTO() {
    }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
