package com.chinacreator.gzcm.engine.data.quality.service;

import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DqRuleDetailNotFoundCodeTest — D.1 ECOS-DQ-111（404 治理记录不存在，与 031 严格区分）离线护栏。
 *
 * <p>设计-02 D.1 行 432：{@code ECOS-DQ-111 | 404 | 治理记录不存在（真实 404，与 031 严格区分）}。
 * {@code 031} 是数据层 SQL/映射异常 → <b>500</b>（GlobalExceptionHandler 显式"不再掩蔽成 404"）；
 * 本码一旦命中即<b>真实业务 404</b>。护栏落在 {@link DqGovernanceServiceImpl#getRuleDetail}
 * （新端点 {@code GET /api/v1/dq/rules/{id}} 的读面），code 按仓内"功能码落消息字面"惯例随
 * {@link NotFoundException} 消息透传到 gateway 的 404 响应体。
 *
 * <p>离线：DqRuleMapper.findById 打桩 null（记录确实查不到）、DqSecurityService 打桩，不触库/不联网。
 */
class DqRuleDetailNotFoundCodeTest {

    private DqRuleMapper mapper;
    private DqSecurityService security;
    private DqGovernanceServiceImpl service;

    @BeforeEach
    void setUp() {
        this.mapper = mock(DqRuleMapper.class);
        this.security = mock(DqSecurityService.class);
        this.service = new DqGovernanceServiceImpl(mapper, security);
    }

    @Test
    @DisplayName("规则不存在 → 抛 NotFoundException，消息携 ECOS-DQ-111（真实 404，非 031/500）")
    void missingRuleThrowsNotFoundWithCode111() {
        when(mapper.findById("r-missing")).thenReturn(null);

        NotFoundException ex =
                assertThrows(NotFoundException.class, () -> service.getRuleDetail("r-missing"));

        assertTrue(ex.getMessage().contains("ECOS-DQ-111"),
                "治理记录不存在应落 D.1 真实 404 功能码 ECOS-DQ-111，实际：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("r-missing"),
                "404 消息应携带被查询的规则 id，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("记录不存在时亦留审计痕（DQ_RULE_DETAIL），不吞审计")
    void missingRuleStillAuditsRead() {
        when(mapper.findById("r-gone")).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.getRuleDetail("r-gone"));

        verify(security).auditRead("DQ_RULE_DETAIL", "r-gone");
    }

    @Test
    @DisplayName("白线：功能码常量值 = doc 语义（防空文件静默绿）")
    void c15CodeConstantWhiteLine() {
        assertEquals("ECOS-DQ-111", DqGovernanceServiceImpl.CODE_DQ_RULE_NOT_FOUND);
    }
}
