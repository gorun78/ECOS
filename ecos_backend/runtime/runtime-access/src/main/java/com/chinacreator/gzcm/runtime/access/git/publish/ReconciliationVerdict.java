package com.chinacreator.gzcm.runtime.access.git.publish;

/**
 * 对账判定词表——§2.9.6③「对账 8 类判定」的<b>唯一状态载体</b>（#147D）。
 *
 * <p>设计原文逐字列出八类：{@code 一致 / 缺失对象 / 多余对象 / 内容漂移 / 版本引用不匹配 /
 * 部分发布 / 依赖不一致 / 无法验证}。本枚举按同一顺序、同一枚数建模，中文标签留在
 * {@link #label()} 里，便于对账报告直接呈现而无需在别处再维护一份中文映射。</p>
 *
 * <p><b>为什么「一致」必须由判定给出、而不能由「没发现别的问题」默认得出</b>：
 * {@link #UNVERIFIABLE} 与 {@link #CONSISTENT} 是两类结论。收口前的真实形态是「拿不到仓库侧
 * 内容就当无差异」，那正是把「没查」报成「查过且一致」的假绿。故
 * {@code PublishReconciler} 在证据缺失时一律判 {@link #UNVERIFIABLE}，
 * 只有双侧内容都取到且逐项对齐才可能返回 {@link #CONSISTENT}。</p>
 *
 * <p><b>自动修复门</b>：§2.9.6③ 末句「自动修复不得绕过审批」——本枚举用
 * {@link #autoFixableWithoutApproval()} 恒为 {@code false} 把这条红线编进类型面：
 * 不存在「可不经审批自动修复」的判定，任何修复动作都须回到
 * {@link PublishStateMachine} 的审批链上（{@code DRAFT → VALIDATING → PENDING_APPROVAL →
 * APPROVED}）才可执行。</p>
 *
 * @author ECOS Runtime底座 #147D
 */
public enum ReconciliationVerdict {

    /** 一致：权威库与仓库侧内容、引用、依赖三项全对齐（唯一可推进到成功的判定）。 */
    CONSISTENT("一致"),

    /** 缺失对象：权威库有而仓库快照没有（归档腿漏写或写入被截断）。 */
    MISSING_OBJECT("缺失对象"),

    /** 多余对象：仓库快照有而权威库没有（回滚未净、或写侧失败后残留）。 */
    EXTRA_OBJECT("多余对象"),

    /** 内容漂移：对象集合同构但字段/规则内容摘要不等（双写非原子留下的典型形态）。 */
    CONTENT_DRIFT("内容漂移"),

    /** 版本引用不匹配：{@code version_no}/{@code git_ref} 与另一侧记录的不是同一版本。 */
    VERSION_REF_MISMATCH("版本引用不匹配"),

    /** 部分发布：三腿不成套即进入的对账结论，与 {@link PublishState#PARTIAL_FAILURE} 同源。 */
    PARTIAL_PUBLISH("部分发布"),

    /** 依赖不一致：跨对象依赖（关系/引用指向）在两侧指向不同目标。 */
    DEPENDENCY_MISMATCH("依赖不一致"),

    /** 无法验证：一侧或双侧证据取不到（仓库未注册、内容按 ref 读回能力缺失、快照为空）。 */
    UNVERIFIABLE("无法验证");

    private final String label;

    ReconciliationVerdict(String label) {
        this.label = label;
    }

    /** §2.9.6③ 中文原词（单源，禁在消费侧另写一份）。 */
    public String label() {
        return label;
    }

    /** 是否「对齐」结论——只有 {@link #CONSISTENT} 是。 */
    public boolean isConsistent() {
        return this == CONSISTENT;
    }

    /**
     * 是否「差异确已确认」（区别于 {@link #UNVERIFIABLE} 的「未查/查不到」）。
     * 供失败恢复分派：确认差异走重应用或回滚，查不到走补证据。
     */
    public boolean isConfirmedDifference() {
        return this != CONSISTENT && this != UNVERIFIABLE;
    }

    /**
     * 红线载体的类型面表达：§2.9.6③「自动修复不得绕过审批」——
     * <b>任何</b>判定都不允许不经审批链自动改写权威库或仓库，故恒为 {@code false}。
     */
    public boolean autoFixableWithoutApproval() {
        return false;
    }

    /**
     * 该判定在 {@link PublishStateMachine#stateAfterVerification} 里是否把已发布态打回
     * {@link PublishState#DRIFTED}（漂移族＝内容/引用/依赖三类确证的"两边说的不是同一件事"）。
     */
    public boolean isDriftFamily() {
        return this == CONTENT_DRIFT
                || this == VERSION_REF_MISMATCH
                || this == DEPENDENCY_MISMATCH;
    }
}
