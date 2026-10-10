package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.time.LocalDateTime;

/**
 * W Agent 控制面表通用列基类（详细设计-10 §6.1 通用列模板 DR05~DR08；V227~V240 全部 16 表齐列）。
 *
 * <p>列口径：{@code tenant_id VARCHAR(64) NOT NULL}（REQ-PLT-24/25 承，R-36 挂账）、
 * {@code org_id VARCHAR(64)}、{@code domain VARCHAR(50) DEFAULT 'default'}（DR08）、
 * {@code version_no VARCHAR(20) NOT NULL}（DR07，DDL 形态为字符串版本位）、
 * {@code create_time/update_time TIMESTAMP}、{@code create_by/update_by VARCHAR(100)}、
 * {@code is_deleted SMALLINT DEFAULT 0}（DR05）。</p>
 *
 * <p>BeanPropertyRowMapper 经继承的属性描述符映射（snake_case→camelCase），DAO 侧 COLUMNS
 * 显式列名禁 SELECT *。RLS/CLS 过滤在 security-engine REST 侧，本层不写策略（ST04/05）。</p>
 *
 * <p>文档口径包名 {@code com.chinacreator.gzcm.ai.wagent} 与本包属同义（F10-01）。</p>
 */
public abstract class WAgentBaseDO {

    private String tenantId;
    private String orgId;
    private String domain;
    private String versionNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
    private Integer isDeleted;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
}
