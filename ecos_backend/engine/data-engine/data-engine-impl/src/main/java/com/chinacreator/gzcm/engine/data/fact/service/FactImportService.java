package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.fact.FactType;
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
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
     * <p><b>载体缺口（V167 实证）</b>：五表无 action/outcome 列。本撮实现 =
     * 更新 {@code update_time/update_by} + 审计事件携带 outcome 载荷（fabricating 新表违反
     * IR03 与 V167 unapplied 约束）。缺口已在交付报告上报，待决策。
     *
     * @throws DataBridgeException 409 ECOS-DATA-021（PUBLISHED 行不可触达，guardPublished）
     *                             / 400（入参不合法）/ 404（行不存在）
     */
    public Map<String, Object> writeActionOutcome(ActionOutcomeRequest req) {
        if (req == null || req.getFactId() == null || req.getFactId().isBlank()
                || req.getOutcome() == null || req.getOutcome().isBlank()) {
            throw badRequest("ECOS-DATA-000", "factId 与 outcome 必填");
        }
        FactType type = FactType.fromPath(req.getFactType());
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
        publish(AUDIT_TOPIC, payload);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("factId", req.getFactId());
        out.put("factType", type.name());
        out.put("outcome", req.getOutcome());
        return out;
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
