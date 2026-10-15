package com.chinacreator.gzcm.workspace.forecast;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;
import com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient;

/**
 * 预测 run 审计包导出 —— 详细设计-09 v1.2 §十一 H-6（卷 07 SEC-04）。
 *
 * <p><b>包结构（PRD-07 SEC-04 原文）</b>：
 * {@code forecast_{runId}_audit_{yyyyMMddHHmmss}.zip} = manifest.json /
 * input-snapshot.json / formulas.json / assumptions.csv / results.csv /
 * actions.csv / evidence-index.csv 七文件；manifest 含各文件 sha256 与
 * 币种/单位/口径版本/截至时点。</p>
 *
 * <p><b>同管道纪律（SEC-01，禁旁路）</b>：数据体全部来自页面同一通道——
 * results.csv = {@link ForecastExportService#buildExport}（RLS+CLS 同管道产物）；
 * 快照/公式/假设/动作/证据 = 同一 {@code ForecastRunGateway} SPI 的
 * status/evidence 读面。源未提供的字段以 null 呈现，<b>禁伪造内容</b>
 * （公式文本不落包：只放 caliberId@version + formulaVersion 引用，R-62）。</p>
 *
 * <p><b>裁决与审计</b>：OPA 动作 {@code audit:export} 默认 DENY（security-engine
 * 明确拒绝/应答不可解析 → 403，宁 DENY 不超发；<b>进程不可用</b>是另一态 ——
 * 503 {@code ECOS-SEC-501} 上抛，禁伪装成"没权限"，详设-01 §4.5.4/§4.7.2，
 * 门禁 {@code SecurityDownAllChannelDenyTest#export}）；成功后发审计事件 {@code AUDIT_PACK_EXPORT}
 * （runtime-event 底座单通道 → Kafka ecos.audit）。包内权限范围与水印<b>不自行拼写</b>，
 * 逐字引用 {@link ForecastExportService} 第①步 {@code decide(purpose=export)} 的裁决证据
 * （decisionId + 命中策略名 + obligations 水印），因此 §4.7.2「manifest 标 scope」的断言
 * 可被审计反查到 {@code ecos_rls_policy}（#142/B4-3b，原文常量自证形态已废）。</p>
 */
@Service
public class ForecastAuditPackService {

    private static final Logger log = LoggerFactory.getLogger(ForecastAuditPackService.class);

    /** OPA 裁决动作字面量（PRD-07 SEC-04 冻结）。 */
    static final String ACTION_AUDIT_EXPORT = "audit:export";
    /** 审计事件类型字面量（PRD-07 SEC-04 冻结）。 */
    static final String AUDIT_EVENT_TYPE = "AUDIT_PACK_EXPORT";

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ForecastRunOrchestrationService orchestrationService;
    private final ForecastExportService exportService;
    private final WorkspaceSecurityEngineClient securityClient;

    public ForecastAuditPackService(ForecastRunOrchestrationService orchestrationService,
                                    ForecastExportService exportService,
                                    WorkspaceSecurityEngineClient securityClient) {
        this.orchestrationService = orchestrationService;
        this.exportService = exportService;
        this.securityClient = securityClient;
    }

    /** 审计包产物：zip 字节 + 文件名（controller 落 Content-Disposition）。 */
    public static final class AuditPack {
        private final String fileName;
        private final byte[] zipBytes;

        AuditPack(String fileName, byte[] zipBytes) {
            this.fileName = fileName;
            this.zipBytes = zipBytes;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getZipBytes() {
            return zipBytes;
        }
    }

