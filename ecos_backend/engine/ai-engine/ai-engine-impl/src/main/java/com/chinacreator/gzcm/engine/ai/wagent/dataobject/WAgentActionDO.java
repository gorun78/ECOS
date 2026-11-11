package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.time.LocalDate;

/**
 * {@code ecos_ai.ecos_wagent_action}（V283 建表）行对象。
 *
 * <p>status 值域尚未在 DDL 收紧（V283 头注：待枚举单源后补 CHECK），本类只承载列，
 * 状态字面量一律由调用处常量给出，禁散落字符串。</p>
 */
public class WAgentActionDO extends WAgentBaseDO {

    private String id;
    private String optionId;
    private String title;
    private String ownerId;
    private String role;
    private LocalDate dueDate;
    private String kpiText;
    private String sourceType;
    private String sourceObjectId;
    private String publishTarget;
    private String publishMode;
    private String status;
    private String approvalToken;
    private String traceId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getKpiText() {
        return kpiText;
    }

    public void setKpiText(String kpiText) {
        this.kpiText = kpiText;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceObjectId() {
        return sourceObjectId;
    }

    public void setSourceObjectId(String sourceObjectId) {
        this.sourceObjectId = sourceObjectId;
    }

    public String getPublishTarget() {
        return publishTarget;
    }

    public void setPublishTarget(String publishTarget) {
        this.publishTarget = publishTarget;
    }

    public String getPublishMode() {
        return publishMode;
    }

    public void setPublishMode(String publishMode) {
        this.publishMode = publishMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApprovalToken() {
        return approvalToken;
    }

    public void setApprovalToken(String approvalToken) {
        this.approvalToken = approvalToken;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
