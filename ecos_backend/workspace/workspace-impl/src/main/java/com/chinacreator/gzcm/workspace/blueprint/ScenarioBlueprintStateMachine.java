package com.chinacreator.gzcm.workspace.blueprint;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 场景蓝图状态机（补充设计 v1.0 §6.3 / 八项建议 #3，<b>SUP-BP-01</b> 唯一规则持有者）。
 *
 * <p>主链 <b>11 态单向</b>（<b>DRAFT 直进 ACTIVE 必拒</b>，§6.3；错误码 {@code DRAFT_DIRECT_ACTIVE_FORBIDDEN}）：
 * DRAFT → DISCOVERING → GAP_ANALYSIS → BLUEPRINT_READY → VALIDATING →
 * READY_FOR_APPROVAL → APPROVED → RELEASED → ACTIVE → RUNNING → COMPLETED。
 * 终端态（出边全无，§6.3）：{@code COMPLETED} / {@code DEPRECATED}。</p>
 *
 * <p>异常/回退态语义（§6.3）：
 * <ul>
 *   <li>{@code NEEDS_CLARIFICATION}：业务目标/口径/权威来源不明确 — 澄清后返回进入前的主链态
 *       （DRAFT/DISCOVERING/GAP_ANALYSIS/BLUEPRINT_READY）；</li>
 *   <li>{@code BLOCKED}：权限/必需数据/能力/依赖/安全策略不满足 — 任一非终态、非发布区可入；
 *       解除后按入口返回对应主链态（不可跳跃推进主链）；</li>
 *   <li>{@code VALIDATION_FAILED}：测试/回放未通过 — 返回 VALIDATING（重跑测试），
 *       或经 GAP_ANALYSIS/BLUEPRINT_READY 回退步重析重编；</li>
 *   <li>{@code PAUSED}：预算耗尽/人工干预/运行风险 — 仅 ACTIVE/RUNNING 可入，恢复回 ACTIVE 或 RUNNING；</li>
 *   <li>{@code DEPRECATED}：被 version+1 新版本替代 — <b>只读终态</b>（§6.6 历史版本可追溯）。</li>
 * </ul>
 *
 * <p>迁移校验回调（§6.3「READY_FOR_APPROVAL 前 BLOCKER gap 必须 RESOLVED/ACCEPTED_RISK」）：
 * {@link #setPreReadyForApprovalGate(PreReadyForApprovalGate)} 由 Service 装配校验实现；
 * 回调拒绝 → 409（{@code GATE_NOT_PASSED}），状态不变。回调未注入时视为通过
 * （供状态机纯内存单测断言迁移矩阵）。</p>
 *
 * <p>版本不可变（§6.2/§6.6）：RELEASED 起行冻结只读，修订 = Service 生成 version+1 新行
 * （旧行 transition→DEPRECATED），RELEASED/ACTIVE/RUNNING 不开编辑回退边。</p>
 *
 * <p>非法迁移一律 {@link IllegalBlueprintTransitionException}（HTTP 409，状态不变，<b>SUP-BP-01</b>）。
 * 合法迁移的<b>五要素</b>（actor/时间/原因/traceId/Blueprint 版本，<b>SUP-BP-03</b>）由 Service 双轨留痕：
 * content_json 内 {@code transitionHistory} 数组（全量历史，不建迁移表）+ 行级
 * actor/transition_reason/trace_id 列（最近一次迁移）。</p>
 */
@Component
public class ScenarioBlueprintStateMachine {

    /** BLOCKED 可返回的主链态（= BLOCKED 入口白名单，§6.3「返回前一主链态」按入口实现）。 */
    static final List<BlueprintStatus> BLOCKED_RETURN_TARGETS = List.of(
            BlueprintStatus.DRAFT,
            BlueprintStatus.DISCOVERING,
            BlueprintStatus.GAP_ANALYSIS,
            BlueprintStatus.BLUEPRINT_READY,
            BlueprintStatus.VALIDATING,
            BlueprintStatus.READY_FOR_APPROVAL,
            BlueprintStatus.APPROVED);

    /** 迁移矩阵（源态 → 合法目标态集合；§6.3 主链单向 + 异常态进出）。 */
    private static final Map<BlueprintStatus, List<BlueprintStatus>> OUT_EDGES = Map.ofEntries(
            Map.entry(BlueprintStatus.DRAFT, List.of(
                    BlueprintStatus.DISCOVERING, BlueprintStatus.NEEDS_CLARIFICATION,
                    BlueprintStatus.BLOCKED)),
            Map.entry(BlueprintStatus.DISCOVERING, List.of(
                    BlueprintStatus.GAP_ANALYSIS, BlueprintStatus.NEEDS_CLARIFICATION,
                    BlueprintStatus.BLOCKED, BlueprintStatus.DRAFT)),
            Map.entry(BlueprintStatus.GAP_ANALYSIS, List.of(
                    BlueprintStatus.BLUEPRINT_READY, BlueprintStatus.NEEDS_CLARIFICATION,
                    BlueprintStatus.BLOCKED, BlueprintStatus.DRAFT)),
            Map.entry(BlueprintStatus.BLUEPRINT_READY, List.of(
                    BlueprintStatus.VALIDATING, BlueprintStatus.GAP_ANALYSIS,
                    BlueprintStatus.NEEDS_CLARIFICATION, BlueprintStatus.BLOCKED, BlueprintStatus.DRAFT)),
            Map.entry(BlueprintStatus.VALIDATING, List.of(
                    BlueprintStatus.READY_FOR_APPROVAL, BlueprintStatus.VALIDATION_FAILED,
                    BlueprintStatus.BLOCKED)),
            Map.entry(BlueprintStatus.READY_FOR_APPROVAL, List.of(
                    BlueprintStatus.APPROVED, BlueprintStatus.VALIDATION_FAILED, BlueprintStatus.BLOCKED)),
            Map.entry(BlueprintStatus.APPROVED, List.of(
                    BlueprintStatus.RELEASED, BlueprintStatus.VALIDATION_FAILED, BlueprintStatus.BLOCKED)),
            // 发布不可变 §6.6：仅激活或版本替代，不可回退编辑、不可阻塞。
            Map.entry(BlueprintStatus.RELEASED, List.of(
                    BlueprintStatus.ACTIVE, BlueprintStatus.DEPRECATED)),
            Map.entry(BlueprintStatus.ACTIVE, List.of(
                    BlueprintStatus.RUNNING, BlueprintStatus.PAUSED, BlueprintStatus.BLOCKED,
                    BlueprintStatus.DEPRECATED)),
            Map.entry(BlueprintStatus.RUNNING, List.of(
                    BlueprintStatus.COMPLETED, BlueprintStatus.PAUSED, BlueprintStatus.BLOCKED,
                    BlueprintStatus.ACTIVE)),
            // 异常态返回「进入前的主链态」（回退步，不跳跃推进主链，§6.3）。
            Map.entry(BlueprintStatus.NEEDS_CLARIFICATION, List.of(
                    BlueprintStatus.DRAFT, BlueprintStatus.DISCOVERING, BlueprintStatus.GAP_ANALYSIS,
                    BlueprintStatus.BLUEPRINT_READY)),
            Map.entry(BlueprintStatus.BLOCKED, BLOCKED_RETURN_TARGETS),
            Map.entry(BlueprintStatus.VALIDATION_FAILED, List.of(
                    BlueprintStatus.VALIDATING, BlueprintStatus.GAP_ANALYSIS,
                    BlueprintStatus.BLUEPRINT_READY)),
            Map.entry(BlueprintStatus.PAUSED, List.of(
                    BlueprintStatus.ACTIVE, BlueprintStatus.RUNNING, BlueprintStatus.BLOCKED)),
            Map.entry(BlueprintStatus.DEPRECATED, List.of()),
            Map.entry(BlueprintStatus.COMPLETED, List.of()));

    /**
     * 进入 READY_FOR_APPROVAL 前的门槛回调（§6.3/§6.6：BLOCKER gap 必须全
     * RESOLVED / ACCEPTED_RISK）。实现由 Service 持有（读 gaps_text 子段判定）。
     */
    @FunctionalInterface
    public interface PreReadyForApprovalGate {
        /**
         * @param blueprintId 蓝图 ID（校验实现据此定位 content）
         * @param gapsJson    蓝图 gaps_text 列原始 JSON（空 = 无 gap，视为通过）
         * @return null = 通过；否则返回拒绝原因文案（人读；缺项 details 由 Service 展开）
         */
        String check(String blueprintId, String gapsJson);
    }

    private volatile PreReadyForApprovalGate preReadyForApprovalGate;

    /**
     * 断言迁移合法；失败抛 {@link IllegalBlueprintTransitionException}（HTTP 409，状态不变，SUP-BP-01）。
     *
     * <p>校验顺序：源/目标词表 → 幂等同态 → 终态 → DRAFT 直进 ACTIVE 特判
     * （{@code DRAFT_DIRECT_ACTIVE_FORBIDDEN}）→ 出边矩阵 → 门槛回调。
     * 不传门槛上下文（纯矩阵断言，state machine 单测用此入口）。</p>
     */
    public void assertTransition(BlueprintStatus from, BlueprintStatus to) {
        assertTransition(from, to, null, null);
    }

    /**
     * 带门槛上下文的断言（Service 侧入口）：to = READY_FOR_APPROVAL 时执行 gate 回调
     * （未注入 = 按通过处理）。
     */
    void assertTransition(BlueprintStatus from, BlueprintStatus to,
                          String blueprintId, String gapsJson) {
        if (from == null || to == null) {
            throw IllegalBlueprintTransitionException.illegal(str(from), str(to));
        }
        if (from == to) {
            return; // 幂等 no-op
        }
        if (isTerminal(from)) {
            throw new IllegalBlueprintTransitionException("TERMINAL_STATE",
                    "蓝图处于只读终态 " + from + "，不可再迁移"
                            + (from == BlueprintStatus.DEPRECATED
                            ? "（历史版本可追溯，修订请生成 version+1 新行，§6.6）" : "（§6.3）"));
        }
        // §6.3 显式禁止：DRAFT 直进 ACTIVE（主链单向，不得跳跃）。
        if (from == BlueprintStatus.DRAFT && to == BlueprintStatus.ACTIVE) {
            throw new IllegalBlueprintTransitionException("DRAFT_DIRECT_ACTIVE_FORBIDDEN",
                    "禁止 DRAFT 直进 ACTIVE：须按主链逐步推进"
                            + "（DRAFT → DISCOVERING → … → RELEASED → ACTIVE，§6.3）");
        }
        List<BlueprintStatus> allowed = OUT_EDGES.get(from);
        if (allowed == null) {
            throw new IllegalBlueprintTransitionException("ILLEGAL_TRANSITION",
                    "未知源状态: " + from);
        }
        if (!allowed.contains(to)) {
            throw new IllegalBlueprintTransitionException("ILLEGAL_TRANSITION",
                    "非法状态迁移: " + from + " → " + to + "（合法出边: " + allowed + "，§6.3 迁移矩阵）");
        }
        // 门槛回调：进入 READY_FOR_APPROVAL 前 BLOCKER gap 必须全 RESOLVED/ACCEPTED_RISK（§6.6）。
        if (to == BlueprintStatus.READY_FOR_APPROVAL && preReadyForApprovalGate != null) {
            String gateResult = preReadyForApprovalGate.check(blueprintId, gapsJson);
            if (gateResult != null) {
                throw new IllegalBlueprintTransitionException("GATE_NOT_PASSED",
                        "进入 READY_FOR_APPROVAL 前置校验未通过: " + gateResult);
            }
        }
    }

    /** 终端态判定（COMPLETED / DEPRECATED，出边全无，§6.3）。 */
    public static boolean isTerminal(BlueprintStatus status) {
        return status == BlueprintStatus.COMPLETED || status == BlueprintStatus.DEPRECATED;
    }

    /**
     * 结构可达目标态（前端按钮态唯一来源，与服务端断言同一矩阵；
     * 按钮可见性 ≠ 迁移必然成功，门槛回调不在此执行）。
     */
    public List<BlueprintStatus> allowedTransitions(BlueprintStatus from) {
        if (from == null || isTerminal(from)) {
            return List.of();
        }
        return OUT_EDGES.getOrDefault(from, List.of());
    }

    /**
     * 该谱线是否已进入「发布不可变/只读」区（§6.6：RELEASED 起冻结 + 终态；
     * ACTIVE/RUNNING 属运行谱线，内容同样冻结）。Service 侧 content 更新拒绝（409）
     * 的唯一口径。
     */
    public boolean immutableAfterRelease(BlueprintStatus status) {
        return status == BlueprintStatus.RELEASED
                || status == BlueprintStatus.ACTIVE
                || status == BlueprintStatus.RUNNING
                || status == BlueprintStatus.DEPRECATED
                || status == BlueprintStatus.COMPLETED;
    }

    /** 装配进入 READY_FOR_APPROVAL 的门槛回调（Service 构造后注入；null = 无门槛）。 */
    public void setPreReadyForApprovalGate(PreReadyForApprovalGate gate) {
        this.preReadyForApprovalGate = gate;
    }

    static String str(BlueprintStatus s) {
        return s == null ? "null" : s.name();
    }

    /** 迁移审计载荷（Service 落库 + 事件共用，§6.3 五要素：actor/时间/原因/traceId/版本）。 */
    public static Map<String, Object> transitionPayload(String blueprintId, int version,
                                                        BlueprintStatus from, BlueprintStatus to,
                                                        String actor, String reason, String traceId) {
        return Map.of(
                "blueprintId", blueprintId == null ? "" : blueprintId,
                "blueprintVersion", version,
                "fromStatus", from == null ? "null" : from.name(),
                "toStatus", to == null ? "null" : to.name(),
                "actor", actor == null ? "" : actor,
                "reason", reason == null ? "" : reason,
                "traceId", traceId == null ? "" : traceId);
    }
}
