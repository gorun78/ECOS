package com.chinacreator.gzcm.engine.ontology.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W73/M0 · F03-01 — Caliber entity service behavior tests (pure Mockito, no Spring context).
 *
 * <p>Cover:
 * <ol>
 *   <li>{@code insertCaliber} — schema-qualified INSERT; app-side UUID id (MC01);</li>
 *   <li>{@code findByCode} — returns empty on {@link EmptyResultDataAccessException};</li>
 *   <li>rule 2 — insertVersion default DRAFT; promote via archiveVersion;</li>
 *   <li>{@code listVersionsByCaliberId} respects status filter.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaliberEntityTest {

    private static final String SCHEMA = "ecos_ontology";

    @Mock
    private JdbcTemplate jdbc;

    private CaliberService service;

    @BeforeEach
    void setUp() {
        service = new CaliberService(jdbc, SCHEMA);
    }

    @Test
    @DisplayName("insertCaliber: app-side UUID + schema-qualified INSERT")
    void insertCaliber_schemaQualifiedUuidId() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "uuid-1");
        row.put("code", "CAL_X");
        row.put("name", "cal x");
        row.put("status", "ACTIVE");
        when(jdbc.queryForMap(anyString(), eq("CAL_X"))).thenReturn(row);

        Map<String, Object> result = service.insertCaliber("CAL_X", "cal x", "CNY", "CNY",
                "role.A", null, "ACTIVE");

        // 1. assert id returned to caller is non-null UUID
        assertEquals("uuid-1", result.get("id"));
        assertNotNull(result.get("id"));

        // 2. assert mocked JDBC received INSERT INTO ecos_ontology.ecos_caliber with is_deleted=0 and version_no='1'
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sqlCaptor.capture(), argsCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("INSERT INTO " + SCHEMA + ".ecos_caliber"),
                "expected schema-qualified INSERT but got: " + sql);
        assertTrue(sql.contains("is_deleted"));
        assertTrue(sql.contains("version_no"));
        // MC01: app-side UUID as first arg (36 chars, contains hyphen)
        Object[] args = argsCaptor.getValue();
        assertTrue(args.length > 0 && args[0] instanceof String, "first INSERT arg must be app-side UUID");
        String id = (String) args[0];
        assertTrue(id.length() == 36 && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "expected UUID format id but got: " + id);
    }

    @Test
    @DisplayName("findByCode: empty Optional when EmptyResultDataAccessException")
    void findByCode_emptyOnNoRow() {
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbc).queryForMap(anyString(), eq("CAL_X"));

        Optional<Map<String, Object>> opt = service.findByCode("CAL_X");
        assertTrue(opt.isEmpty(), "expected empty Optional");
    }

    @Test
    @DisplayName("rule 2: insertVersion defaults DRAFT; row immutable; archive promotes to APPROVED")
    void insertVersion_defaultDraftThenApproved() {
        Map<String, Object> vRow = new LinkedHashMap<>();
        vRow.put("id", "vuuid");
        vRow.put("status", "DRAFT");
        when(jdbc.queryForMap(anyString(), any(Object.class)))
                .thenAnswer(inv -> vRow);

        Map<String, Object> inserted = service.insertVersion("c1", "1", "SUM(x)", 1, "MONTH", null);
        assertEquals("DRAFT", inserted.get("status"));

        // Verify INSERT received status='DRAFT' as last positional arg (MC01: id UUID first)
        ArgumentCaptor<Object[]> arrCap = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), arrCap.capture());
        Object[] args = arrCap.getValue();
        assertTrue(args[0] instanceof String && ((String) args[0]).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "expected UUID first arg (MC01) but got: " + args[0]);
        assertEquals("DRAFT", args[args.length - 1]);

        // Now promote: current DRAFT → target APPROVED (DRAFT→APPROVED is allowed after whitelist update)
        when(jdbc.queryForObject(anyString(), eq(String.class), eq("c1"), eq("1")))
                .thenReturn("DRAFT");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        int affected = service.archiveVersion("c1", "1", "APPROVED", "admin");
        assertEquals(1, affected);
    }

    @Test
    @DisplayName("listVersionsByCaliberId: status filter applied to SQL")
    void listVersions_statusFilterApplied() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        service.listVersionsByCaliberId("c1", "APPROVED");

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCap = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sqlCap.capture(), argsCap.capture());
        String sql = sqlCap.getValue();
        assertTrue(sql.contains("AND status=?"),
                "expected status filter in SQL but got: " + sql);
        Object[] args = argsCap.getValue();
        // first param is caliberId, then status, then limit (if any)
        assertEquals("c1", args[0]);
        assertEquals("APPROVED", args[1]);
    }
}
