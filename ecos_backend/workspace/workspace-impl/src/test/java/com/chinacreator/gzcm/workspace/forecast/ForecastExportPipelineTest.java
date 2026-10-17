package com.chinacreator.gzcm.workspace.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.workspace.security.ExportDecision;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * H-6 导出与审计包验收（详设-09 v1.2 §十一 §5.1 rows 12/15，卷 07 SEC-01/SEC-04）。
 *
 * <p><b>SEC-01 同管道</b>是本页的主张：导出的行级裁决、取数通道、列级 CLS 三步必须与页面查询
 * 同一咽喉同一顺序（裁决先于任何取数），断言方式是 {@link InOrder} 而非"结果长得像"；
 * <b>SEC-04</b> 审计包 OPA 默认 DENY，且包内 manifest 逐文件 sha256 可自证完整性。</p>
 *
 * <p><b>#142/B4-3b 订正（原文保留）</b>：第①步咽喉由 {@code assertReadable}（{@code /rls/apply}
 * 分散面）换为 {@code exportDecision}（三通道唯一入口 {@code /api/v1/security/decision}，
 * {@code purpose=export}）。因此本类内的权限范围/水印断言<b>不再是硬编码常量</b>，
 * 而是逐字等于由测试内<b>真实 bundle 形态</b>经 {@code ExportDecision.fromBundle} 产出的串 ——
 * bundle 的键名契约由 security 侧与本侧 {@code ExportDecisionContractTest} 双向钉住，
 * 键名漂移时本类变红（旧断言把 {@code same-pipeline-as-query} 常量钉成"期望值"，
 * 恰好让"导出件自证"这一缺陷无法变红，属结构性假绿，已废）。</p>
 */
