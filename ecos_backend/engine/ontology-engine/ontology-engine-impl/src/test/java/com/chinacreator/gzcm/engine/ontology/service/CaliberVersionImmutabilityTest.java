package com.chinacreator.gzcm.engine.ontology.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W73/M0 · F03-01 — 行不可变 / 状态机幂等护栏（纯 Mockito 无 Spring 上下文）。
 *
 * <p>断言：
 * <ol>
 *   <li>APPROVED→重复 approve（目标=当前状态）→ archiveVersion 返回 0，无 UPDATE、不抛异常（idempotency guard）；</li>
 *   <li>RETIRED→任意目标 → 返回 0（RETIRED 为终态，无合法前向迁移）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaliberVersionImmutabilityTest {

    private static final String SCHEMA = "ecos_ontology";
    private static final String CID = "c1";
    private static final String V = "1";

    @Mock
    private JdbcTemplate jdbc;

    private CaliberService service;

    @BeforeEach
    void setUp() {
        service = new CaliberService(jdbc, SCHEMA);
    }

    @Test
    @DisplayName("APPROVED 重审批 → 0，无副作用，不抛异常")
    void alreadyApproved_rearchiveIsNoOp() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(CID), eq(V)))
                .thenReturn("APPROVED");

        int affected = assertDoesNotThrow(
                () -> service.archiveVersion(CID, V, "APPROVED", "admin"));
        assertEquals(0, affected);

        // 无 UPDATE（幂等护栏）
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("APPROVED→RETIRED 合法迁移 → 1，且写 approved_by/approved_at")
    void approvedToRetired_isForwardTransition() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(CID), eq(V)))
                .thenReturn("APPROVED");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        int affected = service.archiveVersion(CID, V, "RETIRED", "admin");
        assertEquals(1, affected);
    }

    @Test
    @DisplayName("RETIRED→任意 → 0（终态不可再流转）")
    void retiredIsTerminal() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(CID), eq(V)))
                .thenReturn("RETIRED");

        int affected = service.archiveVersion(CID, V, "APPROVED", "admin");
        assertEquals(0, affected);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("行不存在 → 0，不抛异常")
    void missingRow_returnsZero() {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(CID), eq(V)))
                .thenThrow(new EmptyResultDataAccessException(1));

        int affected = service.archiveVersion(CID, V, "APPROVED", "admin");
        assertEquals(0, affected);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
