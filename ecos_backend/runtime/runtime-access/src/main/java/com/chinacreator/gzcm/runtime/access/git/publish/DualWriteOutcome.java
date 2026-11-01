package com.chinacreator.gzcm.runtime.access.git.publish;

/**
 * 一次发布的<b>三腿分别记录</b>载体（§2.9.6① 红线；#147D）。
 *
 * <p>本类是「Git 提交成功 ≠ DB 应用成功 ≠ 安全审计通过」这句话的数据形态：三条腿各存一个
 * {@link LegOutcome}，外加三条腿各自的<b>正向证据指针</b>——
 * {@code gitRef}（归档腿）、{@code versionRef}（权威库腿的版本引用）、{@code taskId}（恢复/补偿腿的
 * 持久化任务号）。<b>证据指针为空即视为该腿未确证</b>：{@code gitLeg=SUCCEEDED} 而
 * {@code gitRef} 空属自相矛盾，由 {@code PublishStateMachine.deriveState} 判成
 * {@link PublishState#PARTIAL_FAILURE}（伪成功禁止），并由 §3.1.4 DF-5 的「缺
 * version_no/git_ref 拒发布」门面在外层拦住。</p>
 *
 * <p><b>{@code taskId} 的诚实口径</b>：§2.9.6② 要求「领域 API 超时→持久化发布任务
 * （runtime-task 真实 {@code taskId}）」。当前本体侧的持久化载体是引擎自有补偿队列表
 * （{@code GitArchivePendingRepository.enqueue} 返回的队列行 ID），<b>不是</b> runtime-task 的
 * taskId——本字段照实存那个队列 ID，其语义差由 {@link #taskHandleKind()} 显式声明，
 * 台账登记为余项（禁把补偿队列 ID 冒充 runtime-task 任务号）。</p>
 *
 * <p>不可变值对象；用 {@link #builder(LegOutcome, LegOutcome, LegOutcome)} 构造。</p>
 *
 * @author ECOS Runtime底座 #147D
 */
public final class DualWriteOutcome {

    /** 补偿队列 ID 作为任务句柄时的种类标识（非 runtime-task taskId）。 */
    public static final String KIND_COMPENSATION_QUEUE = "COMPENSATION_QUEUE";

    /** runtime-task 任务号种类标识（尚未接入，见 {@link #KIND_COMPENSATION_QUEUE} 的对照）。 */
    public static final String KIND_RUNTIME_TASK = "RUNTIME_TASK";

    private final LegOutcome gitLeg;
    private final LegOutcome dbLeg;
    private final LegOutcome auditLeg;
    private final String gitRef;
    private final String versionRef;
    private final String taskId;
    private final String taskHandleKind;
    private final String reason;
    private final boolean securityCritical;

    private DualWriteOutcome(Builder b) {
        this.gitLeg = b.gitLeg;
        this.dbLeg = b.dbLeg;
        this.auditLeg = b.auditLeg;
        this.gitRef = trimToNull(b.gitRef);
        this.versionRef = trimToNull(b.versionRef);
        this.taskId = trimToNull(b.taskId);
        this.taskHandleKind = trimToNull(b.taskHandleKind);
        this.reason = trimToNull(b.reason);
        this.securityCritical = b.securityCritical;
    }