@DisplayName("H-6 导出同管道 + 审计包 OPA/manifest")
class ForecastExportPipelineTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 范围证据期望值（由 bundle 逐字推出，禁在断言里重写常量）。 */
    private static final String DECISION_ID = "dec-fin-0001";

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /**
     * 构造 security-engine {@code DecisionBundle} 的 REST 形态（键名与
     * {@code DecisionBundle.toMap()} / {@code SecurityDecisionService} 写入点一致；
     * 契约由 {@code ExportDecisionContractTest} 对 security 源文件做双向钉）。
     *
     * <p><b>#142/B4-3d-7 起 rls 段必须同时给两面</b>（差距 D-B4-24b）：消费侧
     * {@code ExportDecision} 的分态判据已改读<b>执行契约面</b>，夹具只写表达面
     * {@code predicateTemplate} 会让裁决在单源里被判成「应答未发执行契约」的拒绝态，
     * 本类的放行路径用例随即变红（红得有道理，但属夹具漂移而非缺陷）。
     * 零命中时按生产者的真实配对给 {@code whereClause="1=0"}。</p>
     *
     * @param rlsRefs   命中的 RLS 策略名（空集＝无出处 ⇒ 范围证据写 NONE）
     * @param predicate 合并谓词模板（财务行现状为 {@code "(1=1)"}）
     * @param clsRefs   命中的 CLS 策略名
     * @param watermark OPA 水印义务文本；{@code null}＝未签发义务（D-B4-12 现状）
     */
    private static Map<String, Object> bundle(List<String> rlsRefs, String predicate,
                                              List<String> clsRefs, String watermark) {
        boolean noPolicy = rlsRefs.isEmpty();
        Map<String, Object> rls = new LinkedHashMap<>();
        rls.put(RlsExecutionContract.KEY_PREDICATE_TEMPLATE, predicate);
        rls.put("bindings", List.of());
        rls.put("combine", "AND");
        rls.put(RlsExecutionContract.KEY_DENY_IF_EMPTY, true);
        rls.put("policyRefs", rlsRefs);
        rls.put(RlsExecutionContract.KEY_WHERE_CLAUSE,
                noPolicy ? RlsExecutionContract.DENY_CLAUSE : predicate);
        rls.put(RlsExecutionContract.KEY_WHERE_PARAMS, List.of());
        rls.put(RlsExecutionContract.KEY_UNRESOLVED_BINDINGS, List.of());
        rls.put(RlsExecutionContract.KEY_NO_POLICY_MATCHED, noPolicy);
        if (noPolicy) {
            rls.put(RlsExecutionContract.KEY_RLS_DENY_REASON, "RLS_NO_POLICY_MATCHED");
        }

        Map<String, Object> columns = new LinkedHashMap<>();
        columns.put("mode", "allow");
        columns.put("names", List.of("period", "predicted"));
        columns.put("policyRefs", clsRefs);

        Map<String, Object> opa = new LinkedHashMap<>();
        opa.put("allow", true);
        opa.put("policyRef", "ecos.data.access");
        opa.put("obligations", watermark == null ? List.of()
                : List.of(row("obligation", "watermark", "watermark", watermark)));

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("rls", rls);
        b.put("columns", columns);
        b.put("masks", List.of());
        b.put("opa", opa);
        b.put("decisionId", DECISION_ID);
        b.put("traceId", "trace-1");
        return b;
    }

    /** 导出裁决（放行态：命中 rls-finance-all + 谓词恒真）。 */
    private static ExportDecision grantedDecision(String watermark) {
        return ExportDecision.fromBundle("forecast_run",
                bundle(List.of("rls-finance-all"), "(1=1)", List.of(), watermark));
    }

    private static Map<String, Object> resultsWith(List<Map<String, Object>> rows) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("forecastRunId", "fc_1");
        m.put("status", "SUCCEEDED");
        m.put("caliberId", "cal-1");
        m.put("caliberVersion", "v3");
        m.put("formulaVersion", "f-2");
        m.put("asOfTime", "2026-09-30T00:00:00");
        m.put("snapshotId", "snap-1");
        m.put("currency", "CNY");
        m.put("unit", "万元");
        m.put("taskId", "T-9");
        if (rows != null) {
            m.put("rows", rows);
        }
        return m;
    }

    private static ForecastRunView view() {
        return ForecastRunView.builder("fc_1")
                .status(ForecastRunStatus.SUCCEEDED)
                .caliberId("cal-1").caliberVersion("v3").formulaVersion("f-2")
                .asOfTime("2026-09-30T00:00:00").snapshotId("snap-1").taskId("T-9")
                .build();
    }

    private static ForecastRunEvidence evidence() {
        return new ForecastRunEvidence("fc_1", "cal-1", "v3", "2026-09-30T00:00:00", "snap-1", "f-2",
                List.of("ev-1", "ev,2"),
                List.of(row("kind", "ASSUMPTION", "assumptionId", "a-1", "text", "口径按季滚动"),
                        row("kind", "ACTION", "actionId", "ac-1", "status", "APPROVED")));
    }

    // ── 导出：同管道三步 ──

    @Test
    @DisplayName("导出裁决咽喉 → 同一 results 通道 → CLS 裁剪，三步严格同序（禁旁路取数）")
    void exportRunsThroughTheSameGuardOrderAsQuery() {
        ScenarioReadGuard guard = mock(ScenarioReadGuard.class);
        ForecastRunOrchestrationService orchestration = mock(ForecastRunOrchestrationService.class);
        List<Map<String, Object>> page = List.of(row("period", "2026Q3", "predicted", "100.00"));
        when(orchestration.results("fc_1")).thenReturn(resultsWith(page));
        when(guard.exportDecision("forecast_run")).thenReturn(grantedDecision(null));
        when(guard.applyColumns(eq("forecast_run"), anyList())).thenAnswer(i -> i.getArgument(1));

        new ForecastExportService(guard, orchestration).buildExport("fc_1");

        InOrder order = inOrder(guard, orchestration);
        order.verify(guard).exportDecision("forecast_run");
        order.verify(orchestration).results("fc_1");
        order.verify(guard).applyColumns(eq("forecast_run"), anyList());
        // 旧分散面不得再出现在导出链路上（否则"同一入口"只是加了个并行调用）
        verify(guard, never()).assertReadable(anyString());
    }

    @Test
    @DisplayName("裁决拒绝 ⇒ 立即中断，一次取数都不发生（宁缺不超发）")
    void rlsDenyStopsBeforeAnyDataAccess() {
        ScenarioReadGuard guard = mock(ScenarioReadGuard.class);
        ForecastRunOrchestrationService orchestration = mock(ForecastRunOrchestrationService.class);
        doThrow(new com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException("scenario:export:forecast_run"))
                .when(guard).exportDecision(anyString());

        assertThrows(com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException.class,
                () -> new ForecastExportService(guard, orchestration).buildExport("fc_1"));
        verifyNoInteractions(orchestration);
    }

    @Test
    @DisplayName("文件头自证权限/水印/口径/asOf，meta 与裁决证据逐字一致（禁常量）")
    void csvHeaderDeclaresPermissionCaliberAndAsOf() {
        ScenarioReadGuard guard = mock(ScenarioReadGuard.class);
        ForecastRunOrchestrationService orchestration = mock(ForecastRunOrchestrationService.class);
        ExportDecision decision = grantedDecision("U:u-fin|dec:" + DECISION_ID + "|2026-10-15");
        when(orchestration.results("fc_1")).thenReturn(resultsWith(List.of(row("period", "2026Q3"))));
        when(guard.exportDecision("forecast_run")).thenReturn(decision);
        when(guard.applyColumns(anyString(), anyList())).thenAnswer(i -> i.getArgument(1));

        ForecastExportService.ExportPayload payload =
                new ForecastExportService(guard, orchestration).buildExport("fc_1");

        String csv = payload.getCsv();
        assertTrue(csv.startsWith("# forecast-export runId=fc_1\n"), csv);
        assertTrue(csv.contains("# permission-scope: " + decision.permissionScope() + "\n"), csv);
        assertTrue(csv.contains("# caliber: cal-1@v3; formulaVersion=f-2\n"), csv);
        assertTrue(csv.contains("# asOf: 2026-09-30T00:00:00\n"), csv);
        // 范围证据必须能反查到裁决与命中策略（旧常量形态给不出这两样）
        assertTrue(csv.contains("decision=" + DECISION_ID), csv);
        assertTrue(csv.contains("purpose=export"), csv);
        assertTrue(csv.contains("rls=[rls-finance-all]"), csv);
        assertEquals(decision.permissionScope(), payload.getMeta().get("permissionScope"));
        assertEquals(DECISION_ID, payload.getMeta().get("decisionId"));
        // §2.10.2 之四：水印义务进页眉<b>和</b>页脚（只写一头时尾部截断无痕迹）
        assertEquals(decision.watermark(), payload.getMeta().get("watermark"));
        assertEquals(2, countOccurrences(csv, "# watermark: " + decision.watermark()), csv);
        assertEquals("T-9", payload.getMeta().get("taskId"), "异步 taskId 须随导出元数据回显");
    }

    @Test
    @DisplayName("缺陷 D-B4-12 — OPA 未签发水印义务时，导出件原样落 NOT_ISSUED（禁冒充已加水印）")
    void missingWatermarkObligationIsDeclaredNotFabricated() {
        ScenarioReadGuard guard = mock(ScenarioReadGuard.class);
        ForecastRunOrchestrationService orchestration = mock(ForecastRunOrchestrationService.class);
        when(orchestration.results("fc_1")).thenReturn(resultsWith(List.of(row("period", "2026Q3"))));
        when(guard.exportDecision("forecast_run")).thenReturn(grantedDecision(null));
        when(guard.applyColumns(anyString(), anyList())).thenAnswer(i -> i.getArgument(1));

        ForecastExportService.ExportPayload payload =
                new ForecastExportService(guard, orchestration).buildExport("fc_1");

        String csv = payload.getCsv();
        assertTrue(csv.contains("# watermark: " + ExportDecision.WATERMARK_NOT_ISSUED), csv);
        assertEquals(ExportDecision.WATERMARK_NOT_ISSUED, payload.getMeta().get("watermark"));
        // 义务缺失时不得凭空生成看似真实的水印（例如主体名+时间戳）
        assertFalse(csv.contains("# watermark: U:"), csv);
    }

    @Test
    @DisplayName("CLS DENY（零列）⇒ CSV 明示 rows=0，禁以 0 值行冒充有数据")
    void clsDenyProducesZeroRowsNotFabricatedOnes() {
        ScenarioReadGuard guard = mock(ScenarioReadGuard.class);
        ForecastRunOrchestrationService orchestration = mock(ForecastRunOrchestrationService.class);
        when(orchestration.results("fc_1")).thenReturn(resultsWith(List.of(row("period", "2026Q3"))));
        when(guard.exportDecision("forecast_run")).thenReturn(grantedDecision(null));
        when(guard.applyColumns(anyString(), anyList())).thenReturn(List.of());

        String csv = new ForecastExportService(guard, orchestration).buildExport("fc_1").getCsv();

        assertTrue(csv.contains("# rows=0 (CLS DENY or empty result)\n"), csv);
        assertFalse(csv.contains("2026Q3"), "列被裁光后禁把原行漏出: " + csv);
    }

    @Test
    @DisplayName("null 值出空串（禁造 0）；含逗号/引号/换行的值按 CSV 转义")
    void nullBecomesEmptyCellAndPunctuationIsEscaped() {
        ScenarioReadGuard guard = mock(ScenarioReadGuard.class);
        ForecastRunOrchestrationService orchestration = mock(ForecastRunOrchestrationService.class);
        when(orchestration.results("fc_1")).thenReturn(resultsWith(List.of(
                row("period", "2026Q3", "predicted", null, "note", "a,b\"c\nd"))));
        when(guard.exportDecision("forecast_run")).thenReturn(grantedDecision(null));
        when(guard.applyColumns(anyString(), anyList())).thenAnswer(i -> i.getArgument(1));

        String csv = new ForecastExportService(guard, orchestration).buildExport("fc_1").getCsv();
        String fromHeader = csv.substring(csv.indexOf("period,"));
        String dataLine = fromHeader.substring(fromHeader.indexOf('\n') + 1);

        assertTrue(dataLine.startsWith("2026Q3,,\"a,b\"\"c\nd\"\n"), dataLine);
        assertEquals("", ForecastExportService.escape(null));
        assertEquals("100.00", ForecastExportService.escape("100.00"));
    }

    @Test
    @DisplayName("结果无 rows 明细面 ⇒ 整结果原样单行透传（不猜结构、不补列）")
    void resultWithoutRowsDetailPassesThroughAsSingleLine() {
        ScenarioReadGuard guard = mock(ScenarioReadGuard.class);
        ForecastRunOrchestrationService orchestration = mock(ForecastRunOrchestrationService.class);
        when(orchestration.results("fc_1")).thenReturn(resultsWith(null));
        when(guard.exportDecision("forecast_run")).thenReturn(grantedDecision(null));
        when(guard.applyColumns(anyString(), anyList())).thenAnswer(i -> i.getArgument(1));

        String csv = new ForecastExportService(guard, orchestration).buildExport("fc_1").getCsv();

        assertTrue(csv.contains("forecastRunId"), csv);
        assertTrue(csv.contains("fc_1"), csv);
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(guard).applyColumns(eq("forecast_run"), captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    // ── 审计包：OPA 默认 DENY + 七文件 manifest ──

    @Test
    @DisplayName("OPA audit:export 不放行 ⇒ 403 且零取数（SEC-04 默认 DENY）")
    void auditPackDeniedBeforeAnyFetch() {
        ForecastRunOrchestrationService orchestration = mock(ForecastRunOrchestrationService.class);
        ForecastExportService exportService = mock(ForecastExportService.class);
        com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient security =
                mock(com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient.class);
        when(security.evaluate(eq("audit:export"), any())).thenReturn(false);

        assertThrows(com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException.class,
                () -> new ForecastAuditPackService(orchestration, exportService, security).buildAuditPack("fc_1"));
        verifyNoInteractions(orchestration, exportService);
        verify(security, never()).audit(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("放行 ⇒ zip 含 manifest+六文件，逐文件 sha256 可复核，且发 AUDIT_PACK_EXPORT")
    void auditPackCarriesSevenFilesAndVerifiableManifest() throws Exception {
        ForecastRunOrchestrationService orchestration = mock(ForecastRunOrchestrationService.class);
        ForecastExportService exportService = mock(ForecastExportService.class);
        com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient security =
                mock(com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient.class);
        when(security.evaluate(eq("audit:export"), any())).thenReturn(true);
        when(orchestration.status("fc_1")).thenReturn(view());
        when(orchestration.evidence("fc_1")).thenReturn(evidence());
        ExportDecision decision = grantedDecision("U:u-fin|dec:" + DECISION_ID + "|2026-10-15");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("permissionScope", decision.permissionScope());
        meta.put("decisionId", decision.decisionId());
        meta.put("watermark", decision.watermark());
        meta.put("currency", "CNY");
        meta.put("unit", "万元");
        when(exportService.buildExport("fc_1")).thenReturn(
                new ForecastExportService.ExportPayload("# forecast-export runId=fc_1\nperiod,predicted\n2026Q3,100.00\n", meta));

        ForecastAuditPackService.AuditPack pack =
                new ForecastAuditPackService(orchestration, exportService, security).buildAuditPack("fc_1");

        Map<String, byte[]> entries = readZip(pack.getZipBytes());
        assertEquals(List.of("actions.csv", "assumptions.csv", "evidence-index.csv", "formulas.json",
                "input-snapshot.json", "manifest.json", "results.csv"), new ArrayList<>(entries.keySet()).stream()
                        .sorted().toList(), "七文件清单冻结（SEC-04）");
        assertTrue(pack.getFileName().startsWith("forecast_fc_1_audit_") && pack.getFileName().endsWith(".zip"),
                pack.getFileName());

        JsonNode manifest = JSON.readTree(entries.get("manifest.json"));
        assertEquals("fc_1", manifest.get("forecastRunId").asText());
        assertEquals(decision.permissionScope(), manifest.get("permissionScope").asText(),
                "包内权限范围=导出人自身权限（逐字引用导出件的裁决证据，禁另拼一套）");
        assertEquals(DECISION_ID, manifest.get("decisionId").asText(),
                "manifest 必须能反查到本次裁决号");
        assertEquals(decision.watermark(), manifest.get("watermark").asText(),
                "水印义务与导出件同源（禁 manifest 与 CSV 各写一套）");
        assertEquals(6, manifest.get("files").size());
        for (JsonNode file : manifest.get("files")) {
            String name = file.get("name").asText();
            assertEquals(ForecastAuditPackService.sha256Hex(entries.get(name)), file.get("sha256").asText(),
                    name + " 的 manifest 摘要须与包内字节一致");
            assertEquals(entries.get(name).length, file.get("bytes").asInt());
        }
        // results.csv 复用导出产物（禁两套实现）
        assertEquals("# forecast-export runId=fc_1\nperiod,predicted\n2026Q3,100.00\n",
                new String(entries.get("results.csv"), StandardCharsets.UTF_8));
        verify(security).audit(eq("AUDIT_PACK_EXPORT"), eq("forecast_run:fc_1"), eq("OK"));
    }

    @Test
    @DisplayName("证据 kind 分面：ASSUMPTION/ACTION 各自入列，无 kind 标注的条目不错装")
    void evidenceItemsArePartitionedByKindOnlyWhenAnnotated() throws Exception {
        ForecastRunOrchestrationService orchestration = mock(ForecastRunOrchestrationService.class);
        ForecastExportService exportService = mock(ForecastExportService.class);
        com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient security =
                mock(com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient.class);
        when(security.evaluate(eq("audit:export"), any())).thenReturn(true);
        when(orchestration.status("fc_1")).thenReturn(view());
        when(orchestration.evidence("fc_1")).thenReturn(new ForecastRunEvidence(
                "fc_1", "cal-1", "v3", "2026-09-30T00:00:00", "snap-1", "f-2", List.of(),
                List.of(row("assumptionId", "no-kind"))));
        when(exportService.buildExport("fc_1")).thenReturn(
                new ForecastExportService.ExportPayload("run\n", Map.of()));

        Map<String, byte[]> entries = readZip(
                new ForecastAuditPackService(orchestration, exportService, security)
                        .buildAuditPack("fc_1").getZipBytes());

        String assumptions = new String(entries.get("assumptions.csv"), StandardCharsets.UTF_8);
        String actions = new String(entries.get("actions.csv"), StandardCharsets.UTF_8);
        assertFalse(assumptions.contains("no-kind"), "未标 kind 的条目禁被当作 ASSUMPTION 装入: " + assumptions);
        assertFalse(actions.contains("no-kind"), actions);
        assertTrue(new String(entries.get("evidence-index.csv"), StandardCharsets.UTF_8)
                .startsWith("seq,evidenceRef\n"), "证据引用零条时只出表头，禁造行");
    }

    /** 数一段文本在整体里出现的次数（水印要求页眉+页脚双写时用，禁只断"含有一次"）。 */
    private static int countOccurrences(String haystack, String needle) {
        int count = 0, from = 0;
        while ((from = haystack.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static Map<String, byte[]> readZip(byte[] zipBytes) throws Exception {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    buffer.write(chunk, 0, read);
                }
                out.put(entry.getName(), buffer.toByteArray());
            }
        }
        return out;
    }
}
