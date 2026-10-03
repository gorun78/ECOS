package com.chinacreator.gzcm.engine.data.quality.service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F02-08 / D.1 数据域 DQ 错误码护栏 —— 规则状态机非法迁移（ECOS-DQ-101，409）。
 *
 * <p>把 {@link DqRuleLifecycleServiceImpl#assertTransition} 的纯静态状态机契约离线单测化：
 * 用于 transition 判非法时<b>必须</b>抛并携带 {@code ECOS-DQ-101}（D.1：规则状态机非法迁移，
 * 如 DRAFT → ACTIVE 需经签核人，禁止越级），而合法迁移不抛。不触库、不 Spring、不联网。</p>
 */
@DisplayName("D.1 DQ-101 规则状态机非法迁移护栏")
class DqRuleStateTransitionInvalidTest {

    @Test
    @DisplayName("DRAFT → ACTIVE（越级，未经 IN_REVIEW 签核）→ ECOS-DQ-101")
    void draftToActiveIsIllegal() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> DqRuleLifecycleServiceImpl.assertTransition("DRAFT", "ACTIVE", "r-1"));
        assertTrue(ex.getMessage().contains("ECOS-DQ-101"),
                "非法映射应携 ECOS-DQ-101，实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("状态转换非法"), "应说明状态转换非法: " + ex.getMessage());
    }

    @Test
    @DisplayName("SUPERSEDED → 任何状态（终态，无后继）→ ECOS-DQ-101")
    void terminalStateNoSuccessor() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> DqRuleLifecycleServiceImpl.assertTransition("SUPERSEDED", "ACTIVE", "r-2"));
        assertTrue(ex.getMessage().contains("ECOS-DQ-101"),
                "终态转换应携 ECOS-DQ-101，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("from = null（未知态）→ ECOS-DQ-101，不裸抛 NPE")
    void unknownInitialState() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> DqRuleLifecycleServiceImpl.assertTransition(null, "ACTIVE", "r-3"));
        assertTrue(ex.getMessage().contains("ECOS-DQ-101"),
                "未知初始态应携 ECOS-DQ-101，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("合法迁移 DRAFT→IN_REVIEW、IN_REVIEW→ACTIVE、IN_REVIEW→REJECTED 不抛")
    void legalTransitionsPass() {
        assertDoesNotThrow(() -> DqRuleLifecycleServiceImpl.assertTransition("DRAFT", "IN_REVIEW", "r-4"));
        assertDoesNotThrow(() -> DqRuleLifecycleServiceImpl.assertTransition("IN_REVIEW", "ACTIVE", "r-5"));
        assertDoesNotThrow(() -> DqRuleLifecycleServiceImpl.assertTransition("IN_REVIEW", "REJECTED", "r-6"));
        assertDoesNotThrow(() -> DqRuleLifecycleServiceImpl.assertTransition("ACTIVE", "DISABLED", "r-7"));
    }

    @Test
    @DisplayName("白线 — 错误码常量在实现内真实定义且 = doc 语义值（防空文件静默绿）")
    void c15CodeConstant() {
        assertEquals("ECOS-DQ-101", DqRuleLifecycleServiceImpl.CODE_DQ_RULE_STATE_INVALID);
    }
}
