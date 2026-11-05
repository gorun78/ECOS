package com.chinacreator.gzcm.engine.ontology.controller;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberVersionSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.MetricBindingDTO;
import com.chinacreator.gzcm.engine.ontology.service.CaliberFormulaDslService;
import com.chinacreator.gzcm.engine.ontology.service.CaliberService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 口径端点 wire 逐字保真（DI-2 #147DJ）。
 *
 * <p>旧 Map 时代各端点键集在此按<b>顺序敏感</b>集合断言：强类型 record 的序列化顺序
 * 必须与属主 DAO 显式列清单同序，键少一个或多一个即红。夹具走真实
 * {@link CaliberService} 行 Map（DAO 契约未改），转换在 DTO {@code of(Map)}。</p>
 */
class CaliberWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CODE = "CAL_X";

    private CaliberService caliberService;
    private CaliberController controller;

    @BeforeEach
    void setUp() {
        caliberService = mock(CaliberService.class);
        controller = new CaliberController(caliberService, mock(CaliberFormulaDslService.class));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("current：18 键别名列逐字冻结，null 值照常出现（跨引擎只读面）")
    void currentKeepsAllAliasedColumnsIncludingNulls() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("caliber_id", "cid-1");
        row.put("code", CODE);
        row.put("name", "收入口径");
        row.put("caliber_status", "ACTIVE");
        row.put("unit", "CNY");
        row.put("currency", "CNY");
        row.put("owner_role", "role.CFO");
        row.put("dimension_json", "{\"org\":\"all\"}");
        row.put("domain", "default");
        row.put("version_id", "vid-2");
        row.put("version_no", "2");
        row.put("formula", null);
        row.put("additive", Short.valueOf((short) 1));
        row.put("period_granularity", "MONTH");
        row.put("version_status", "APPROVED");
        row.put("approved_by", "admin");
        row.put("approved_at", Timestamp.valueOf("2026-11-05 10:00:00"));
        row.put("git_ref", "abc1234");
        when(caliberService.getByCaliberCodeAndCurrentVersion(CODE)).thenReturn(Optional.of(row));

        JsonNode data = dataOf(controller.current(CODE));

        assertEquals(List.of("caliber_id", "code", "name", "caliber_status", "unit", "currency",
                "owner_role", "dimension_json", "domain", "version_id", "version_no", "formula",
                "additive", "period_granularity", "version_status", "approved_by", "approved_at",
                "git_ref"), fieldNames(data));
        assertTrue(data.has("formula"), "null formula 的键必须照常出现（旧 Map 同形）");
        assertEquals(1, data.get("additive").asInt(), "SMALLINT 按数值透出");
    }

    @Test
    @DisplayName("detail：主行 10 键 + versions[] 16 键（升版历史强类型化）")
    void detailKeepsMainRowAndVersionHistoryKeys() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "cid-1");
        row.put("code", CODE);
        row.put("name", "收入口径");
        row.put("status", "ACTIVE");
        row.put("unit", "CNY");
        row.put("currency", "CNY");
        row.put("owner_role", "role.CFO");
        row.put("dimension_json", null);
        row.put("domain", "default");
        row.put("create_time", Timestamp.valueOf("2026-11-01 00:00:00"));
        when(caliberService.findByCode(CODE)).thenReturn(Optional.of(row));

        Map<String, Object> version = new LinkedHashMap<>();
        version.put("id", "vid-1");
        version.put("caliber_id", "cid-1");
        version.put("version_no", "1");
        version.put("formula", "M_FC_REVENUE = SUM(fact.revenue)");
        version.put("additive", Short.valueOf((short) 1));
        version.put("period_granularity", "MONTH");
        version.put("status", "DRAFT");
        version.put("approved_by", null);
        version.put("approved_at", null);
        version.put("git_ref", null);
        version.put("create_time", Timestamp.valueOf("2026-11-01 00:00:00"));
        version.put("update_time", Timestamp.valueOf("2026-11-01 00:00:00"));
        version.put("create_by", "admin");
        version.put("update_by", null);
        version.put("is_deleted", Short.valueOf((short) 0));
        version.put("domain", "default");
        when(caliberService.listVersionsByCaliberId("cid-1", null)).thenReturn(List.of(version));

        JsonNode data = dataOf(controller.detail(CODE));

        assertEquals(List.of("id", "code", "name", "status", "unit", "currency", "ownerRole",
                "domain", "createTime", "versions"), fieldNames(data));
        JsonNode history = data.get("versions");
        assertEquals(1, history.size());
        assertEquals(List.of("id", "caliber_id", "version_no", "formula", "additive",
                "period_granularity", "status", "approved_by", "approved_at", "git_ref",
                "create_time", "update_time", "create_by", "update_by", "is_deleted", "domain"),
                fieldNames(history.get(0)));
    }

    @Test
    @DisplayName("addVersion：读回行 10 键（version_no 与 status 强类型承载）")
    void addVersionKeepsReadbackKeys() throws Exception {
        when(caliberService.findByCode(CODE)).thenReturn(Optional.of(Map.of("id", "cid-1", "code", CODE)));
        Map<String, Object> inserted = new LinkedHashMap<>();
        inserted.put("id", "vid-2");
        inserted.put("caliber_id", "cid-1");
        inserted.put("version_no", "2");
        inserted.put("formula", null);
        inserted.put("additive", null);
        inserted.put("period_granularity", null);
        inserted.put("status", "DRAFT");
        inserted.put("approved_by", null);
        inserted.put("approved_at", null);
        inserted.put("git_ref", null);
        when(caliberService.insertVersion(eq("cid-1"), eq("2"), any(), any(), any(), any()))
                .thenReturn(inserted);

        CaliberVersionSaveDTO dto = new CaliberVersionSaveDTO();
        dto.setVersionNo("2");
        JsonNode data = dataOf(controller.addVersion(CODE, dto));

        assertEquals(List.of("id", "caliber_id", "version_no", "formula", "additive",
                "period_granularity", "status", "approved_by", "approved_at", "git_ref"),
                fieldNames(data));
        assertEquals("DRAFT", data.get("status").asText());
    }

    @Test
    @DisplayName("approve 4 键 / retire 3 键（approvedBy 仅审批腿出现）")
    void stateChangeKeysDependOnLeg() throws Exception {
        when(caliberService.findByCode(CODE)).thenReturn(Optional.of(Map.of("id", "cid-1", "code", CODE)));
        when(caliberService.archiveVersion("cid-1", "1", "APPROVED", "admin")).thenReturn(1);
        when(caliberService.archiveVersion("cid-1", "1", "RETIRED", "admin")).thenReturn(1);

        UserContext ctx = new UserContext();
        ctx.setUsername("admin");
        UserContext.setCurrent(ctx);

        JsonNode approved = dataOf(controller.approve(CODE, "1"));
        assertEquals(List.of("code", "versionNo", "status", "approvedBy"), fieldNames(approved));
        assertEquals("APPROVED", approved.get("status").asText());

        JsonNode retired = dataOf(controller.retire(CODE, "1"));
        assertEquals(List.of("code", "versionNo", "status"), fieldNames(retired));
        assertEquals("RETIRED", retired.get("status").asText());
        assertFalse(retired.has("approvedBy"), "退休腿不得凭空多出 approvedBy");
    }

    @Test
    @DisplayName("bindMetric：4 键（metricCode + 3 枚 snake 键）")
    void bindMetricKeepsServicesReceiptKeys() throws Exception {
        when(caliberService.findByCode(CODE)).thenReturn(Optional.of(Map.of("id", "cid-1", "code", CODE)));
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("metricCode", "M_FC_REVENUE");
        receipt.put("caliber_id", "cid-1");
        receipt.put("formula_version", "1");
        receipt.put("caliber_snapshot", "{\"caliber_id\":\"cid-1\"}");
        when(caliberService.bindMetricCaliber("M_FC_REVENUE", "cid-1", "1")).thenReturn(receipt);

        MetricBindingDTO dto = new MetricBindingDTO();
        dto.setCaliberCode(CODE);
        dto.setFormulaVersion("1");
        JsonNode data = dataOf(controller.bindMetric("M_FC_REVENUE", dto));

        assertEquals(List.of("metricCode", "caliber_id", "formula_version", "caliber_snapshot"),
                fieldNames(data));
    }

    @Test
    @DisplayName("conflict-count：count + evidence.criterion（判据字面量冻结）")
    void conflictCountKeepsEvidenceCriterion() throws Exception {
        when(caliberService.countConflictedCaliberCodes()).thenReturn(3);

        JsonNode data = dataOf(controller.conflictCount());

        assertEquals(List.of("count", "evidence"), fieldNames(data));
        assertEquals(3, data.get("count").asInt());
        assertEquals("multi-APPROVED-versions-per-code",
                data.get("evidence").get("criterion").asText());
    }

    // ── 辅助 ─────────────────────────────────────────────

    private static JsonNode dataOf(ApiResponse<?> resp) throws Exception {
        assertEquals(0, resp.getCode(), "夹具必须走成功信封: " + resp.getMessage());
        return MAPPER.valueToTree(resp).get("data");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
