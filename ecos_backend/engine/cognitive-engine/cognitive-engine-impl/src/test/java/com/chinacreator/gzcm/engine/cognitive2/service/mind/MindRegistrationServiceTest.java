package com.chinacreator.gzcm.engine.cognitive2.service.mind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chinacreator.gzcm.engine.cognitive2.dao.MindAssetDao;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;

/**
 * B42/P1-19 契约门：场景 Mind 注册门面（V187 + V188 唯一写入口）。
 *
 * <p>四条：①新建（无同键）→ 插入 Mind + 绑定 PRIMARY；②同键更新 → 复用原 id/版本，
 * 不新增 Mind 行；③缺必填 → 400 guard，且零写；④解绑 → 透传受影响行数（0 如实 0）。</p>
 */
class MindRegistrationServiceTest {

    private final MindAssetDao dao = mock(MindAssetDao.class);
    private MindRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new MindRegistrationService(dao);
    }

    @Test
    @DisplayName("新建 Mind：插入后绑定 PRIMARY/100 并返回 mindId")
    void registersNewMind() {
        when(dao.findMindByKey("mind_benchmark")).thenReturn(null);

        Map<String, Object> out = service.register("sc_ent2_group_month", "mind_benchmark",
                "板块对标心智", "描述", List.of("DIAGNOSE", "FORECAST"), List.of(),
                null, null, null, "u-admin");

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        verify(dao).upsertMind(id.capture(), eq("mind_benchmark"), eq("板块对标心智"), eq("描述"),
                eq("[\"DIAGNOSE\",\"FORECAST\"]"), eq("[]"), eq("ACTIVE"), eq(1),
                eq("1.0.0"), eq("default"), eq("u-admin"));
        verify(dao).upsertScenarioBinding(anyString(), eq("sc_ent2_group_month"),
                eq(id.getValue()), eq("PRIMARY"), eq(100), eq("default"), eq("u-admin"));
        assertEquals(id.getValue(), out.get("mindId"));
        assertEquals(true, out.get("isActive"));
        assertEquals(32, id.getValue().length());
    }

    @Test
    @DisplayName("同键更新：复用原 mindId 与版本，不换主键")
    void updatesExistingMind() {
        when(dao.findMindByKey("mind_benchmark")).thenReturn(Map.of(
                "id", "mind_existing_1", "mind_version", "2.0.0", "domain", "entmgmt"));

        Map<String, Object> out = service.register("sc_ent2_group_month", "mind_benchmark",
                "板块对标心智v2", null, List.of("STRATEGY"), List.of("maxSteps=10"),
                "SECONDARY", 50, false, "u-admin");

        verify(dao).upsertMind(eq("mind_existing_1"), eq("mind_benchmark"), eq("板块对标心智v2"),
                eq(null), eq("[\"STRATEGY\"]"), eq("[\"maxSteps=10\"]"), eq("ACTIVE"), eq(0),
                eq("2.0.0"), eq("entmgmt"), eq("u-admin"));
        verify(dao).upsertScenarioBinding(anyString(), eq("sc_ent2_group_month"),
                eq("mind_existing_1"), eq("SECONDARY"), eq(50), eq("entmgmt"), eq("u-admin"));
        assertEquals("mind_existing_1", out.get("mindId"));
        assertEquals(false, out.get("isActive"));
    }

    @Test
    @DisplayName("缺必填 ⇒ 400 guard 且零写")
    void rejectsMissingFields() {
        assertThrows(CognitiveGuardException.class,
                () -> service.register("sc", " ", "name", null, List.of(), List.of(), null, null, null, "u"));
        verify(dao, never()).upsertMind(anyString(), anyString(), anyString(), any(),
                anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("解绑：透传受影响行数（0 行不伪装成功）")
    void unbindReturnsAffectedRows() {
        when(dao.softDeleteBinding("sc_ent2_group_month", "mind_x")).thenReturn(0);
        assertEquals(0, service.unbind("sc_ent2_group_month", "mind_x"));
        assertTrue(true);
    }
}
