package com.chinacreator.gzcm.workspace.controller;

/**
 * 查询历史删除结果 VO（PMO-74 H11-T4）。
 *
 * <p>替代 {@code QueryHistoryController#delete} 原裸返 {@code Map.of("deleted", true, "id", id)}，
 * JSON 形态与原 Map 完全一致：{@code {"deleted":true,"id":"<recordId>"}}（字段只增不删）。</p>
 */
public class QueryHistoryDeleteVO {

    /** 是否删除成功 */
    private boolean deleted;

    /** 被删除记录 id */
    private String id;

    public QueryHistoryDeleteVO() {
    }

    public QueryHistoryDeleteVO(boolean deleted, String id) {
        this.deleted = deleted;
        this.id = id;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
