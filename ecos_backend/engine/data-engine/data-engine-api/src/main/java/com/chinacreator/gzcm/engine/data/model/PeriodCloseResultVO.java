package com.chinacreator.gzcm.engine.data.model;

/**
 * 期间关账结果（详细设计-09 v1.2 §十一 H-3 交付①）。
 *
 * <p>幂等契约：对已 CLOSED 期间重复 close → HTTP 200 + {@code alreadyClosed=true}，
 * <b>不</b>产生第二次 {@code ecos.audit} 事件、不重复更新 closed_at/closed_by（首写留痕）。
 */
public class PeriodCloseResultVO {

    /** 归一后期间键 YYYY-MM。 */
    private String period;

    /** 关账后状态（恒 CLOSED；成功是唯一非异常出口）。 */
    private String status;

    /** 关账操作者（首写留痕；重复 close 不改写）。 */
    private String closedBy;

    /** 关账时点 ISO_LOCAL_DATE_TIME（首写留痕）。 */
    private String closedAt;

    /** true = 本次调用命中既有 CLOSED 行（幂等回放，零写入零审计）。 */
    private boolean alreadyClosed;

    /** 关账说明回显。 */
    private String reason;

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public String getClosedAt() { return closedAt; }
    public void setClosedAt(String closedAt) { this.closedAt = closedAt; }
    public boolean isAlreadyClosed() { return alreadyClosed; }
    public void setAlreadyClosed(boolean alreadyClosed) { this.alreadyClosed = alreadyClosed; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
