package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.fact.FactType;
import com.chinacreator.gzcm.engine.data.fact.dao.ActionOutcomeMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.AttributionFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.CostFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ForecastSnapshotMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ResourceFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.StageFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dq.DqRowGate;
import com.chinacreator.gzcm.engine.data.fact.model.ActionOutcomeRequest;
import com.chinacreator.gzcm.engine.data.fact.model.FactBatchSummary;
import com.chinacreator.gzcm.engine.data.fact.model.FactImportRequest;
import com.chinacreator.gzcm.engine.data.fact.model.FactImportResult;
import com.chinacreator.gzcm.engine.data.fact.model.RowRejection;
import com.chinacreator.gzcm.engine.data.fact.period.PeriodLock;
import com.chinacreator.gzcm.engine.data.model.ActionOutcomeVO;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 业务事实导入/查询/批次/action-outcome 门面 — 详细设计-02 §F02-06 + C.5 控制流。
 *
 * <p>控制流（C.5，本撮范围）：
 * <ol>
 *   <li>整批校验：rows 非空且 ≤100（EN03，超限 400）、DQ-F05 同批单币种（批级不变量，本撮内建 → 整批 400 ECOS-DATA-023）；</li>
 *   <li>关账期预检：任一行期间命中 {@link PeriodLock#isClosed} → 409 ECOS-DATA-022；</li>
 *   <li>列白名单归一（MC02）：外部键 snake/camel 归一到 {@link FactType#columnWhitelist()}，
 *       白名单外 → rejected（ruleId DQ-F07）；系统列（id/dqStatus/… 持久列基准）外部携带 → rejected；</li>
 *   <li>行级 DQ 门禁 {@link DqRowGate}（DQ-F01..F11 由并行 Agent 交付，bean 名 dqRowGate）→ rejected 不入库、进结果；</li>
 *   <li>accepted 行落表 {@code dq_status=PASSED, is_active=1, version_no='1'}；batch_id 应用侧 UUID（MC01）；
 *       create_by/update_by 取 {@link UserContext}（缺省 system）；插入按 100/片（EN03）；</li>
 *   <li>批次覆盖率 = accepted/total &lt; 阈值（默认 0.9）→ 响应附 coverageWarning（WARN 不阻断；
 *       待处理队列 dq_work_order 写入 = F02-07 归属，本撮跳过）；</li>
 *   <li>成功导入/写 outcome 发布审计 → EventBusService topic ecos.audit（ST06；Optional 注入，null 安全 no-op）。</li>
 * </ol>
 *
 * <p>读安全边界：{@link #listFacts} 的 RLS/CLS/mask 决策管道接入留 TODO(F02-01-decide)
 * （batch-3）；本撮<b>不</b>直调 security-engine REST。
 */
@Service
public class FactImportService {

    private static final Logger log = LoggerFactory.getLogger(FactImportService.class);

    static final int MAX_ROWS_PER_BATCH = 100;      // EN03
    static final int INSERT_CHUNK = 100;            // EN03 批量切片
    static final String DQ_STATUS_PASSED = "PASSED";
    static final String DQ_STATUS_PUBLISHED = "PUBLISHED";
    static final String RULE_DQ_F05 = "DQ-F05";
    static final String RULE_DQ_F07 = "DQ-F07";
    static final String AUDIT_TOPIC = "ecos.audit"; // ST06（KafkaTopics.AUDIT 同值，避免 common 依赖环）

    private final AttributionFactMapper attributionMapper;
    private final StageFactMapper stageMapper;
    private final ResourceFactMapper resourceMapper;
    private final CostFactMapper costMapper;
    private final ForecastSnapshotMapper forecastMapper;
    private final DqRowGate dqRowGate;
    private final PeriodLock periodLock;
    private final Optional<EventBusService> eventBus;
    private final double coverageThreshold;

    /**
     * H-3 交付②：outcome 物理载体（V279 ecos_dw.ecos_application_action_outcome）mapper。
     * 可选注入——未装配（V279 未应用 / 手工单测旧接线）时 writeActionOutcome 回退
     * "审计事件 + 触达列"旧形态并 WARN（非静默：降级可见，载体就绪后自动升级为物理落表）。
     */
    private ActionOutcomeMapper actionOutcomeMapper;

    @Autowired(required = false)
    public void setActionOutcomeMapper(ActionOutcomeMapper actionOutcomeMapper) {
        this.actionOutcomeMapper = actionOutcomeMapper;
    }

    @Autowired
    public FactImportService(AttributionFactMapper attributionMapper,
                             StageFactMapper stageMapper,
                             ResourceFactMapper resourceMapper,
                             CostFactMapper costMapper,
                             ForecastSnapshotMapper forecastMapper,
                             DqRowGate dqRowGate,
                             PeriodLock periodLock,
                             @Autowired(required = false) Optional<EventBusService> eventBus,
                             @Value("${ecos.data.facts.coverage-threshold:0.9}") double coverageThreshold) {
        this.attributionMapper = attributionMapper;
        this.stageMapper = stageMapper;
        this.resourceMapper = resourceMapper;
        this.costMapper = costMapper;
        this.forecastMapper = forecastMapper;
        this.dqRowGate = dqRowGate;
        this.periodLock = periodLock;
        this.eventBus = eventBus == null ? Optional.empty() : eventBus;
        this.coverageThreshold = coverageThreshold;
    }

    // ── 导入 ──────────────────────────────────────────────────────

    /**
     * 导入一批事实行（≤100）。行级被拒不进异常通道 — rejected 明细进响应（HTTP 200）；
     * 仅整批级违规（rows 空/超行数 / DQ-F05 币种冲突 / 关账期）抛 {@link DataBridgeException}
     *（400 ECOS-DATA-023 / 409 ECOS-DATA-022）。
     */
    public FactImportResult importFacts(FactType type, FactImportRequest req) {
        if (req == null || req.getRows() == null || req.getRows().isEmpty()) {
            throw badRequest(DataDomainErrorCode.ECOS_DATA_023, "rows 不能为空");
        }
        if (req.getRows().size() > MAX_ROWS_PER_BATCH) {
            throw badRequest(DataDomainErrorCode.ECOS_DATA_023,
                    "单批最多 " + MAX_ROWS_PER_BATCH + " 行（EN03），当前 " + req.getRows().size() + " 行");
        }

        // 批级不变量 DQ-F05（本撮内建 pre-check）
        checkDqF05BatchCurrency(req);

        String batchId = (req.getBatchId() == null || req.getBatchId().isBlank())
                ? UUID.randomUUID().toString() : req.getBatchId().trim();

        List<Map<String, Object>> rawRows = new ArrayList<>(req.getRows());

        // 关账期预检（ECOS-DATA-022）
        checkPeriodsNotClosed(type, rawRows, req.getPeriod());

        // 列白名单归一（MC02 门禁）
        List<Map<String, Object>> normalized = new ArrayList<>(rawRows.size());
        List<RowRejection> rejected = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            Map<String, Object> t = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : rawRows.get(i).entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String key = String.valueOf(e.getKey());
                String col = normalizeKey(key);
                if (SYSTEM_COLUMNS.contains(col)) {
                    rejected.add(new RowRejection(i + 1, key, RULE_DQ_F07,
                            "列 '" + key + "' 为系统列，服务端填充，禁止导入携带",
                            "删除该列后重传"));
                    continue;
                }
                if (!type.columnWhitelist().contains(col)) {
                    rejected.add(new RowRejection(i + 1, key, RULE_DQ_F07,
                            "列 '" + key + "' 不在 " + type.name() + " 列白名单",
                            "列清单见 GET /api/v1/datanet/facts/" + type.name().toLowerCase(Locale.ROOT) + "/template"));
                    continue;
                }
                t.put(col, e.getValue());
            }
            normalized.add(t);
        }

        // 行级 DQ 门禁（DQ-F01..F11 = 并行 Agent 交付的 DqRowGate）
        Map<String, Object> batchCtx = new HashMap<>();
        batchCtx.put("batchId", batchId);
        batchCtx.put("userId", currentActor());
        batchCtx.put("factType", type);
        if (req.getCurrency() != null && !req.getCurrency().isBlank()) {
            batchCtx.put("declaredCurrency", req.getCurrency().trim().toUpperCase(Locale.ROOT));
        }
        if (req.getPeriod() != null && !req.getPeriod().isBlank()) {
            batchCtx.put("targetPeriod", req.getPeriod().trim());
        }
        rejected.addAll(dqRowGate.checkRows(type, normalized, batchCtx));

        Set<Integer> rejectedIdx = new HashSet<>();
        for (RowRejection r : rejected) {
            rejectedIdx.add(r.rowNo() - 1);
        }

        List<Map<String, Object>> accepted = new ArrayList<>();
        for (int i = 0; i < normalized.size(); i++) {
            if (rejectedIdx.contains(i)) {
                continue;
            }
            accepted.add(hydrate(normalized.get(i), batchId));
        }

        for (int i = 0; i < accepted.size(); i += INSERT_CHUNK) {
            mapperInserter(type).apply(accepted.subList(i, Math.min(i + INSERT_CHUNK, accepted.size())));
        }

        FactImportResult result = new FactImportResult();
        result.setBatchId(batchId);
        result.setAccepted(accepted.size());
        result.setRejected(rejected);

        // 批次覆盖率（C.5：缺口 → WARN 不阻断；待处理队列写入归 F02-07）
        double coverage = (double) accepted.size() / rawRows.size();
        if (coverage < coverageThreshold) {
            result.setCoverageWarning(String.format(Locale.ROOT,
                    "本批覆盖率 %.1f%% 低于阈值 %.0f%%（rejected %d/%d），经待处理队列补齐后重传（同 batchId 幂等去重）",
                    coverage * 100.0, coverageThreshold * 100.0, rejectedIdx.size(), rawRows.size()));
        }

        publishAudit(type, result, req);
        return result;
    }

    // ── 查询 / 批次 ───────────────────────────────────────────────

    /**
     * 分页查询事实行（默认 pageSize 20 / 上限 200，C.9）。
     *
     * <p>TODO(F02-01-decide): batch-3 接入分册 01 决策管道（decide → RLS/CLS/mask）；
     * 当前返回行未叠加行级安全谓词，上线前必须闭合本 TODO。
     *
     * @return {@code {total, page, pageSize, items}}
     */
    public Map<String, Object> listFacts(FactType type, long page, long pageSize,
                                         String projectId, String period,
                                         String batchId, String dqStatus) {
        long p = Math.max(1, page);
        long ps = pageSize < 1 ? 20 : Math.min(200, pageSize);
        // FORECAST_SNAPSHOT 无 project_id/period 列 → 忽略两过滤
        String pProject = type == FactType.FORECAST_SNAPSHOT ? null : projectId;
        String pPeriod = type == FactType.FORECAST_SNAPSHOT ? null : period;
        List<Map<String, Object>> rows = mapperPaging(type).apply(
                (int) ((p - 1) * ps), (int) ps, pProject, pPeriod, batchId, dqStatus);
        int total = mapperCount(type).apply(pProject, pPeriod, batchId, dqStatus);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("page", p);
        out.put("pageSize", ps);
        out.put("items", rows);
        return out;
    }

    /** 批次摘要：每表 total + byDqStatus 计数（getFactBatch，D.2）。 */
    public FactBatchSummary getBatch(String batchId) {
        FactBatchSummary s = new FactBatchSummary();
        s.setBatchId(batchId);
        long total = 0;
        for (FactType t : FactType.values()) {
            int count = mapperCountBatch(t).apply(batchId);
            if (count == 0) {
                continue;
            }
            total += count;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("factType", t.name());
            entry.put("table", t.tableName());
            entry.put("total", count);
            Map<String, Object> byStatus = new LinkedHashMap<>();
            for (String st : List.of("PENDING", DQ_STATUS_PASSED, DQ_STATUS_PUBLISHED, "REJECTED")) {
                int n = mapperCountBatchStatus(t).apply(batchId, st);
                if (n > 0) {
                    byStatus.put(st, n);
                }
            }
            entry.put("byDqStatus", byStatus);
            s.getByFactType().add(entry);
        }
        s.setTotalRows(total);
        return s;
    }

    // ── action-outcome（W→D 反馈链）───────────────────────────────

    /**
     * W→D 反馈链写入（writeActionOutcome，D.2）。
     *
     * <p><b>载体决策记录（卷09 v1.2 §十一 H-3 交付②："加列 vs 加表" → 加表）</b>：
     * <ol>
     *   <li>加列否决——outcome 若落 V167 五事实表列，回灌必须 UPDATE 行；PUBLISHED 行禁
     *       UPDATE（ECOS-DATA-021 守卫已实装），加列即造出必须绕过不可变守卫的写路径，自相矛盾；</li>
     *   <li>基数否决——一个 action/事实行可多次回灌（滚动复核），1:N 只能靠追加行承载
     *       （append-only，IR03 只增友好）；</li>
     *   <li>域归属——V218 public.ecos_fc_action_ext 是控制域<b>决策侧</b>扩展（expected_impact=
     *       计划值），非事实回灌载体；任务裁定业务域 ecos_dw（ST07），写路径只归 data-engine
     *       唯一写通道（ADR-14/ST08，BusinessDomainWriteArchTest 把守 ecos_dw. 字面量）。</li>
     * </ol>
     * 裁定落 {@code ecos_dw.ecos_application_action_outcome}（V279；ST03-A 金额列
     * expected_impact/actual_impact NUMERIC(18,2) 已逐列登记）。旧载体形态（触达列 +
     * ecos.audit 事件）保留不回改（只增不改）；V279 未应用环境自动回退旧形态并 WARN。
     *
     * <p>幂等：携带 idempotencyKey 的重复提交命中 uniq_eaao_idem 首写行 → 直接返回
     * {@code outcomeId + alreadyRecorded=true}，零触达、零审计、零第二行（V279 唯一索引双保险）。
     *
     * @throws DataBridgeException 409 ECOS-DATA-021（PUBLISHED 行不可触达，guardPublished）
     *                             / 400（入参不合法 / 金额越 NUMERIC(18,2) 精度域）/ 404（行不存在）
     */
    public Map<String, Object> writeActionOutcome(ActionOutcomeRequest req) {
        if (req == null || req.getFactId() == null || req.getFactId().isBlank()
                || req.getOutcome() == null || req.getOutcome().isBlank()) {
            throw badRequest("ECOS-DATA-000", "factId 与 outcome 必填");
        }
        FactType type = FactType.fromPath(req.getFactType());
        String idemKey = trimToNull(req.getIdempotencyKey());
        if (idemKey != null && actionOutcomeMapper != null) {
            Map<String, Object> first = actionOutcomeMapper.selectByIdempotencyKey(idemKey);
            if (first != null) {
                Map<String, Object> replay = new LinkedHashMap<>();
                replay.put("factId", req.getFactId());
                replay.put("factType", type.name());
                replay.put("outcome", req.getOutcome());
                replay.put("outcomeId", str(first.get("id")));
                replay.put("alreadyRecorded", true);
                return replay;
            }
        }
        checkOutcomeImpactAmounts(req);

        String status = mapperGetDqStatus(type).apply(req.getFactId());
        guardPublished(type, req.getFactId(), status);
        int touched = mapperTouch(type).apply(req.getFactId(), currentActor());
        if (touched == 0) {
            throw new DataBridgeException(404, 404,
                    "事实行不存在: " + req.getFactId(), null);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "FACT_ACTION_OUTCOME");
        payload.put("factType", type.name());
        payload.put("factId", req.getFactId());
        payload.put("outcome", req.getOutcome());
        payload.put("comment", req.getComment());
        payload.put("batchId", req.getBatchId());
        payload.put("actor", currentActor());
        // H-3 增块（只增不改：旧键全保留）
        payload.put("actionId", trimToNull(req.getActionId()));
        payload.put("forecastRunId", trimToNull(req.getForecastRunId()));
        payload.put("period", trimToNull(req.getPeriod()));
        payload.put("expectedImpact", req.getExpectedImpact());
        payload.put("actualImpact", req.getActualImpact());
        publish(AUDIT_TOPIC, payload);

        String outcomeId = null;
        if (actionOutcomeMapper != null) {
            outcomeId = writeOutcomeCarrier(type, req, idemKey);
        } else {
            log.warn("[ActionOutcome] ecos_dw.ecos_application_action_outcome 写通道未装配"
                    + "（V279 未应用？）——本次回灌仅落旧载体形态（审计事件+触达列），无物理 outcome 行");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("factId", req.getFactId());
        out.put("factType", type.name());
        out.put("outcome", req.getOutcome());
        out.put("alreadyRecorded", false);
        if (outcomeId != null) {
            out.put("outcomeId", outcomeId);
        }
        return out;
    }

    /** 物理载体落行（V279，data-engine 唯一写通道；append-only 零 UPDATE 语句）。 */
    private String writeOutcomeCarrier(FactType type, ActionOutcomeRequest req, String idemKey) {
        String actor = currentActor();
        Map<String, Object> row = new LinkedHashMap<>();
        String id = UUID.randomUUID().toString();                                  // MC01 应用侧 UUID
        row.put("id", id);
        row.put("forecastRunId", trimToNull(req.getForecastRunId()));
        row.put("actionId", trimToNull(req.getActionId()));
        row.put("factType", type.name());
        row.put("factId", req.getFactId());
        row.put("period", trimToNull(req.getPeriod()));
        row.put("outcome", req.getOutcome());
        row.put("outcomeComment", trimToNull(req.getComment()));
        row.put("expectedImpact", req.getExpectedImpact());
        row.put("actualImpact", req.getActualImpact());
        row.put("currency", req.getCurrency() == null || req.getCurrency().isBlank()
                ? "CNY" : req.getCurrency().trim().toUpperCase(Locale.ROOT));
        row.put("batchId", trimToNull(req.getBatchId()));
        row.put("idempotencyKey", idemKey);
        row.put("detailJson", trimToNull(req.getDetailJson()));
        row.put("domain", "default");                                              // DR08
        row.put("versionNo", "1");                                                 // DR07
        row.put("isDeleted", 0);                                                   // DR06
        row.put("createBy", actor);
        row.put("updateBy", actor);
        actionOutcomeMapper.insertRow(row);
        return id;
    }

    /**
     * outcome 回灌行查询（H-3 交付②读端点）。
     *
     * <p>定位键二选一（forecast_run_id / action_id 均走 V279 索引），<b>禁全库扫描</b>：
     * 两键皆空 → 400 ECOS-DATA-023；同时给出 → 按 forecastRunId 索引查询后内存收敛 actionId。
     */
    public List<ActionOutcomeVO> listActionOutcomes(String forecastRunId, String actionId) {
        String run = trimToNull(forecastRunId);
        String act = trimToNull(actionId);
        if (run == null && act == null) {
            throw badRequest(DataDomainErrorCode.ECOS_DATA_023,
                    "forecastRunId 与 actionId 至少给一（禁全库扫描 outcome 载体）");
        }
        if (actionOutcomeMapper == null) {
            throw new DataBridgeException(503, 503, DataDomainErrorCode.ECOS_DATA_041
                    + "：outcome 物理载体（V279 ecos_dw.ecos_application_action_outcome）未就绪——"
                    + "查询端点需 V279 应用后启用", null);
        }
        List<Map<String, Object>> rows = run != null
                ? actionOutcomeMapper.selectByForecastRunId(run)
                : actionOutcomeMapper.selectByActionId(act);
        List<ActionOutcomeVO> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (run != null && act != null && !act.equals(str(row.get("action_id")))) {
                continue;   // 双键过滤（内存收敛，不再二次查库）
            }
            out.add(toOutcomeVO(row));
        }
        return out;
    }

    private static ActionOutcomeVO toOutcomeVO(Map<String, Object> row) {
        ActionOutcomeVO vo = new ActionOutcomeVO();
        vo.setOutcomeId(str(row.get("id")));
        vo.setForecastRunId(str(row.get("forecast_run_id")));
        vo.setActionId(str(row.get("action_id")));
        vo.setFactType(str(row.get("fact_type")));
        vo.setFactId(str(row.get("fact_id")));
        vo.setPeriod(str(row.get("period")));
        vo.setOutcome(str(row.get("outcome")));
        vo.setComment(str(row.get("outcome_comment")));
        vo.setExpectedImpact(toDecimal(row.get("expected_impact")));
        vo.setActualImpact(toDecimal(row.get("actual_impact")));
        vo.setCurrency(str(row.get("currency")));
        vo.setBatchId(str(row.get("batch_id")));
        vo.setIdempotencyKey(str(row.get("idempotency_key")));
        vo.setCreateBy(str(row.get("create_by")));
        Object ct = row.get("create_time");
        vo.setCreateTime(ct instanceof Timestamp t
                ? t.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : str(ct));
        return vo;
    }

    /**
     * 金额入参显式校验（后端规范：精度问题显式拒绝，不默认四舍五入）。
     * NUMERIC(18,2)：整数位 ≤16 位、scale ≤2；违 → 400 ECOS-DATA-023。
     */
    static void checkOutcomeImpactAmounts(ActionOutcomeRequest req) {
        checkNumeric182(req.getExpectedImpact(), "expectedImpact");
        checkNumeric182(req.getActualImpact(), "actualImpact");
    }

    private static void checkNumeric182(BigDecimal v, String field) {
        if (v == null) {
            return;
        }
        if (v.stripTrailingZeros().scale() > 2) {
            throw badRequest(DataDomainErrorCode.ECOS_DATA_023,
                    field + " 小数位超 NUMERIC(18,2) 精度域（>2 位小数显式拒绝，禁静默舍入）: " + v.toPlainString());
        }
        if (v.abs().compareTo(new BigDecimal("10000000000000000")) >= 0) {
            throw badRequest(DataDomainErrorCode.ECOS_DATA_023,
                    field + " 整数位超 NUMERIC(18,2) 精度域（>16 位）: " + v.toPlainString());
        }
    }

    /**
     * PUBLISHED 不可变守卫（F02-06-4 / ECOS-DATA-021）：
     * PUBLISHED 行任何更新路径必须先过本守卫 → 409。
     */
    public void guardPublished(FactType type, String factId, String dqStatus) {
        if (DQ_STATUS_PUBLISHED.equals(dqStatus)) {
            throw new DataBridgeException(409, 409,
                    DataDomainErrorCode.ECOS_DATA_021
                            + "：PUBLISHED 事实行不可 UPDATE（更正=新行 + 旧行 is_active 置 0，保留审计链）",
                    null);
        }
    }

    // ── 内部 ──────────────────────────────────────────────────────

    /** DQ-F05：批次声明币种与行内 currency 必须唯一一致（批级不变量 → 400 整批拒）。 */
    void checkDqF05BatchCurrency(FactImportRequest req) {
        Set<String> currencies = new HashSet<>();
        if (req.getCurrency() != null && !req.getCurrency().isBlank()) {
            currencies.add(req.getCurrency().trim().toUpperCase(Locale.ROOT));
        }
        for (Map<String, Object> row : req.getRows()) {
            Object c = row.get("currency");
            if (c != null && !String.valueOf(c).isBlank()) {
                currencies.add(String.valueOf(c).trim().toUpperCase(Locale.ROOT));
            }
        }
        if (currencies.size() > 1) {
            throw badRequest(DataDomainErrorCode.ECOS_DATA_023,
                    RULE_DQ_F05 + "：同批次币种必须唯一 — 检测到 " + currencies
                            + "；请拆批或统一 currency 后重传");
        }
    }

    /** 关账期预检（F02-06-5）：任一行期间已关账 → 409 ECOS-DATA-022。 */
    void checkPeriodsNotClosed(FactType type, List<Map<String, Object>> rows, String batchPeriod) {
        for (Map<String, Object> row : rows) {
            String p = type == FactType.ATTRIBUTION
                    ? str(row.get("effectiveFrom")) : str(row.get("period"));
            if (p != null && periodLock.isClosed(p)) {
                throw period409(p);
            }
        }
        if (batchPeriod != null && !batchPeriod.isBlank() && periodLock.isClosed(batchPeriod.trim())) {
            throw period409(batchPeriod.trim());
        }
    }

    static DataBridgeException period409(String period) {
        return new DataBridgeException(409, 409,
                DataDomainErrorCode.ECOS_DATA_022 + "：期间 " + period
                        + " 已关账，拒绝写入（联动 09 册 FC-05 period lock）", null);
    }

    /** 落库前系统列填充（C.5：accepted → PASSED / is_active=1 / version_no='1' / 审计列）。 */
    static Map<String, Object> hydrate(Map<String, Object> row, String batchId) {
        Map<String, Object> t = new LinkedHashMap<>(row);
        t.putIfAbsent("id", UUID.randomUUID().toString());   // MC01 应用侧 UUID
        t.put("dqStatus", DQ_STATUS_PASSED);
        t.put("isActive", 1);                                 // F02-06-3 活跃唯一方案
        t.put("batchId", batchId);
        t.putIfAbsent("domain", "default");                   // DR08
        t.put("versionNo", "1");                              // DR07
        t.put("isDeleted", 0);                                // DR06
        // V167 DDL NOT NULL 默认列预填（Service 侧同口径，避免 INSERT 全值传 null）
        t.putIfAbsent("attributionType", "PRIMARY");
        t.putIfAbsent("sourceType", "IMPORT");
        t.putIfAbsent("staffHashAlgo", "SHA-256");
        t.putIfAbsent("isPool", 0);
        t.putIfAbsent("costCategory", "SALARY");
        t.putIfAbsent("currency", "CNY");
        String actor = currentActor();
        t.put("createBy", actor);
        t.put("updateBy", actor);
        return t;
    }

    /** UserContext 当前操作者；无上下文（如测试）→ "system"。 */
    static String currentActor() {
        String u = UserContext.getCurrentUserId();
        if (u == null) {
            u = UserContext.getCurrentUsername();
        }
        return (u == null || u.isBlank()) ? "system" : u;
    }

    /** 外部键归一（snake→camel）。 */
    static String normalizeKey(String key) {
        String k = key.trim();
        if (!k.contains("_")) {
            return k;
        }
        StringBuilder sb = new StringBuilder(k.length());
        boolean up = false;
        for (char c : k.toCharArray()) {
            if (c == '_') {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return sb.toString();
    }

    private void publish(String topic, Object payload) {
        eventBus.ifPresent(bus -> {
            try {
                bus.publish(topic, payload);
            } catch (Exception e) {
                // C.8 降级矩阵：Kafka 不可用不阻断业务（WARN，SLA 归 F02-08 重试队列）
                log.warn("[Fact] event bus publish failed topic={}: {}", topic, e.getMessage(), e);
            }
        });
    }

    private void publishAudit(FactType type, FactImportResult result, FactImportRequest req) {
        if (result.getAccepted() == 0) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "FACT_IMPORT");
        payload.put("factType", type.name());
        payload.put("batchId", result.getBatchId());
        payload.put("accepted", result.getAccepted());
        payload.put("rejectedCount", result.getRejected().size());
        payload.put("currency", req.getCurrency());
        payload.put("period", req.getPeriod());
        payload.put("actor", currentActor());
        publish(AUDIT_TOPIC, payload);
    }

    private static DataBridgeException badRequest(String code, String msg) {
        return new DataBridgeException(400, 400, code + "：" + msg, null);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** DB NUMERIC 回读形态兼容（PG JDBC 常给 BigDecimal；单测/兼容驱动可能给其它 Number/String）。 */
    private static BigDecimal toDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 系统列（V167 持久列基准）：服务端填充，导入不可携带。 */
    private static final Set<String> SYSTEM_COLUMNS = Set.of(
            "id", "dqStatus", "dq_status",
            "isActive", "is_active",
            "batchId", "batch_id",
            "domain", "versionNo", "version_no",
            "isDeleted", "is_deleted",
            "createTime", "create_time", "updateTime", "update_time",
            "createBy", "create_by", "updateBy", "update_by");

    // ── mapper 路由 ───────────────────────────────────────────────

    @FunctionalInterface
    interface IInt1 { int apply(String a); }

    @FunctionalInterface
    interface IInt2 { int apply(String a, String b); }

    @FunctionalInterface
    interface IInt4 { int apply(String a, String b, String c, String d); }

    @FunctionalInterface
    interface IRowPaging {
        List<Map<String, Object>> apply(int offset, int limit, String projectId,
                                        String period, String batchId, String dqStatus);
    }

    private Function<List<Map<String, Object>>, Integer> mapperInserter(FactType type) {
        return switch (type) {
            case ATTRIBUTION -> attributionMapper::insertBatch;
            case STAGE -> stageMapper::insertBatch;
            case RESOURCE -> resourceMapper::insertBatch;
            case COST -> costMapper::insertBatch;
            case FORECAST_SNAPSHOT -> forecastMapper::insertBatch;
        };
    }

    private IRowPaging mapperPaging(FactType type) {
        return switch (type) {
            case ATTRIBUTION -> (o, l, p, pp, b, d) -> attributionMapper.pageQuery(o, l, p, pp, b, d);
            case STAGE -> (o, l, p, pp, b, d) -> stageMapper.pageQuery(o, l, p, pp, b, d);
            case RESOURCE -> (o, l, p, pp, b, d) -> resourceMapper.pageQuery(o, l, p, pp, b, d);
            case COST -> (o, l, p, pp, b, d) -> costMapper.pageQuery(o, l, p, pp, b, d);
            case FORECAST_SNAPSHOT -> (o, l, p, pp, b, d) -> forecastMapper.pageQuery(o, l, p, pp, b, d);
        };
    }

    private IInt4 mapperCount(FactType type) {
        return switch (type) {
            case ATTRIBUTION -> attributionMapper::countQuery;
            case STAGE -> stageMapper::countQuery;
            case RESOURCE -> resourceMapper::countQuery;
            case COST -> costMapper::countQuery;
            case FORECAST_SNAPSHOT -> forecastMapper::countQuery;
        };
    }

    private IInt1 mapperCountBatch(FactType type) {
        return switch (type) {
            case ATTRIBUTION -> attributionMapper::countBatch;
            case STAGE -> stageMapper::countBatch;
            case RESOURCE -> resourceMapper::countBatch;
            case COST -> costMapper::countBatch;
            case FORECAST_SNAPSHOT -> forecastMapper::countBatch;
        };
    }

    private IInt2 mapperCountBatchStatus(FactType type) {
        return switch (type) {
            case ATTRIBUTION -> attributionMapper::countBatchByStatus;
            case STAGE -> stageMapper::countBatchByStatus;
            case RESOURCE -> resourceMapper::countBatchByStatus;
            case COST -> costMapper::countBatchByStatus;
            case FORECAST_SNAPSHOT -> forecastMapper::countBatchByStatus;
        };
    }

    private Function<String, String> mapperGetDqStatus(FactType type) {
        return switch (type) {
            case ATTRIBUTION -> attributionMapper::getDqStatusById;
            case STAGE -> stageMapper::getDqStatusById;
            case RESOURCE -> resourceMapper::getDqStatusById;
            case COST -> costMapper::getDqStatusById;
            case FORECAST_SNAPSHOT -> forecastMapper::getDqStatusById;
        };
    }

    private BiFunction<String, String, Integer> mapperTouch(FactType type) {
        return switch (type) {
            case ATTRIBUTION -> attributionMapper::touchUpdate;
            case STAGE -> stageMapper::touchUpdate;
            case RESOURCE -> resourceMapper::touchUpdate;
            case COST -> costMapper::touchUpdate;
            case FORECAST_SNAPSHOT -> forecastMapper::touchUpdate;
        };
    }
}
