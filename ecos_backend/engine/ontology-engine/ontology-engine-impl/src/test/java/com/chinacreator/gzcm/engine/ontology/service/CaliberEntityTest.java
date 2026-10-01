package com.chinacreator.gzcm.engine.ontology.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.dao.CaliberDao;
import com.chinacreator.gzcm.engine.ontology.service.CaliberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W73/M0 · F03-01 — Caliber entity service behavior tests (pure Mockito, no Spring context).
 *
 * <p>R2-B3 (arch rule §2.5 #1): JDBC 下沉 {@link CaliberDao}，本测试 mock 对象由
 * JdbcTemplate 切换为 CaliberDao（SQL 文本断言随收口迁至 DAO 层，Service 层只断言
 * 业务语义：MC01 应用侧 UUID、状态机入参、filter 透传）。</p>
 *
 * <p>Cover:
 * <ol>
 *   <li>{@code insertCaliber} — 先落主表再按 code 读回; app-side UUID id (MC01);</li>
 *   <li>{@code findByCode} — 透传 DAO 的 Optional（empty 分支）;</li>
 *   <li>rule 2 — insertVersion default DRAFT; promote via archiveVersion;</li>
 *   <li>{@code listVersionsByCaliberId} status filter 透传。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaliberEntityTest {

    @Mock
    private CaliberDao caliberDao;

    private CaliberService service;

    @BeforeEach
    void setUp() {
        service = new CaliberService(caliberDao);
    }

    @Test
    @DisplayName("insertCaliber: app-side UUID id 先落主表，再按 code 读回行")
    void insertCaliber_appSideUuidId() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "uuid-1");
        row.put("code", "CAL_X");
        row.put("name", "cal x");
        row.put("status", "ACTIVE");
        when(caliberDao.findByCode("CAL_X")).thenReturn(Optional.of(row));

        Map<String, Object> result = service.insertCaliber("CAL_X", "cal x", "CNY", "CNY",
                "role.A", null, "ACTIVE");

        // 1. assert id returned to caller is the mocked row id
        assertEquals("uuid-1", result.get("id"));
        assertNotNull(result.get("id"));

        // 2. assert DAO 收到 insert，第一参数是应用侧 UUID (MC01)
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(caliberDao).insertCaliber(idCaptor.capture(), eq("CAL_X"), eq("cal x"), eq("ACTIVE"),
                eq("CNY"), eq("CNY"), eq("role.A"), eq((String) null));
        String id = idCaptor.getValue();
        assertTrue(id.length() == 36 && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "expected UUID format id but got: " + id);
    }

    @Test
    @DisplayName("findByCode: DAO 返回 empty Optional 时 Service 透传 empty")
    void findByCode_emptyOnNoRow() {
        when(caliberDao.findByCode("CAL_X")).thenReturn(Optional.empty());

        Optional<Map<String, Object>> opt = service.findByCode("CAL_X");
        assertTrue(opt.isEmpty(), "expected empty Optional");
    }

    @Test
    @DisplayName("rule 2: insertVersion defaults DRAFT; row immutable; archive promotes to APPROVED")
    void insertVersion_defaultDraftThenApproved() {
        Map<String, Object> vRow = new LinkedHashMap<>();
        vRow.put("id", "vuuid");
        vRow.put("status", "DRAFT");
        when(caliberDao.findVersionById(anyString())).thenReturn(vRow);

        Map<String, Object> inserted = service.insertVersion("c1", "1", "SUM(x)", 1, "MONTH", null);
        assertEquals("DRAFT", inserted.get("status"));

        // Verify INSERT received status='DRAFT' as last arg (MC01: id UUID first)
        ArgumentCaptor<String> idCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusCap = ArgumentCaptor.forClass(String.class);
        verify(caliberDao).insertVersion(idCap.capture(), eq("c1"), eq("1"), eq("SUM(x)"),
                eq(1), eq("MONTH"), statusCap.capture());
        String id = idCap.getValue();
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "expected UUID first arg (MC01) but got: " + id);
        assertEquals("DRAFT", statusCap.getValue());

        // Now promote: current DRAFT → target APPROVED (DRAFT→APPROVED is allowed)
        when(caliberDao.findVersionStatus("c1", "1")).thenReturn("DRAFT");
        when(caliberDao.archiveVersion("c1", "1", "APPROVED", "admin", "DRAFT")).thenReturn(1);
        int affected = service.archiveVersion("c1", "1", "APPROVED", "admin");
        assertEquals(1, affected);
    }

    @Test
    @DisplayName("listVersionsByCaliberId: status filter 透传到 DAO")
    void listVersions_statusFilterApplied() {
        when(caliberDao.listVersionsByCaliberId("c1", "APPROVED")).thenReturn(List.of());
        service.listVersionsByCaliberId("c1", "APPROVED");

        verify(caliberDao).listVersionsByCaliberId("c1", "APPROVED");
    }
}
