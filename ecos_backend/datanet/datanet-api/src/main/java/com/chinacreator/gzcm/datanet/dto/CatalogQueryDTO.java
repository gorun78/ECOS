package com.chinacreator.gzcm.datanet.dto;

/**
 * 目录查询参数 DTO。
 *
 * @author DataBridge Datanet Team
 */
public class CatalogQueryDTO {

    /** 搜索关键词（模糊匹配资源名称/描述/标签） */
    private String keyword;

    /** 资源类型过滤: TABLE, VIEW, API, FILE */
    private String resourceType;

    /** 所属部门过滤 */
    private String orgId;

    /** 分类路径过滤（支持前缀匹配） */
    private String categoryPath;

    /** 分页页码（从1开始） */
    private Integer page = 1;

    /** 每页条数 */
    private Integer pageSize = 20;

    // ===== Getters/Setters =====

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public String getCategoryPath() { return categoryPath; }
    public void setCategoryPath(String categoryPath) { this.categoryPath = categoryPath; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
