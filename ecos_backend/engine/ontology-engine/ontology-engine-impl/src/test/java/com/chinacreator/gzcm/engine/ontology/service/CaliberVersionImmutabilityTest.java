package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.model.CaliberVersion;
import com.chinacreator.gzcm.engine.ontology.repository.CaliberRepository;
import com.chinacreator.gzcm.engine.ontology.repository.CaliberVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F03-01 口径版本行不可变 + 状态机只前进（W73/C57 P0）：
 * - APPROVED → REVIEWING（后退）拒绝，409 ECOS-ONTO-041；
 * - 本实现不提供"内容列 UPDATE"方法 —— 用 Mock 断言 versionRepo 仅走 updateStatus（不可变更）路径。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaliberVersionImmutabilityTest {

    @Mock CaliberRepository caliberRepo;
    @Mock CaliberVersionRepository versionRepo;

    private CaliberServiceImpl svc() { return new CaliberServiceImpl(caliberRepo, versionRepo); }

    @Test
    @DisplayName("合法迁移 DRAFT → REVIEWING → APPROVED → RETIRED：状态流转 approvedBy/approvedAt 正确回填")
    void c1_forwardMigration() {
        CaliberVersion v = version("vid", "DRAFT");
        when(versionRepo.findById("vid")).thenReturn(Optional.of(v));

        CaliberVersion v1 = svc().transition("vid", "REVIEWING", "op1");
        assertEquals("REVIEWING", v1.getStatus());
        verify(versionRepo).updateStatus(eq("vid"), eq("REVIEWING"), any(), any(), eq("op1"));

        when(versionRepo.findById("vid")).thenReturn(Optional.of(version("vid", "REVIEWING")));
        CaliberVersion v2 = svc().transition("vid", "APPROVED", "op2");
        assertEquals("APPROVED", v2.getStatus());
        assertNotNull(v2.getApprovedAt(), "APPROVED 迁移必落 approved_at");

        when(versionRepo.findById("vid")).thenReturn(Optional.of(version("vid", "APPROVED")));
        CaliberVersion v3 = svc().transition("vid", "RETIRED", "op3");
        assertEquals("RETIRED", v3.getStatus());
    }

    @Test
    @DisplayName("非法迁移 APPROVED → REVIEWING（后退）→ 409 ECOS-ONTO-041 拒绝，不落库")
    void c2_backwardRejected() {
        when(versionRepo.findById("mid")).thenReturn(Optional.of(version("mid", "APPROVED")));
        CaliberServiceImpl.BusinessException ex = assertThrows(CaliberServiceImpl.BusinessException.class,
                () -> svc().transition("mid", "REVIEWING", "op"));
        assertEquals("ECOS-ONTO-041", ex.code);
        assertEquals(409, ex.httpStatus);
        verify(versionRepo, never()).updateStatus(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("非法迁移 APPROVED → DRAFT（后退）同样拒绝 409")
    void c3_approvedCannotDescend() {
        when(versionRepo.findById("x2")).thenReturn(Optional.of(version("x2", "APPROVED")));
        CaliberServiceImpl.BusinessException ex = assertThrows(CaliberServiceImpl.BusinessException.class,
                () -> svc().transition("x2", "DRAFT", "op"));
        assertEquals("ECOS-ONTO-041", ex.code);
        verify(versionRepo, never()).updateStatus(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("非法目标 RETIRED → APPROVED：终态不可复活")
    void c4_retiredTerminal() {
        when(versionRepo.findById("x3")).thenReturn(Optional.of(version("x3", "RETIRED")));
        CaliberServiceImpl.BusinessException ex = assertThrows(CaliberServiceImpl.BusinessException.class,
                () -> svc().transition("x3", "APPROVED", "op"));
        assertEquals("ECOS-ONTO-041", ex.code);
    }

    @Test
    @DisplayName("版本不存在 → 404 ECOS-ONTO-080")
    void c5_missingVersion404() {
        when(versionRepo.findById("nope")).thenReturn(Optional.empty());
        CaliberServiceImpl.BusinessException ex = assertThrows(CaliberServiceImpl.BusinessException.class,
                () -> svc().transition("nope", "REVIEWING", "op"));
        assertEquals("ECOS-ONTO-080", ex.code);
        assertEquals(404, ex.httpStatus);
    }

    private static CaliberVersion version(String id, String status) {
        CaliberVersion v = new CaliberVersion();
        v.setId(id);
        v.setCaliberId("CAL-X");
        v.setVersionNo("1.0");
        v.setStatus(status);
        return v;
    }
}
