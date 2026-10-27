package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.engine.data.fact.dao.ForecastRunSnapshotMapper;
import com.chinacreator.gzcm.engine.data.model.ForecastRunSnapshotVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 预测运行只读快照查询服务 — 详细设计-09 v1.2 §十一 H-3 交付③（ST08 读通道）。
 *
 * <p>数据源：{@code ecos_dw.ecos_application_forecast_run}（V197 头 + V277 六要素增列），
 * 按 {@code forecast_run_id} 唯一定位（uniq_afcr_run_id 索引命中，禁全库扫描——
 * cognitive {@code ForecastRunSnapshotReader.SnapshotQuery} 要点 4）。
 * 本 service <b>只读</b>：写路径仍归预测运行写通道（R-17 ①/ADR-14），本撮不新增任何写。
 *
 * <p><b>快照明细摄取契约（cells/edges/profits）</b>：读 {@code simulation_result_json}，
 * 缺次读 {@code forecast_result_json}（优先级固定、可复现）；期望形态——
 * <pre>
 * {
 *   "baselineProfit": 1234.56, "scenarioProfit": 1300.00,
 *   "cells":  [ {"projectId":"P1","period":202601,"baseline":1.0,"scenario":2.0}, ... ],
 *   "edges":  [ {"chainId":"C1","path":[{"node":"N1","type":"EVENT"}],
 *               "baseline":1.0,"scenario":2.0,"evidenceRefs":["E1"],
 *               "statSupport":{"method":"ols","pValue":0.03}}, ... ]
 * }
 * </pre>
 * 非 JSON 形态（{@code DeterministicForecastCalculator.canonicalBytes} 的
 * {@code sixFactorLine|...} 文本，实证存在）⇒ cells/edges 回空列表、利润回 null，
 * <b>禁伪造数值</b>（认知侧对空明细按 DG-9/fail-loud 处理，本层不合成）。
 * 键名兼容 snake_case（baseline_profit/scenario_profit、chain_id、evidence_refs、stat_support、
 * project_id）与 camel 双形态；未知键忽略、缺失键置 null（只增不改的前向兼容读）。
 */
