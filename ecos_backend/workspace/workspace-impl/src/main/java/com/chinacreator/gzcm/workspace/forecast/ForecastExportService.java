package com.chinacreator.gzcm.workspace.forecast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.workspace.security.ExportDecision;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;

/**
 * 预测 run 导出通道 —— 详细设计-09 v1.2 §十一 H-6（卷 07 SEC-01，C220）。
 *
 * <p><b>SEC-01 同管道铁律</b>：导出不得自建取数旁路——行级 RLS 与页面查询走<b>同一</b>
 * {@link ScenarioReadGuard} 咽喉（拒绝/不可用 ⇒ 403/503），数据体与
 * {@code GET /{runId}/results} 走<b>同一</b>
 * {@link ForecastRunOrchestrationService#results(String)} 通道（区间三值等真值原样导出，
 * 禁二次计算），列级 CLS 与页面查询走<b>同一</b> {@link ScenarioReadGuard#applyColumns}
 * 裁剪（DENY ⇒ 零行，宁缺不超发）。</p>
 *
 * <p><b>#142/B4-3b 订正（原文保留）</b>：第①步原为 {@code assertReadable}（{@code /rls/apply}
 * 分散面），现改为 {@link ScenarioReadGuard#exportDecision} —— 即三通道唯一裁决入口
 * {@code POST /api/v1/security/decision}（{@code purpose=export}）。裁决仍先于任何取数，
 * 顺序主张不变，但导出件此后拿到的是<b>四源裁决 + 范围证据</b>而非只有谓词。</p>
 *
 * <p><b>文件头权限声明（卷 07 SEC-01 第 2 条 / §2.10.2 五件套之四）</b>：CSV 头部注释行写入
 * 权限范围（{@code decision=<id> purpose=export rls=[<命中策略>] cls=[...]}，可被审计反查到
 * {@code ecos_rls_policy.policy_name}）、口径（caliberId@caliberVersion + formulaVersion）与
 * 截至时点 asOf；水印义务取 bundle 的 {@code opa.obligations}，<b>未签发时原样落
 * {@code NOT_ISSUED}</b> 而非伪造水印（差距文档 D-B4-12）。旧形态此处是硬编码常量
 * {@code rls+cls:forecast_run(same-pipeline-as-query, SEC-01)}，审计无从反查，已废。</p>
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
     * 构建 run 结果 CSV：四步固定序 = ①导出裁决（三通道唯一入口）→ ②同一 results 通道取数 →
     * ③CLS 列裁剪 → ④文件头权限/水印/口径/asOf。任一环拒绝即中断（②③不可越过①）。
     *
     * <p>第①步产出的 {@link ExportDecision} 是文件头 {@code permission-scope} 与
     * {@code watermark} 两行的<b>唯一</b>取值来源；第④步禁止出现任何本地拼的权限常量。</p>
     */
    public ExportPayload buildExport(String forecastRunId) {
        // ① 导出裁决：三通道唯一入口（purpose=export），先于任何取数；证据（decisionId +
        //    命中策略名 + 水印义务）随导出件外发，供审计反查
        ExportDecision decision = readGuard.exportDecision(ENTITY_CODE);
        // ② 数据体：与 GET /{runId}/results 同一通道（同管道，禁旁路 SQL/二次计算）
        Map<String, Object> results = orchestrationService.results(forecastRunId);
        // ③ 列级 CLS：与页面查询同一裁剪（DENY → 空行集）。整表裁决的 columns 面在
        //    本端点形态下不可用作白名单（D-B4-16 在册），故裁剪仍按行集实际列走 /cls/columns
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
        meta.put("permissionScope", decision.permissionScope());
        meta.put("decisionId", decision.decisionId());
        meta.put("watermark", decision.watermark());

        String watermarkLine = "# watermark: " + decision.watermark() + '\n';
        StringBuilder sb = new StringBuilder();
        sb.append("# forecast-export runId=").append(nullSafe(results.get("forecastRunId"))).append('\n');
        sb.append("# permission-scope: ").append(meta.get("permissionScope")).append('\n');
        sb.append(watermarkLine);
        sb.append("# caliber: ").append(nullSafe(results.get("caliberId")))
                .append('@').append(nullSafe(results.get("caliberVersion")))
                .append("; formulaVersion=").append(nullSafe(results.get("formulaVersion"))).append('\n');
        sb.append("# asOf: ").append(nullSafe(results.get("asOfTime"))).append('\n');
        appendCsv(sb, rows);
        // §2.10.2 五件套之四：水印义务进页眉<b>和</b>页脚（只写一头时尾部可被截断而无痕迹）
        sb.append(watermarkLine);
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
