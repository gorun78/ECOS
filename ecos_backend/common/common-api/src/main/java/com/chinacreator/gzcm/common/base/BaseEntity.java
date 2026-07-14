package com.chinacreator.gzcm.common.base;

import java.io.Serial;

/**
 * 所有实体的抽象基类。
 * <p>
 * 提供统一的审计字段：创建时间/创建人/更新时间/更新人。
 * 推荐所有数据库实体继承此类。
 */
public abstract class BaseEntity {

    /** 创建时间 (ISO-8601) */
    protected String createdAt;

    /** 创建人 ID */
    protected String createdBy;

    /** 更新时间 (ISO-8601) */
    protected String updatedAt;

    /** 更新人 ID */
    protected String updatedBy;

    // ── getter/setter ───────────────────────────────

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
