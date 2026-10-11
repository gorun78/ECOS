package com.chinacreator.gzcm.workspace.forecast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;

/**
 * 预测 run 导出通道 —— 详细设计-09 v1.2 §十一 H-6（卷 07 SEC-01，C220）。
 *
 * <p><b>SEC-01 同管道铁律</b>：导出不得自建取数旁路——行级 RLS 与页面查询走<b>同一</b>
 * {@link ScenarioReadGuard#assertReadable(String)} 咽喉（拒绝/不可用 ⇒ 403 PERMISSION_DENIED），
 * 数据体与 {@code GET /{runId}/results} 走<b>同一</b>
 * {@link ForecastRunOrchestrationService#results(String)} 通道（区间三值等真值原样导出，
 * 禁二次计算），列级 CLS 与页面查询走<b>同一</b> {@link ScenarioReadGuard#applyColumns}
 * 裁剪（DENY ⇒ 零行，宁缺不超发）。</p>
 *
 * <p><b>文件头权限声明（卷 07 SEC-01 第 2 条）</b>：CSV 头部注释行写入
 * 权限范围（RLS/CLS 裁决实体）、口径（caliberId@caliberVersion + formulaVersion）与
 * 截至时点 asOf，导出件自带口径与权限自证。</p>
 */
@Service
public class ForecastExportService {

    /** 行级/列级裁决实体编码（与 run 状态/结果读端点同源，SEC-01"同一管道"的锚点）。 */
    static final String ENTITY_CODE = "forecast_run";

    private final ScenarioReadGuard readGuard;
    private final ForecastRunOrchestrationService orchestrationService;

    public ForecastExportService(ScenarioReadGuard readGuard,
                                 ForecastRunOrchestrationService orchestrationService) {
        this.readGuard = readGuard;
        this.orchestrationService = orchestrationService;
    }

    /** 导出产物：CSV 文本 + 文件头权限/口径/asOf 元数据（audit-pack 复用同一产物，禁两套实现）。 */
    public static final class ExportPayload {
        private final String csv;
        private final Map<String, Object> meta;

        ExportPayload(String csv, Map<String, Object> meta) {
            this.csv = csv;
            this.meta = meta;
        }

        public String getCsv() {
            return csv;
        }

        public Map<String, Object> getMeta() {
            return meta;
        }
    }

    /**
     * 构建 run 结果 CSV：四步固定序 = ①RLS 咽喉 → ②同一 results 通道取数 →
     * ③CLS 列裁剪 → ④文件头权限/口径/asOf。任一环拒绝即中断（②③不可越过①）。
     */
    public ExportPayload buildExport(String forecastRunId) {
        // ① 行级 RLS：与页面查询同一咽喉（先于任何取数）
        readGuard.assertReadable(ENTITY_CODE);
        // ② 数据体：与 GET /{runId}/results 同一通道（同管道，禁旁路 SQL/二次计算）
        Map<String, Object> results = orchestrationService.results(forecastRunId);
        // ③ 列级 CLS：与页面查询同一裁剪（DENY → 空行集）
        List<Map<String, Object>> rows = readGuard.applyColumns(ENTITY_CODE, extractRows(results));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("forecastRunId", results.get("forecastRunId"));
        meta.put("status", results.get("status"));
        meta.put("caliberId", results.get("caliberId"));
        meta.put("caliberVersion", results.get("caliberVersion"));
        meta.put("formulaVersion", results.get("formulaVersion"));
        meta.put("asOfTime", results.get("asOfTime"));
        meta.put("snapshotId", results.get("snapshotId"));
        meta.put("currency", results.get("currency"));
        meta.put("unit", results.get("unit"));
        meta.put("taskId", results.get("taskId"));
        meta.put("permissionScope", "rls+cls:" + ENTITY_CODE + "(same-pipeline-as-query, SEC-01)");

        StringBuilder sb = new StringBuilder();
        sb.append("# forecast-export runId=").append(nullSafe(results.get("forecastRunId"))).append('\n');
        sb.append("# permission-scope: ").append(meta.get("permissionScope")).append('\n');
        sb.append("# caliber: ").append(nullSafe(results.get("caliberId")))
                .append('@').append(nullSafe(results.get("caliberVersion")))
                .append("; formulaVersion=").append(nullSafe(results.get("formulaVersion"))).append('\n');
        sb.append("# asOf: ").append(nullSafe(results.get("asOfTime"))).append('\n');
        appendCsv(sb, rows);
        return new ExportPayload(sb.toString(), meta);
    }

    /** 结果 → 明细行集：raw 含 {@code rows:List<Map>} 即明细面；否则整结果单行（原样透传）。 */
    static List<Map<String, Object>> extractRows(Map<String, Object> results) {
        Object rows = results.get("rows");
        if (rows instanceof Collection<?> c) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : c) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, v) -> row.put(String.valueOf(k), v));
                    out.add(row);
                }
            }
            return out;
        }
        return List.of(new LinkedHashMap<>(results));
    }

    /** 列集合 = 行键并集（保序）；null 值出空串（禁造 0/默认值）。 */
    private static void appendCsv(StringBuilder sb, List<Map<String, Object>> rows) {
        Set<String> columns = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            columns.addAll(row.keySet());
        }
        if (columns.isEmpty()) {
            sb.append("# rows=0 (CLS DENY or empty result)\n");
            return;
        }
        List<String> header = new ArrayList<>(columns);
        for (int i = 0; i < header.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(header.get(i)));
        }
        sb.append('\n');
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < header.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escape(row.get(header.get(i))));
            }
            sb.append('\n');
        }
    }

    static String escape(Object v) {
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v);
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static String nullSafe(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
