package com.chinacreator.gzcm.engine.ontology.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.engine.ontology.dao.CaliberDao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * WAgent Readiness 供数 — caliber conflict-count 端点的 Service + Controller 行为测试。
 *
 * <p>判据：同一 caliber.code 下 ≥2 条 APPROVED 且 is_deleted=0 的版本行 ⇒ 冲突。</p>
 *
 * <p>Cover:
 * <ol>
 *   <li>count=0 无冲突时 Service 透传 DAO 返回值 0;</li>
 *   <li>存在冲突时 Service 正确透传 >0 计数;</li>
 *   <li>DAO 抛异常时 Controller 捕获并返回 500 ECOS-ONTO-042（fail-closed）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaliberConflictCountTest {

    @Mock
    private CaliberDao caliberDao;

    private CaliberService service;

    @BeforeEach
    void setUp() {
        service = new CaliberService(caliberDao);
    }

    @Test
    @DisplayName("count=0: 无冲突口径时 Service 透传 DAO 返回值 0")
    void noConflict_returnsZero() {
        when(caliberDao.countConflictedCaliberCodes()).thenReturn(0);

        int result = service.countConflictedCaliberCodes();

        assertEquals(0, result);
        verify(caliberDao).countConflictedCaliberCodes();
    }

    @Test
    @DisplayName("存在两版本 APPROVED 冲突时 count 正确 (>0)")
    void conflict_present_returnsCorrectCount() {
        when(caliberDao.countConflictedCaliberCodes()).thenReturn(3);

        int result = service.countConflictedCaliberCodes();

        assertEquals(3, result);
        verify(caliberDao).countConflictedCaliberCodes();
    }

    @Test
    @DisplayName("DAO 抛异常时 Controller 捕获并返回 500 fail-closed")
    void daoException_controllerReturns500() {
        when(caliberDao.countConflictedCaliberCodes()).thenThrow(new RuntimeException("db down"));

        com.chinacreator.gzcm.engine.ontology.controller.CaliberController controller =
                new com.chinacreator.gzcm.engine.ontology.controller.CaliberController(
                        service, null);

        com.chinacreator.gzcm.common.base.ApiResponse<java.util.Map<String, Object>> resp =
                controller.conflictCount();

        assertEquals(500, resp.getCode());
        assertNotNull(resp.getMessage());
        assertTrue(resp.getMessage().contains("conflict-count failed"));
    }
}
