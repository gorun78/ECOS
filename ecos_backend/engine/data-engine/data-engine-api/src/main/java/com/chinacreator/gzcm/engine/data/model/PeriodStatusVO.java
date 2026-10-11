package com.chinacreator.gzcm.engine.data.model;

/**
 * 期间状态视图（详细设计-09 v1.2 §十一 H-3 交付①；表 V278 {@code ecos_data.ecos_data_period_status}）。
 *
 * <p>{@code GET /api/v1/datanet/facts/periods/{yyyMM}} 响应；无行=未登账期间，按 OPEN 呈现
 * （与 PeriodLock 端口"关账=显式登记"语义一致，禁把缺省推断为 CLOSED）。
 */
public class PeriodStatusVO {

    /** 期间键 YYYY-MM（归一后）。 */
    private String period;

    /** OPEN / CLOSED（无登记表行时恒 OPEN）。 */
    private String status;

    /** 关账操作者（OPA 裁决通过的财务侧身份；OPEN 时 null）。 */
    private String closedBy;

    /** 关账时点 ISO_LOCAL_DATE_TIME（OPEN 时 null）。 */
    private String closedAt;

    /** 关账说明（月结/审批单号等；OPEN 时 null）。 */
    private String closeReason;

    /** 当前判定是否来自 DB 真身（false=降级到配置期锁形态，排障用）。 */
    private boolean degraded;

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public String getClosedAt() { return closedAt; }
    public void setClosedAt(String closedAt) { this.closedAt = closedAt; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
    public boolean isDegraded() { return degraded; }
    public void setDegraded(boolean degraded) { this.degraded = degraded; }
}
