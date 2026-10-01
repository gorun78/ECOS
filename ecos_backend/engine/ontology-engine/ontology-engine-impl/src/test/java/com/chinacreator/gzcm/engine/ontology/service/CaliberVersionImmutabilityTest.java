package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.dao.CaliberDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W73/M0 · F03-01 — 行不可变 / 状态机幂等护栏（纯 Mockito 无 Spring 上下文）。
 *
 * <p>R2-B3 (架构铁律 §2.5 #1)：JDBC 下沉 {@link CaliberDao}，mock 对象由
 * JdbcTemplate 切换为 CaliberDao；「status 查询 + UPDATE」的断言点随之迁移。</p>
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

    private static final String CID = "c1";
    private static final String V = "1";

    @Mock
    private CaliberDao caliberDao;

    private CaliberService service;

    @BeforeEach
    void setUp() {
        service = new CaliberService(caliberDao);
    }

    @Test
    @DisplayName("APPROVED 重审批 → 0，无副作用，不抛异常")
    void alreadyApproved_rearchiveIsNoOp() {
        when(caliberDao.findVersionStatus(CID, V)).thenReturn("APPROVED");

        int affected = assertDoesNotThrow(
                () -> service.archiveVersion(CID, V, "APPROVED", "admin"));
        assertEquals(0, affected);

        // 无 UPDATE（幂等护栏）
        verify(caliberDao, never()).archiveVersion(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("APPROVED→RETIRED 合法迁移 → 1，且写 approved_by/approved_at")
    void approvedToRetired_isForwardTransition() {
        when(caliberDao.findVersionStatus(CID, V)).thenReturn("APPROVED");
        when(caliberDao.archiveVersion(CID, V, "RETIRED", "admin", "APPROVED")).thenReturn(1);

        int affected = service.archiveVersion(CID, V, "RETIRED", "admin");
        assertEquals(1, affected);
    }

    @Test
    @DisplayName("RETIRED→任意 → 0（终态不可再流转）")
    void retiredIsTerminal() {
        when(caliberDao.findVersionStatus(CID, V)).thenReturn("RETIRED");

        int affected = service.archiveVersion(CID, V, "APPROVED", "admin");
        assertEquals(0, affected);
        verify(caliberDao, never()).archiveVersion(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("行不存在 → 0，不抛异常")
    void missingRow_returnsZero() {
        // DAO 内 catch EmptyResultDataAccessException → 返回 null
        when(caliberDao.findVersionStatus(CID, V)).thenReturn(null);

        int affected = service.archiveVersion(CID, V, "APPROVED", "admin");
        assertEquals(0, affected);
        verify(caliberDao, never()).archiveVersion(any(), any(), any(), any(), any());
    }
}