    public static Builder builder(LegOutcome gitLeg, LegOutcome dbLeg, LegOutcome auditLeg) {
        return new Builder(gitLeg, dbLeg, auditLeg);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public LegOutcome gitLeg() {
        return gitLeg;
    }

    public LegOutcome dbLeg() {
        return dbLeg;
    }

    public LegOutcome auditLeg() {
        return auditLeg;
    }

    /** 归档腿的正向证据（真实 commit sha 或 tag）。 */
    public String gitRef() {
        return gitRef;
    }

    /** 权威库腿的版本引用（version_no 或版本行 ID）。 */
    public String versionRef() {
        return versionRef;
    }

    /** 恢复/补偿的持久化任务句柄（见类注释的诚实口径）。 */
    public String taskId() {
        return taskId;
    }

    public String taskHandleKind() {
        return taskHandleKind;
    }

    /** 人读原因（失败/未知/不适用三态均应有；缺失只说明记录不到位，不改判）。 */
    public String reason() {
        return reason;
    }

    /**
     * 是否关键安全变更。{@code true} 时审计腿未确证即 fail-closed
     * （§2.9.6②「审计不可用→关键安全变更 fail-closed」）。
     */
    public boolean securityCritical() {
        return securityCritical;
    }

    /** 三腿是否全部「无否定证据且无未知」。 */
    public boolean allLegsCleared() {
        return gitLeg.isCleared() && dbLeg.isCleared() && auditLeg.isCleared()
                && !gitLeg.isIndeterminate() && !dbLeg.isIndeterminate() && !auditLeg.isIndeterminate();
    }

    /** 是否存在<b>确认失败</b>的腿。 */
    public boolean anyLegFailed() {
        return gitLeg.isFailure() || dbLeg.isFailure() || auditLeg.isFailure();
    }

    /** 是否存在<b>结果未知</b>的腿（须凭 {@link #taskId()} 对账后定论）。 */
    public boolean anyLegUnknown() {
        return gitLeg.isIndeterminate() || dbLeg.isIndeterminate() || auditLeg.isIndeterminate();
    }

    /** 是否<b>至少一条</b>腿确认成功（全腿皆败时不是「部分」，是「全败」）。 */
    public boolean anyLegSucceeded() {
        return gitLeg.isSuccess() || dbLeg.isSuccess();
    }

    /** 归档腿是否给出了可用 ref（{@code SUCCEEDED} 却无 ref ＝ 伪成功）。 */
    public boolean gitEvidencePresent() {
        return gitRef != null;
    }

    /** 权威库腿是否给出了版本引用。 */
    public boolean versionEvidencePresent() {
        return versionRef != null;
    }

    /** 单行摘要，供消费侧日志/VO 的 reason 字段拼装（不含敏感正文）。 */
    public String legSummary() {
        return "git=" + gitLeg + (gitRef == null ? "" : "(" + gitRef + ")")
                + ", db=" + dbLeg + (versionRef == null ? "" : "(" + versionRef + ")")
                + ", audit=" + auditLeg
                + (taskId == null ? "" : ", task=" + taskId + "@" + taskHandleKind);
    }

    /** 可变构造器（腿结果必填，证据与原因选填；禁无参构造造出半空对象）。 */
    public static final class Builder {
        private final LegOutcome gitLeg;
        private final LegOutcome dbLeg;
        private final LegOutcome auditLeg;
        private String gitRef;
        private String versionRef;
        private String taskId;
        private String taskHandleKind;
        private String reason;
        private boolean securityCritical;

        private Builder(LegOutcome gitLeg, LegOutcome dbLeg, LegOutcome auditLeg) {
            if (gitLeg == null || dbLeg == null || auditLeg == null) {
                throw new IllegalArgumentException("三腿结果必填（§2.9.6① 分别记录，禁缺省折叠）");
            }
            this.gitLeg = gitLeg;
            this.dbLeg = dbLeg;
            this.auditLeg = auditLeg;
        }

        public Builder gitRef(String gitRef) {
            this.gitRef = gitRef;
            return this;
        }

        public Builder versionRef(String versionRef) {
            this.versionRef = versionRef;
            return this;
        }

        public Builder task(String taskId, String taskHandleKind) {
            this.taskId = taskId;
            this.taskHandleKind = taskHandleKind;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder securityCritical(boolean securityCritical) {
            this.securityCritical = securityCritical;
            return this;
        }

        public DualWriteOutcome build() {
            return new DualWriteOutcome(this);
        }
    }
}
