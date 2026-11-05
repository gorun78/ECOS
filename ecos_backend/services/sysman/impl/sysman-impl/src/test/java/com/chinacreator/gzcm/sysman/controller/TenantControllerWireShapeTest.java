package com.chinacreator.gzcm.sysman.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 租户面 wire 键面保真门（#147CJ · DoD DI-2 第十四批）。
 *
 * <p><b>钉的是什么</b>：列表 4 键＋租户行 12 个 snake 键、配额行 7 键、用量视图 4 键
 * （tenant_id/range_days/daily_usage/totals_by_type）、账单 7 键＋行 5 键；建租户缺省
 * （id 自动 T- 前缀、tenantCode=id 小写、ACTIVE、ROW_FILTER、三个额度 0）；改租户
 * {@code xxxPresent} 语义（显式 null 写空、未提供不动）。</p>
 *
 * <p><b>变异自证</b>：把 {@code TenantRow#tenantName} 的 {@code @JsonProperty("tenant_name")}
 * 去掉 ⇒ 行键集红；把 {@code TenantUpdateRequest#setStatus} 的 present 标记去掉 ⇒
 * 「只给 status」用例失效。</p>
 */
class TenantControllerWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> TENANT_KEYS = List.of("id", "tenant_name", "tenant_code",
            "status", "max_users", "max_storage_mb", "max_api_per_day", "isolation_mode",
            "schema_name", "database_url", "created_at", "updated_at");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return MAPPER.convertValue(value, Map.class);
    }

    private static List<String> keysOf(Object value) {
        return new ArrayList<>(asMap(value).keySet());
    }

    private static TenantService.TenantRow row() {
        return new TenantService.TenantRow("T-1", "租户一", "t-1", "ACTIVE", 10, 1024L, 10000L,
                "ROW_FILTER", null, null,
                Timestamp.valueOf("2026-01-01 00:00:00"), Timestamp.valueOf("2026-01-02 00:00:00"));
    }

    private static TenantService controllerService() {
        return mock(TenantService.class);
    }

    @Test
    @DisplayName("列表：4 键＋租户行 12 个 snake 键（键序）")
    void listKeys() {
        TenantService service = controllerService();
        when(service.listTenants(any(), anyInt(), anyInt()))
                .thenReturn(new TenantService.TenantPage(1, 20, 1, List.of(row())));
        TenantController controller = new TenantController(service);

        TenantService.TenantPage page = controller.list(null, 1, 20).getData();
        assertEquals(List.of("page", "size", "total", "data"), keysOf(page));
        assertEquals(TENANT_KEYS, keysOf(page.data().get(0)));
    }

    @Test
    @DisplayName("详情：无行 ⇒ 404；有行 ⇒ 12 键")
    void getKeys() {
        TenantService service = controllerService();
        TenantController controller = new TenantController(service);

        when(service.getTenant("T-404")).thenReturn(null);
        assertEquals(404, controller.get("T-404").getCode());

        when(service.getTenant("T-1")).thenReturn(row());
        assertEquals(TENANT_KEYS, keysOf(controller.get("T-1").getData()));
    }

    @Test
    @DisplayName("建租户缺省与回执 4 键")
    void createDefaults() {
        TenantService service = controllerService();
        TenantController controller = new TenantController(service);

        TenantController.TenantCreateRequest req = new TenantController.TenantCreateRequest();
        req.setTenantName("新租户");
        ApiResponse<TenantController.CreateResultVO> resp = controller.create(req);

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertEquals(List.of("id", "tenantName", "tenantCode", "status"), keysOf(resp.getData()));
        assertTrue(resp.getData().id().startsWith("T-"));
        assertEquals(resp.getData().id().toLowerCase(), resp.getData().tenantCode());
        assertEquals("ACTIVE", resp.getData().status());

        verify(service).createTenant(anyString(), org.mockito.ArgumentMatchers.eq("新租户"),
                anyString(), org.mockito.ArgumentMatchers.eq("ACTIVE"),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.eq("ROW_FILTER"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    @DisplayName("改租户：只给 status 时 SET 只含 status；显式 null 写空")
    void updatePresenceSemantics() throws Exception {
        TenantService service = controllerService();
        when(service.countTenant("T-1")).thenReturn(1);
        when(service.getTenant("T-1")).thenReturn(row());
        TenantController controller = new TenantController(service);

        TenantController.TenantUpdateRequest onlyStatus = MAPPER.readValue(
                "{\"status\":\"SUSPENDED\"}", TenantController.TenantUpdateRequest.class);
        controller.update("T-1", onlyStatus);

        ArgumentCaptor<StringBuilder> setCaptor = ArgumentCaptor.forClass(StringBuilder.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        verify(service).updateTenant(anyString(), setCaptor.capture(), paramsCaptor.capture());
        String set = setCaptor.getValue().toString();
        assertTrue(set.contains("status = ?"));
        assertTrue(!set.contains("tenant_name = ?"), "未提供 tenant_name 不得进 SET");
        assertEquals(List.of("SUSPENDED"), paramsCaptor.getValue());

        TenantController.TenantUpdateRequest explicitNull = MAPPER.readValue(
                "{\"schemaName\":null}", TenantController.TenantUpdateRequest.class);
        assertTrue(explicitNull.isSchemaNamePresent(),
                "显式 null 必须被 Jackson 识别为「已提供」（若此处红＝setter 未被调用）");
        controller.update("T-1", explicitNull);
        ArgumentCaptor<StringBuilder> setCaptor2 = ArgumentCaptor.forClass(StringBuilder.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> paramsCaptor2 = ArgumentCaptor.forClass(List.class);
        verify(service, org.mockito.Mockito.atLeast(2)).updateTenant(anyString(), setCaptor2.capture(),
                paramsCaptor2.capture());
        assertTrue(setCaptor2.getAllValues().get(1).toString().contains("schema_name = ?"),
                "显式 null 仍写入（containsKey 语义）; 实际 SET=" + setCaptor2.getAllValues());
        assertNull(paramsCaptor2.getAllValues().get(1).get(0));
    }

    @Test
    @DisplayName("配额/用量/账单：键面与键序")
    void quotaUsageInvoiceKeys() {
        TenantService service = controllerService();
        when(service.countTenant("T-1")).thenReturn(1);
        when(service.queryTenantQuotas("T-1")).thenReturn(List.of(
                new TenantService.QuotaRow("q-1", "T-1", "API_CALLS", 100L, 1000L,
                        Timestamp.valueOf("2026-01-01 00:00:00"), Timestamp.valueOf("2026-01-02 00:00:00"))));
        when(service.queryTenantUsageSummary("T-1")).thenReturn(List.of(
                new TenantService.UsageMaxRow("API_CALLS", 42L)));
        when(service.queryDailyUsage(anyString(), any(LocalDate.class))).thenReturn(List.of(
                new TenantService.DailyUsageRow(Date.valueOf("2026-01-02"), "API_CALLS", 2L),
                new TenantService.DailyUsageRow(Date.valueOf("2026-01-02"), "STORAGE_MB", 1L)));
        when(service.queryMonthlyUsage(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(new TenantService.MonthlyUsageRow("API_CALLS", 1000L)));
        when(service.queryQuotaDefinitions("T-1")).thenReturn(List.of(
                new TenantService.QuotaDefRow("API_CALLS", 100L, 1000L)));
        TenantController controller = new TenantController(service);

        TenantController.QuotaResultVO quota = controller.getQuota("T-1").getData();
        assertEquals(List.of("tenantId", "quotas", "usage"), keysOf(quota));
        assertEquals(List.of("id", "tenant_id", "quota_type", "daily_limit", "monthly_limit",
                "created_at", "updated_at"), keysOf(quota.quotas().get(0)));
        assertEquals(List.of("quota_type", "used_count"), keysOf(quota.usage().get(0)));

        TenantController.UsageResultVO usage = controller.getUsage("T-1", "30d").getData();
        assertEquals(List.of("tenant_id", "range_days", "daily_usage", "totals_by_type"), keysOf(usage));
        assertEquals(30, usage.rangeDays());
        assertEquals(Map.of("API_CALLS", 2L, "STORAGE_MB", 1L), usage.totalsByType());
        assertEquals(List.of("usage_date", "quota_type", "used_count"), keysOf(usage.dailyUsage().get(0)));

        TenantController.InvoiceResultVO invoice = controller.getInvoice("T-1", "2026-01").getData();
        assertEquals(List.of("tenant_id", "month", "quota_definitions", "line_items",
                "total_cost_cents", "total_cost_display", "generated_at"), keysOf(invoice));
        assertEquals(List.of("quota_type", "usage", "unit_price", "cost_cents", "cost_display"),
                keysOf(invoice.lineItems().get(0)));
        assertEquals(10L, invoice.totalCostCents(), "1000 次 × ¥0.10/千次 = 10 分");
        assertEquals("¥0.10", invoice.totalCostDisplay());
    }
}
