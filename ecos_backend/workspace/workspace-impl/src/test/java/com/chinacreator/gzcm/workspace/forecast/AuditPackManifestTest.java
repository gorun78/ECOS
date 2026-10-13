package com.chinacreator.gzcm.workspace.forecast;

import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;
import com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuditPackManifestTest — 详设-01 F01-11（REQ-SEC-04 / PRD-07 §4.1-4.2）审计包门禁。
 *
 * <p>冻结验收面（详设-01 F01-11 行，M3）：
 * ① zip = PRD §4.1 七文件（manifest.json + 6 数据件）；
 * ② manifest 逐件 sha256 checksum 与包内实际字节一致（确定性重算）；
 * ③ manifest 头部元数据完整（币种/单位/口径版本/截至时点 —— PRD §4.2 规则 2）；
 * ④ 成功导出发审计事件 AUDIT_PACK_EXPORT（PRD §4.2 规则 3）；
 * ⑤ OPA audit:export 默认 DENY → 403（ForbiddenOperationException），不发成功事件。</p>
 */
class AuditPackManifestTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> EXPECTED_FILES = List.of(
            "manifest.json", "input-snapshot.json", "formulas.json",
            "assumptions.csv", "results.csv", "actions.csv", "evidence-index.csv");

    private ForecastRunOrchestrationService orchestration;
    private ForecastExportService exportService;
    private WorkspaceSecurityEngineClient securityClient;
    private ForecastAuditPackService service;

    @BeforeEach
    void setUp() {
        orchestration = mock(ForecastRunOrchestrationService.class);
        exportService = mock(ForecastExportService.class);
        securityClient = mock(WorkspaceSecurityEngineClient.class);
        service = new ForecastAuditPackService(orchestration, exportService, securityClient);

        ForecastRunView view = ForecastRunView.builder("fc_1")
                .status(ForecastRunStatus.SUCCEEDED)
                .caliberId("cal_9")
                .caliberVersion("v3")
                .formulaVersion("fv_2")
                .asOfTime("2026-09-30T00:00:00")
                .snapshotId("snap_7")
                .build();
        ForecastRunEvidence evidence = new ForecastRunEvidence(
                "fc_1", "cal_9", "v3", "2026-09-30T00:00:00", "snap_7", "fv_2",
                List.of("ref-a", "ref-b"),
                List.of(
                        item("ASSUMPTION", "a1", "画像假设 A"),
                        item("ACTION", "act1", "经营动作 X")));
        when(orchestration.status("fc_1")).thenReturn(view);
        when(orchestration.evidence("fc_1")).thenReturn(evidence);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("currency", "CNY");
        meta.put("unit", "万元");
        meta.put("permissionScope", "self");
        when(exportService.buildExport("fc_1"))
                .thenReturn(new ForecastExportService.ExportPayload("period,revenue\n2026Q1,100\n", meta));
    }

    private static Map<String, Object> item(String kind, String id, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("id", id);
        m.put("text", text);
        return m;
    }

    private static Map<String, byte[]> unzip(byte[] zipBytes) throws Exception {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry e;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            while ((e = in.getNextEntry()) != null) {
                buf.reset();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
                out.put(e.getName(), buf.toByteArray());
                in.closeEntry();
            }
        }
        return out;
    }

    @Test
    @DisplayName("成功路径：七文件齐套 + manifest 逐件 sha256 与包内字节一致 + 元数据头完整")
    void manifestChecksumsMatchPackBytes() throws Exception {
        when(securityClient.evaluate(eq("audit:export"), anyMap())).thenReturn(true);

        ForecastAuditPackService.AuditPack pack = service.buildAuditPack("fc_1");
        assertNotNull(pack.getFileName());
        assertTrue(pack.getFileName().startsWith("forecast_fc_1_audit_"),
                "文件名须为 forecast_{runId}_audit_{ts}.zip 形态: " + pack.getFileName());

        Map<String, byte[]> files = unzip(pack.getZipBytes());
        assertEquals(EXPECTED_FILES, List.copyOf(files.keySet()),
                "PRD-07 §4.1 七文件冻结（manifest 首位）");

        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = JSON.readValue(files.get("manifest.json"), Map.class);
        // ③ 元数据头（币种/单位/口径版本/截至时点，PRD §4.2 规则 2）
        assertEquals("CNY", manifest.get("currency"));
        assertEquals("万元", manifest.get("unit"));
        assertEquals("v3", manifest.get("caliberVersion"));
        assertEquals("2026-09-30T00:00:00", manifest.get("asOfTime"));
        assertEquals("self", manifest.get("permissionScope"));

        // ② 逐件 checksum：manifest 声明的 sha256/bytes 必须与包内实际字节重算一致
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) manifest.get("files");
        assertEquals(6, entries.size(), "manifest.files 应覆盖除 manifest.json 外的 6 件");
        for (Map<String, Object> entry : entries) {
            String name = String.valueOf(entry.get("name"));
            byte[] actual = files.get(name);
            assertNotNull(actual, "manifest 声明的文件须真实存在于包内: " + name);
            assertEquals(ForecastAuditPackService.sha256Hex(actual), entry.get("sha256"),
                    name + " sha256 与包内字节不一致");
            assertEquals(((Number) entry.get("bytes")).longValue(), actual.length,
                    name + " bytes 与包内长度不一致");
        }

        // ④ 导出动作审计事件（eventType 冻结 AUDIT_PACK_EXPORT）
        verify(securityClient).audit("AUDIT_PACK_EXPORT", "forecast_run:fc_1", "OK");
    }

    @Test
    @DisplayName("OPA audit:export 默认 DENY → 403 抛 ForbiddenOperationException，不发成功审计")
    void denyPathProducesNoPack() {
        when(securityClient.evaluate(anyString(), anyMap())).thenReturn(false);

        assertThrows(ForbiddenOperationException.class, () -> service.buildAuditPack("fc_1"));
        verify(securityClient, never()).audit(eq("AUDIT_PACK_EXPORT"), anyString(), eq("OK"));
    }
}
