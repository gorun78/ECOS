package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.model.Caliber;
import com.chinacreator.gzcm.engine.ontology.model.CaliberVersion;
import com.chinacreator.gzcm.engine.ontology.repository.CaliberRepository;
import com.chinacreator.gzcm.engine.ontology.repository.CaliberVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F03-01 口径实体领域服务（W73/C57 P0）：口径主表 CRUD + 版本行不可变 + DR07 版本编码断言。
 * 全 mock，禁连库。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaliberEntityTest {

    @Mock CaliberRepository caliberRepo;
    @Mock CaliberVersionRepository versionRepo;

    private CaliberServiceImpl svc() { return new CaliberServiceImpl(caliberRepo, versionRepo); }

    @Test
    @DisplayName("createCaliber：DRAFT→自动补齐 DR06/DR07/DR08")
    void c1a_create() {
        when(caliberRepo.findByCode("CAL_ACCT")).thenReturn(Optional.empty());
        Caliber in = new Caliber();
        in.setCode("CAL_ACCT");
        in.setName("验收口径");
        Caliber c = svc().createCaliber(in, "pmo");
        assertNotNull(c.getId(), "应用侧 UUID 必生成（MC01）");
        assertEquals("pmo", c.getCreateBy());
        assertEquals("pmo", c.getUpdateBy());
        assertNotNull(c.getCreateTime());
        verify(caliberRepo).insert(argThat(x -> "CAL_ACCT".equals(x.getCode())));
    }

    @Test
    @DisplayName("createCaliber：重复 code → 409 ECOS-ONTO-041，不 insert")
    void c1b_dupCode() {
        when(caliberRepo.findByCode("DUP")).thenReturn(Optional.of(new Caliber()));
        Caliber dup = new Caliber();
        dup.setCode("DUP");
        CaliberServiceImpl.BusinessException ex = assertThrows(CaliberServiceImpl.BusinessException.class,
                () -> svc().createCaliber(dup, "pmo"));
        assertEquals("ECOS-ONTO-041", ex.code);
        assertEquals(409, ex.httpStatus);
        verify(caliberRepo, never()).insert(any());
    }

    @Test
    @DisplayName("publishCaliberVersion：重复 (caliberId,versionNo) → 409 ECOS-ONTO-041")
    void c2_publishVersionDupKey() {
        CaliberVersion existing = new CaliberVersion();
        existing.setId("v-old");
        when(versionRepo.findByCaliberIdAndVersion("CAL-1", "1.0")).thenReturn(Optional.of(existing));
        when(caliberRepo.findByCode(any())).thenReturn(Optional.of(new Caliber()));

        CaliberVersion v = new CaliberVersion();
        v.setCaliberId("CAL-1");
        v.setVersionNo("1.0");
        CaliberServiceImpl.BusinessException ex = assertThrows(CaliberServiceImpl.BusinessException.class,
                () -> svc().publishCaliberVersion(v, "pmo"));
        assertEquals("ECOS-ONTO-041", ex.code);
        assertEquals(409, ex.httpStatus);
        verify(versionRepo, never()).insert(any());
    }

    @Test
    @DisplayName("listCalibers：走 schema 全表读，DR06 createBy 记录传入")
    void c3_list() {
        Caliber c = new Caliber();
        c.setCode("CAL_A");
        when(caliberRepo.findAll()).thenReturn(List.of(c));
        List<Caliber> out = svc().listCalibers();
        assertEquals(1, out.size());
        assertEquals("CAL_A", out.get(0).getCode());
    }

    @Test
    @DisplayName("isVersionApproved：仅当 status=APPROVED 才 true（V1 门禁判据）")
    void c4_isVersionApproved() {
        CaliberVersion v = new CaliberVersion();
        v.setId("vid");
        v.setStatus("APPROVED");
        when(versionRepo.findByCaliberIdAndVersion("CAL-1", "1.0")).thenReturn(Optional.of(v));
        assertTrue(svc().isVersionApproved("CAL-1", "1.0"));

        CaliberVersion draft = new CaliberVersion();
        draft.setStatus("DRAFT");
        when(versionRepo.findByCaliberIdAndVersion("CAL-1", "9.9")).thenReturn(Optional.of(draft));
        assertEquals(false, svc().isVersionApproved("CAL-1", "9.9"));

        // 空入参兜底 false
        assertEquals(false, svc().isVersionApproved(null, "1.0"));
        assertEquals(false, svc().isVersionApproved("CAL-1", null));
    }

    @Test
    @DisplayName("createCaliber 走 schema 收录路径：捕获 insert 断言 DR 列被填充")
    void c5_createBacksCapture() {
        when(caliberRepo.findByCode("CAL_B")).thenReturn(Optional.empty());
        Caliber in = new Caliber();
        in.setCode("CAL_B");
        in.setName("成本");
        Caliber c = svc().createCaliber(in, "op1");
        ArgumentCaptor<Caliber> cap = ArgumentCaptor.forClass(Caliber.class);
        verify(caliberRepo).insert(cap.capture());
        assertEquals("CAL_B", cap.getValue().getCode());
        assertEquals("ACTIVE", cap.getValue().getStatus());
        assertEquals("op1", cap.getValue().getCreateBy());
        assertEquals("op1", cap.getValue().getUpdateBy());
    }

    @Test
    @DisplayName("isAvailable：仓储异常时返 false（供 fail-closed 判定）")
    void c6_isAvailable() {
        org.mockito.Mockito.doThrow(new RuntimeException("down")).when(caliberRepo).findAll();
        assertEquals(false, svc().isAvailable());
        org.mockito.Mockito.doReturn(List.of()).when(caliberRepo).findAll();
        assertEquals(true, svc().isAvailable());
    }
}
