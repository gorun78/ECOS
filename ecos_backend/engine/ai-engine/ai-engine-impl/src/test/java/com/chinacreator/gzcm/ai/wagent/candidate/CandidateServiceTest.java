package com.chinacreator.gzcm.ai.wagent.candidate;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.CandidateStatus;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.CandidateType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-20/F10-23 Candidate 生命周期离线单测：
 * 合法前向链迁移放行，非法迁移抛 E-WA-STATE；过期候选不可批；四眼（审批人≠生成 Run）强制。
 */
class CandidateServiceTest {

    private static final Instant FAR_FUTURE = Instant.now().plusSeconds(999_999_999L);

    /** 构造一个最小合法 Candidate（type 非空、generatedByRun 非空、五校验列非空）。 */
    private static CandidateModel model(String id, String genRun, Instant expire) {
        return new CandidateModel(
                id, CandidateType.RULE, "ai-engine", "{}",
                "{\"payload\":1}", "hash-" + id, genRun, "step1",
                new BigDecimal("0.9"), "{}",
                "warn", "warn", "warn", "warn", "warn",
                CandidateStatus.PENDING_APPROVAL, "batch1", null, null, null, expire);
    }

    /** F10-20：合法前向链迁移放行；非法（APPROVED→DRAFT）抛 E-WA-STATE（IllegalStateException）。 */
    @Test
    void lifecycleTransitionsAreLegalOnlyForwardChain() {
        // 合法前向链：DRAFT→VALIDATING→READY→PENDING_APPROVAL→APPROVED→PUBLISHED。
        assertEquals(CandidateStatus.VALIDATING,
                LifecycleState.ensureTransition(CandidateStatus.DRAFT, CandidateStatus.VALIDATING));
        assertEquals(CandidateStatus.READY,
                LifecycleState.ensureTransition(CandidateStatus.VALIDATING, CandidateStatus.READY));
        assertEquals(CandidateStatus.PENDING_APPROVAL,
                LifecycleState.ensureTransition(CandidateStatus.READY, CandidateStatus.PENDING_APPROVAL));
        assertEquals(CandidateStatus.APPROVED,
                LifecycleState.ensureTransition(CandidateStatus.PENDING_APPROVAL, CandidateStatus.APPROVED));
        assertEquals(CandidateStatus.PUBLISHED,
                LifecycleState.ensureTransition(CandidateStatus.APPROVED, CandidateStatus.PUBLISHED));

        // 任意活跃态 → EXPIRED / SUPERSEDED 合法。
        assertEquals(CandidateStatus.EXPIRED,
                LifecycleState.ensureTransition(CandidateStatus.DRAFT, CandidateStatus.EXPIRED));

        // 非法回退：APPROVED→DRAFT 必须抛 E-WA-STATE。
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> LifecycleState.ensureTransition(CandidateStatus.APPROVED, CandidateStatus.DRAFT));
        assertTrue(ex.getMessage().contains("E-WA-STATE"), "非法迁移须带 E-WA-STATE，实际：" + ex.getMessage());
    }

    /** F10-23：过期候选（expireTime 非远未来）不可审批 ⇒ 抛 IllegalStateException。 */
    @Test
    void expiredCandidateCannotBeApproved() {
        CandidateService svc = new CandidateService();
        String id = svc.create(model("exp-1", "run-gen", Instant.now().minusSeconds(60)));
        assertEquals(CandidateStatus.DRAFT, svc.get(id).status());
        // 推到 PENDING_APPROVAL。
        svc.updateStatus(id, CandidateStatus.VALIDATING);
        svc.updateStatus(id, CandidateStatus.READY);
        svc.updateStatus(id, CandidateStatus.PENDING_APPROVAL);

        assertThrows(IllegalStateException.class,
                () -> svc.approve(id, "token", "reviewer-other"),
                "过期候选必须拒批");
    }

    /** F10-23：四眼原则——reviewerId == generatedByRun ⇒ 抛 IllegalStateException。 */
    @Test
    void fourEyesEnforcedOnApprove() {
        CandidateService svc = new CandidateService();
        String id = svc.create(model("fe-1", "run-gen", FAR_FUTURE));
        svc.updateStatus(id, CandidateStatus.VALIDATING);
        svc.updateStatus(id, CandidateStatus.READY);
        svc.updateStatus(id, CandidateStatus.PENDING_APPROVAL);

        // 审批人 = 生成 Run 发起人 ⇒ 四眼违规。
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.approve(id, "token", "run-gen"));
        assertTrue(ex.getMessage().contains("four-eyes"),
                "四眼违规消息须含 four-eyes，实际：" + ex.getMessage());

        // 对照：不同审批人 + 未过期 ⇒ 放行到 APPROVED。
        CandidateModel approved = svc.approve(id, "token", "reviewer-zhang");
        assertEquals(CandidateStatus.APPROVED, approved.status());
    }
}