@Service
public class ForecastRunSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(ForecastRunSnapshotService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ForecastRunSnapshotMapper snapshotMapper;

    public ForecastRunSnapshotService(ForecastRunSnapshotMapper snapshotMapper) {
        this.snapshotMapper = snapshotMapper;
    }

    /**
     * 按运行键取只读快照。
     *
     * @return 六要素 + 上下文 + 利润真值（可空）+ cells/edges 明细（可空列表）
     * @throws com.chinacreator.gzcm.common.exception.DataBridgeException 404（runId 无行——
     *         禁把"行不存在"伪装成空快照）
     */
    public ForecastRunSnapshotVO getSnapshot(String forecastRunId) {
        if (forecastRunId == null || forecastRunId.isBlank()) {
            throw new com.chinacreator.gzcm.common.exception.DataBridgeException(
                    400, 400, DataDomainErrorCode.ECOS_DATA_023 + "：forecastRunId 不能为空（禁全库扫描）", null);
        }
        Map<String, Object> header = snapshotMapper.selectRunHeader(forecastRunId.trim());
        if (header == null) {
            throw new com.chinacreator.gzcm.common.exception.DataBridgeException(
                    404, 404, "预测运行不存在: " + forecastRunId, null);
        }
        return toSnapshotVO(header);
    }

    /**
     * 按幂等键取只读快照（详设-09 H-5 幂等复用判定链 / V287 增列，只读）。
     *
     * <p>与 {@link #getSnapshot} 的唯一语义差别：<b>未命中返回 null 而非 404</b>。
     * 理由：幂等键未命中是"首次运行"的正常分支（编排面据此继续 submit），不是资源缺失；
     * 把正常分支做成 404 会诱使调用侧用异常控制流程并掩盖真实的路由未上线（X-44）。</p>
     *
     * @return 命中返回 VO；未命中返回 null（调用方禁据此造数）
     * @throws com.chinacreator.gzcm.common.exception.DataBridgeException 400（键空白——禁全库扫描）
     */
    public ForecastRunSnapshotVO getSnapshotByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new com.chinacreator.gzcm.common.exception.DataBridgeException(
                    400, 400, DataDomainErrorCode.ECOS_DATA_023 + "：idempotencyKey 不能为空（禁全库扫描）", null);
        }
        Map<String, Object> header = snapshotMapper.selectRunHeaderByIdempotencyKey(idempotencyKey.trim());
        return header == null ? null : toSnapshotVO(header);
    }

    /** 运行头 → 快照 VO（两条读路径共用同一映射，禁形态分叉）。 */
    private ForecastRunSnapshotVO toSnapshotVO(Map<String, Object> header) {
        ForecastRunSnapshotVO vo = new ForecastRunSnapshotVO();
        vo.setForecastRunId(str(header.get("forecast_run_id")));
        vo.setCaliberId(str(header.get("caliber_id")));
        vo.setCaliberVersion(str(header.get("caliber_version")));
        vo.setAsOfTime(iso(header.get("as_of_time")));
        vo.setSnapshotId(str(header.get("snapshot_id")));
        vo.setFormulaVersion(str(header.get("formula_version")));
        vo.setScenarioId(str(header.get("scenario_id")));
        vo.setRunType(str(header.get("run_type")));
        vo.setStatus(str(header.get("status")));
        vo.setBaselineRunId(str(header.get("baseline_run_id")));
        // V277/V287 增列：历史行缺值原样 null（禁伪造回填）；cognitive 侧 SnapshotLineage.reused() 依赖本键
        vo.setReusedFromRunId(str(header.get("reused_from_run_id")));
        vo.setTaskId(str(header.get("task_id")));
        vo.setIdempotencyKey(str(header.get("idempotency_key")));
        vo.setContentHash(str(header.get("content_hash")));

        JsonNode payload = firstParseable(str(header.get("simulation_result_json")),
                str(header.get("forecast_result_json")));
        if (payload == null) {
            // canonicalBytes 文本/空载荷形态：明细空、利润 null（fail-loud 语义归调用侧，禁伪造）
            vo.setCells(List.of());
            vo.setEdges(List.of());
            return vo;
        }
        vo.setBaselineProfit(decimal(payload, "baselineProfit", "baseline_profit"));
        vo.setScenarioProfit(decimal(payload, "scenarioProfit", "scenario_profit"));
        vo.setCells(parseCells(array(payload, "cells")));
        vo.setEdges(parseEdges(array(payload, "edges")));
        return vo;
    }

    // ── 解析（全宽容：坏节点跳过并 WARN，不抛、不伪造）──────────────

    private JsonNode firstParseable(String... jsonCandidates) {
        for (String raw : jsonCandidates) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String t = raw.trim();
            if (!t.startsWith("{") && !t.startsWith("[")) {
                continue;   // canonicalBytes 等非 JSON 形态，直落下一候选
            }
            try {
                JsonNode node = MAPPER.readTree(t);
                if (node != null && node.isObject()) {
                    return node;
                }
            } catch (Exception e) {
                log.warn("[Snapshot] result JSON 解析失败（按无快照明细处理）: {}", e.getMessage());
            }
        }
        return null;
    }

    private static JsonNode array(JsonNode payload, String key) {
        JsonNode v = payload.get(key);
        return v != null && v.isArray() ? v : null;
    }

    private List<ForecastRunSnapshotVO.CellVO> parseCells(JsonNode cells) {
        List<ForecastRunSnapshotVO.CellVO> out = new ArrayList<>();
        if (cells == null) {
            return out;
        }
        for (JsonNode c : cells) {
            if (!c.isObject()) {
                continue;
            }
            ForecastRunSnapshotVO.CellVO vo = new ForecastRunSnapshotVO.CellVO();
            vo.setProjectId(text(c, "projectId", "project_id"));
            JsonNode p = c.get("period");
            if (p != null && !p.isNull()) {
                if (p.isNumber()) {
                    vo.setPeriod(p.intValue());        // Cell.period = int（原样保真）
                } else {
                    try {
                        vo.setPeriod(Integer.parseInt(p.asText().trim()));
                    } catch (NumberFormatException e) {
                        log.warn("[Snapshot] cell.period 非整型形态，置 null 原样上报（禁 clamp/伪造）: {}", p.asText());
                    }
                }
            }
            vo.setBaseline(decimal(c, "baseline", "baseline"));
            vo.setScenario(decimal(c, "scenario", "scenario"));
            out.add(vo);
        }
        return out;
    }

    private List<ForecastRunSnapshotVO.EdgeVO> parseEdges(JsonNode edges) {
        List<ForecastRunSnapshotVO.EdgeVO> out = new ArrayList<>();
        if (edges == null) {
            return out;
        }
        for (JsonNode e : edges) {
            if (!e.isObject()) {
                continue;
            }
            ForecastRunSnapshotVO.EdgeVO vo = new ForecastRunSnapshotVO.EdgeVO();
            vo.setChainId(text(e, "chainId", "chain_id"));
            vo.setBaseline(decimal(e, "baseline", "baseline"));
            vo.setScenario(decimal(e, "scenario", "scenario"));
            JsonNode path = e.get("path");
            if (path != null && path.isArray()) {
                List<ForecastRunSnapshotVO.PathNodeVO> nodes = new ArrayList<>();
                for (Iterator<JsonNode> it = path.elements(); it.hasNext(); ) {
                    JsonNode n = it.next();
                    ForecastRunSnapshotVO.PathNodeVO pn = new ForecastRunSnapshotVO.PathNodeVO();
                    pn.setNode(text(n, "node", "node"));
                    pn.setType(text(n, "type", "type"));
                    nodes.add(pn);
                }
                vo.setPath(nodes);
            }
            JsonNode refs = e.get("evidenceRefs") != null ? e.get("evidenceRefs") : e.get("evidence_refs");
            if (refs != null && refs.isArray()) {
                List<String> list = new ArrayList<>();
                for (Iterator<JsonNode> it = refs.elements(); it.hasNext(); ) {
                    list.add(it.next().asText());
                }
                vo.setEvidenceRefs(list);
            }
            JsonNode ss = e.get("statSupport") != null ? e.get("statSupport") : e.get("stat_support");
            if (ss != null && ss.isObject()) {
                ForecastRunSnapshotVO.StatSupportVO sv = new ForecastRunSnapshotVO.StatSupportVO();
                sv.setMethod(text(ss, "method", "method"));
                JsonNode pv = ss.get("pValue") != null ? ss.get("pValue") : ss.get("p_value");
                if (pv != null && pv.isNumber()) {
                    sv.setpValue(pv.doubleValue());
                }
                vo.setStatSupport(sv);
            }
            out.add(vo);
        }
        return out;
    }

    private static String text(JsonNode node, String camel, String snake) {
        JsonNode v = node.get(camel);
        if (v == null) {
            v = node.get(snake);
        }
        return v == null || v.isNull() ? null : v.asText();
    }

    private static BigDecimal decimal(JsonNode node, String camel, String snake) {
        JsonNode v = node.get(camel);
        if (v == null) {
            v = node.get(snake);
        }
        if (v == null || v.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** as_of_time：TIMESTAMP → ISO_LOCAL_DATE_TIME 字符串（RunSnapshot.asOfTime 同形）。 */
    private static String iso(Object o) {
        if (o instanceof Timestamp t) {
            return t.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        if (o instanceof LocalDateTime ldt) {
            return ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        return str(o);
    }
}