    /**
     * 构建审计包：①OPA {@code audit:export} 裁决（默认 DENY→403）→ ②同管道取数
     * （status/results(RLS+CLS CSV)/evidence）→ ③七文件 + sha256 manifest →
     * ④审计事件 {@code AUDIT_PACK_EXPORT}（成功留痕；事件发布失败不逆转导出结果，
     * 由 {@code WorkspaceSecurityEngineClient} 底座侧 WARN）。
     */
    public AuditPack buildAuditPack(String forecastRunId) {
        // ① SEC-04 OPA 裁决（明确拒绝/应答不可解析 = false → 403，宁 DENY 不超发；
        //    进程不可用 → 客户端单点转 503 ECOS-SEC-501 上抛，取数面零触达）
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("resource", "forecast_run");
        attrs.put("forecastRunId", forecastRunId);
        if (!securityClient.evaluate(ACTION_AUDIT_EXPORT, attrs)) {
            throw new ForbiddenOperationException("forecast:" + ACTION_AUDIT_EXPORT,
                    Map.of("forecastRunId", forecastRunId));
        }

        // ② 同管道取数（未装配 SPI 诚实 501：status/evidence/results 同咽喉）
        ForecastRunView view = orchestrationService.status(forecastRunId);
        ForecastExportService.ExportPayload export = exportService.buildExport(forecastRunId);
        ForecastRunEvidence evidence = orchestrationService.evidence(forecastRunId);

        String generatedAt = LocalDateTime.now().format(TS);
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("input-snapshot.json", jsonBytes(inputSnapshot(view, evidence)));
        files.put("formulas.json", jsonBytes(formulasRef(view)));
        files.put("assumptions.csv", csvBytes("assumptionId,text,sourceRef,version",
                filterItems(evidence, "ASSUMPTION")));
        files.put("results.csv", export.getCsv().getBytes(StandardCharsets.UTF_8));
        files.put("actions.csv", csvBytes("actionId,status,decidedBy,expectedImpact,controlMetric",
                filterItems(evidence, "ACTION")));
        files.put("evidence-index.csv", evidenceIndexBytes(evidence));

        List<Map<String, Object>> manifestFiles = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", e.getKey());
            entry.put("sha256", sha256Hex(e.getValue()));
            entry.put("bytes", e.getValue().length);
            manifestFiles.add(entry);
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("forecastRunId", forecastRunId);
        manifest.put("fileName", "forecast_" + forecastRunId + "_audit_" + generatedAt + ".zip");
        manifest.put("generatedAt", generatedAt);
        manifest.put("permissionScope", export.getMeta().get("permissionScope"));
        // #142/B4-3b：水印与裁决号引用导出件真值（同源，禁在包层再拼一套）。
        // 义务未签发时值为 NOT_ISSUED —— 那是 D-B4-12 的在册痕迹，不是"已加水印"。
        manifest.put("watermark", export.getMeta().get("watermark"));
        manifest.put("decisionId", export.getMeta().get("decisionId"));
        manifest.put("currency", export.getMeta().get("currency"));
        manifest.put("unit", export.getMeta().get("unit"));
        manifest.put("caliberId", view.getCaliberId());
        manifest.put("caliberVersion", view.getCaliberVersion());
        manifest.put("formulaVersion", view.getFormulaVersion());
        manifest.put("asOfTime", view.getAsOfTime());
        manifest.put("snapshotId", view.getSnapshotId());
        manifest.put("status", view.getStatus() == null ? null : view.getStatus().name());
        manifest.put("taskId", view.getTaskId());
        manifest.put("files", manifestFiles);
        byte[] manifestBytes = jsonBytes(manifest);

        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zip, StandardCharsets.UTF_8)) {
            writeEntry(out, "manifest.json", manifestBytes);
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                writeEntry(out, e.getKey(), e.getValue());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("审计包 zip 组装失败: runId=" + forecastRunId, ex);
        }

        // ④ 审计事件（底座 EventBus 单通道；PRD-07 SEC-04 eventType 冻结）
        securityClient.audit(AUDIT_EVENT_TYPE, "forecast_run:" + forecastRunId, "OK");
        log.info("审计包已导出 runId={} entries={}", forecastRunId, files.size() + 1);
        return new AuditPack("forecast_" + forecastRunId + "_audit_" + generatedAt + ".zip",
                zip.toByteArray());
    }

    // ── 文件体（源未提供 = null/零行，禁造数） ──

    private static Map<String, Object> inputSnapshot(ForecastRunView view, ForecastRunEvidence evidence) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("snapshotId", view.getSnapshotId());
        m.put("asOfTime", view.getAsOfTime());
        m.put("factRefs", evidence.getEvidenceItems().isEmpty() ? null : evidence.getEvidenceItems());
        m.put("note", "输入快照实体由预测输入快照表落位（卷02/引擎属主）；编排面仅搬运引用，禁重建快照内容");
        return m;
    }

    private static Map<String, Object> formulasRef(ForecastRunView view) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caliberId", view.getCaliberId());
        m.put("caliberVersion", view.getCaliberVersion());
        m.put("formulaVersion", view.getFormulaVersion());
        m.put("note", "公式正文不落审计包（R-62 禁编排面拼公式）；按口径引用经卷02/03 口径库回查");
        return m;
    }

    /** 证据条目按 kind 过滤（引擎未标注 kind ⇒ 零行 + note，禁把非本类条目错装）。 */
    private static List<Map<String, Object>> filterItems(ForecastRunEvidence evidence, String kind) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : evidence.getEvidenceItems()) {
            if (kind.equalsIgnoreCase(String.valueOf(item.get("kind")))) {
                out.add(item);
            }
        }
        return out;
    }

    private static byte[] evidenceIndexBytes(ForecastRunEvidence evidence) {
        StringBuilder sb = new StringBuilder("seq,evidenceRef\n");
        List<String> refs = evidence.getEvidenceRefs();
        for (int i = 0; i < refs.size(); i++) {
            sb.append(i + 1).append(',').append(ForecastExportService.escape(refs.get(i))).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] csvBytes(String header, List<Map<String, Object>> items) {
        StringBuilder sb = new StringBuilder();
        List<String> baseCols = List.of(header.split(","));
        java.util.LinkedHashSet<String> cols = new java.util.LinkedHashSet<>(baseCols);
        for (Map<String, Object> item : items) {
            cols.addAll(item.keySet());
        }
        sb.append(String.join(",", cols)).append('\n');
        for (Map<String, Object> item : items) {
            boolean first = true;
            for (String col : cols) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(ForecastExportService.escape(item.get(col)));
            }
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] jsonBytes(Map<String, Object> body) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(body);
        } catch (Exception e) {
            throw new IllegalStateException("审计包 JSON 序列化失败", e);
        }
    }

    private static void writeEntry(ZipOutputStream out, String name, byte[] content) throws java.io.IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(content);
        out.closeEntry();
    }

    static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用（JDK 强制算法，理论不可达）", e);
        }
    }
}
