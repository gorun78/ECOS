package com.chinacreator.gzcm.runtime.access.git.publish;

/**
 * 发布 / 双写状态机的<b>唯一状态词表</b>（《ECOS补充详细设计说明书-R7至R11-2026-10-16.md》
 * §2.9.6①，验收 §3.1.4 DF-1；#147D）。
 *
 * <p><b>16 态 = 主链 9 + 旁支 7</b>，与 §2.9.6① 的图示逐字同构（禁增删、禁改名、禁在他处
 * 另立同义状态词）：
 * <pre>
 * 主链: DRAFT → VALIDATING → PENDING_APPROVAL → APPROVED → READY_TO_APPLY
 *       → APPLYING → APPLIED → VERIFYING → SUCCEEDED
 * 旁支: REJECTED / PARTIAL_FAILURE / FAILED / ROLLBACK_PENDING / ROLLING_BACK
 *       / ROLLED_BACK / DRIFTED
 * </pre>
 *
 * <p><b>为什么必须有 {@code PARTIAL_FAILURE} 这一枚</b>（§2.9.6① 红线）：Git 与 DB 属不同持久化
 * 系统，<b>不构成天然单一原子事务</b>；{@code Git 提交成功 ≠ DB 应用成功 ≠ 安全审计通过}，
 * 三者分别记录，<b>禁把部分成功报完全成功</b>。收口前的真实形态是「DB 行改成已发布即报已发布，
 * Git 归档失败只落一条 warn」——本枚举的存在就是为了让那种报法不可表达。</p>
 *
 * <p><b>词表单源</b>：本枚举 16 枚名称是仓内唯一合法持有面，由
 * {@code PublishStateMachineSingleSourceGateTest}（common-api 仓扫门）钉住——其中 7 枚
 * （{@code READY_TO_APPLY}/{@code APPLYING}/{@code VERIFYING}/{@code PARTIAL_FAILURE}/
 * {@code ROLLBACK_PENDING}/{@code ROLLING_BACK}/{@code DRIFTED}）在收口前全仓 main 面
 * <b>零命中</b>，故可作零容差判据；其余 9 枚（{@code DRAFT}/{@code APPROVED}/{@code SUCCEEDED} 等）
 * 与各域既有生命周期词表同号、天然不可作零容差，改由「16 枚必须齐全且只在属主文件声明」判。</p>
 *
 * @author ECOS Runtime底座 #147D
 */
public enum PublishState {

    // ── 主链 9 态（§2.9.6①）────────────────────────────────────────

    /** 变更请求已建立，尚未校验（权威库与仓库均未动）。 */
    DRAFT(1, true),

    /** 校验中：口径/依赖/值域/快照完整性（§2.9.6② 的「依赖冲突」在此暴露）。 */
    VALIDATING(2, true),

    /** 待审批：四眼/审批策略在服务端裁决中（§2.9.4① Change Governance）。 */
    PENDING_APPROVAL(3, true),

    /** 审批通过（结果必须服务端持久化，禁只活在前端态）。 */
    APPROVED(4, true),

    /** 可应用：前置门全过，等待写入腿开跑。 */
    READY_TO_APPLY(5, true),

    /** 应用中：{@code APPLYING} 是三腿分别记录的窗口，任何一腿的成败都在此收敛。 */
    APPLYING(6, true),

    /** 权威库已应用（DB 腿成功）；{@code APPLIED ≠ SUCCEEDED}——归档/审计腿尚未定。 */
    APPLIED(7, true),

    /** 验证中：对账判定尚未给出（§2.9.6③），禁在此报最终成功。 */
    VERIFYING(8, true),

    /** 完全成功：三腿均成功<b>且</b>对账判定为「一致」——唯一可对外报成功的态。 */
    SUCCEEDED(9, true),

    // ── 旁支 7 态（§2.9.6①②③）───────────────────────────────────────

    /** 审批拒绝（人工裁决结果，非失败；可退回 {@link #DRAFT} 修订重提）。 */
    REJECTED(0, false),

    /**
     * 部分失败：三腿<b>不成套</b>（任一腿确认成功而另一腿确认失败或结果未知）。
     * §2.9.6① 红线的落点——本态存在即令「部分成功」不可被报成「完全成功」。
     */
    PARTIAL_FAILURE(0, false),

    /** 已失败：无腿成功，或关键安全变更因审计不可用 fail-closed（§2.9.6②）。 */
    FAILED(0, false),

    /** 待回滚：已登记回滚意图，尚未取得可回滚的 git ref 检出计划。 */
    ROLLBACK_PENDING(0, false),

    /** 回滚中：正在「发布已知历史版本（git ref 检出重应用）」，非任意反向 SQL（§2.9.6②）。 */
    ROLLING_BACK(0, false),

    /** 已回滚（唯一终态，出边全无）：回滚完成后本请求不得再推进。 */
    ROLLED_BACK(0, false),

    /** 已漂移：对账判定为内容漂移/版本引用不匹配/依赖不一致（§2.9.6③），须重应用或回滚。 */
    DRIFTED(0, false);

    /** 主链序号（1~9）；旁支态为 {@code 0}，表示不在主链上。 */
    private final int mainChainOrder;

    /** 是否属主链态（主链单向推进，禁跳跃；旁支是失败/恢复轨）。 */
    private final boolean mainChain;

    PublishState(int mainChainOrder, boolean mainChain) {
        this.mainChainOrder = mainChainOrder;
        this.mainChain = mainChain;
    }

    public int mainChainOrder() {
        return mainChainOrder;
    }

    public boolean isMainChain() {
        return mainChain;
    }

    /**
     * 失败/异常轨判定（{@code SUCCEEDED} 与主链中间态不算失败）。
     * 旁支 7 枚里除 {@code REJECTED} 外都是「需要恢复动作」的失败族——
     * 拒绝是业务裁决，不是执行失败，故不并入。
     */
    public boolean isFailureFamily() {
        return this == PARTIAL_FAILURE || this == FAILED || this == DRIFTED
                || this == ROLLBACK_PENDING || this == ROLLING_BACK || this == ROLLED_BACK;
    }

    /** 结果是否已定（既非「进行中」也非「待验证」）——供报成功前的最后一道自检。 */
    public boolean isSettled() {
        return this == SUCCEEDED || this == REJECTED || isFailureFamily();
    }

    /**
     * 三腿均已确认成功的必要条件位（{@link #SUCCEEDED} 之外的任何态都不许报「完全成功」）。
     */
    public boolean mayReportFullSuccess() {
        return this == SUCCEEDED;
    }
}
