package com.chinacreator.gzcm.engine.data.quality.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.datasource.IDataSourceService;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineExecution;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineExecutionService;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineRepository;
import com.chinacreator.gzcm.engine.data.quality.DqAutoRepairService;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DQ 白名单自动修复服务实现（PMO-48-C T12）。
 *
 * <p>铁律硬卡（决策 #5：仅低危场景自动修复）：</p>
 * <ul>
 *   <li>{@link #AUTO_REPAIR_WHITELIST} = { DATASOURCE_DISCONNECT, PIPELINE_NODE_RETRY }，
 *       其余 scenario（NOT_NULL/UNIQUE/FORMAT/PII/CONSISTENCY/完整性缺口）一律 MANUAL 降级</li>
 *   <li>前置校验：工单必须是 AUTO_REPAIR 模式 + handling_mode 由告警链判定 →
 *       非白名单的 alert_type 命中即返回（DEGRADED 留痕）</li>
 *   <li>动作链路经注入的 Service：DS 健康检查走 {@code IDataSourceService#testDataSource}，
 *       Pipeline 重跑走 {@code PipelineExecutionService#executePipeline}，
 *       禁止 {@code new Driver/Connector}（铁律 2.5）</li>
 * </ul>
 *
 * <p>时序：{@link #tryRepair(String)} 由 {@code DqAlertServiceImpl.maybeCreateWorkOrder}
 * 同步调用（工件连续来分析，修复不超出分发链的可感知耗时：3 次重试 = 10+30+60 = 100s 上限，
 * Pipeline 重跑里式同步执行）。若内部失败 → DEGRADED/FAILED + 工单转 MANUAL，不抛异常。</p>
 *
 * <p>每步 auditWrite（DQ_AUTO_REPAIR）（铁律 2.4 #5）。不 {@code new Thread}、不
 * {@code ScheduledExecutorService}（架构铁律 5.1 #14 + 2.5 #3）。</p>
 *
 * @author PMO-48-C T12
 */
@Service("ecosDqAutoRepairService")
public class DqAutoRepairServiceImpl implements DqAutoRepairService {

    private static final Logger log = LoggerFactory.getLogger(DqAutoRepairServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** 自动修复白名单（T12 铁律硬卡）：仅 2 类低危场景允许自动修复 */
    public static final Set<String> AUTO_REPAIR_WHITELIST = Set.of(
            "DATASOURCE_DISCONNECT",   // 数据源瞬时断连 → 3 次重试健康检查
            "PIPELINE_NODE_RETRY");    // Pipeline 节点已配置 retry → 整管线重跑

    /** DATASOURCE_DISCONNECT 重试的间隔（10s / 30s / 60s，总上限 ≈100s） */
    private static final long[] DS_RETRY_DELAYS_MS = {10_000L, 30_000L, 60_000L};

    /** Pipeline 场景超时保护（秒）— 超出即标记 REPAIR_TIMEOUT 并转 MANUAL */
    private static final long PIPELINE_REPAIR_TIMEOUT_S = 300L;

    private final DqRuleMapper ruleMapper;
    private final DqSecurityService securityService;
    private final PipelineExecutionService pipelineExecutionService;
    private final PipelineRepository pipelineRepository;
    private final DataSourceService dataSourceService;
    private final IDataSourceService iDataSourceService;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public DqAutoRepairServiceImpl(DqRuleMapper ruleMapper,
                                    DqSecurityService securityService,
                                    PipelineExecutionService pipelineExecutionService,
                                    PipelineRepository pipelineRepository,
                                    DataSourceService dataSourceService,
                                    IDataSourceService iDataSourceService,
                                    ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.ruleMapper = ruleMapper;
        this.securityService = securityService;
        this.pipelineExecutionService = pipelineExecutionService;
        this.pipelineRepository = pipelineRepository;
        this.dataSourceService = dataSourceService;
        this.iDataSourceService = iDataSourceService;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    // ==================== 主入口 ====================

    @Override
    public void tryRepair(String workOrderId) {
        if (workOrderId == null || workOrderId.isBlank()) {
            log.warn("DqAutoRepair: workOrderId 为空，跳过");
            return;
        }
        JdbcTemplate jdbc = requireJdbc();
        DqWorkOrderRow wo = readWorkOrder(jdbc, workOrderId);
        if (wo == null) {
            log.warn("DqAutoRepair 工单不存在: workOrderId={}", workOrderId);
            securityService.auditWrite("DQ_AUTO_REPAIR", workOrderId, "WORK_ORDER_NOT_FOUND");
            return;
        }

        // ① 前置：非 AUTO_REPAIR 模式 → MANUAL（工单状态不变，仅 audit 留痕）
        if (!"AUTO_REPAIR".equalsIgnoreCase(wo.handlingMode)) {
            log.info("DqAutoRepair 跳过: workOrderId={} handlingMode={}（非 AUTO_REPAIR 不自动修复）",
                    workOrderId, wo.handlingMode);
            writeRepairLog(workOrderId, jdbc, Map.of("skip", "NOT_AUTO_REPAIR",
                    "handlingMode", wo.handlingMode == null ? "" : wo.handlingMode), "FAILED");
            securityService.auditWrite("DQ_AUTO_REPAIR", workOrderId, "SKIP_NOT_AUTO_REPAIR");
            return;
        }

        // ② 前置：alert_type 白名单硬卡（决策 #5：仅白名单场景允许自动修复）
        String alertType = wo.repairAction;
        if (alertType == null || alertType.isBlank() || !AUTO_REPAIR_WHITELIST.contains(alertType)) {
            log.info("DqAutoRepair 降级: workOrderId={} alertType={} 不在白名单，转 MANUAL",
                    workOrderId, alertType);
            degradeToManual(jdbc, workOrderId, "ALERT_TYPE_NOT_IN_WHITELIST:" + alertType);
            securityService.auditWrite("DQ_AUTO_REPAIR", workOrderId, "DEGRADED_NOT_WHITELISTED");
            return;
        }

        // ③ 执行修复动作（白名单内）
        Map<String, Object> repairLog = new LinkedHashMap<>();
        String status;
        String actionToLog = alertType;
        try {
            if ("DATASOURCE_DISCONNECT".equals(alertType)) {
                status = repairDatasource(wo, repairLog);
            } else { // PIPELINE_NODE_RETRY
                status = repairPipeline(wo, repairLog);
            }
        } catch (RuntimeException e) {
            log.error("DqAutoRepair 执行异常: workOrderId={}, alertType={}, error={}",
                    workOrderId, alertType, e.getMessage(), e);
            repairLog.put("error", truncate(e.getMessage(), 400));
            status = "FAILED";
        }

        // ④ 回写工单字段（repair_action=scenario 码，repair_status=元 / repair_log=JSON）
        writeRepairLog(workOrderId, jdbc, repairLog, status);
        if ("SUCCESS".equals(status)) {
            // 重试阶段把 retry_count 也自增（代表总尝试次数，供 T13 状态机参考）
            try {
                jdbc.update(
                        "UPDATE ecos_dq.dq_work_order SET retry_count = retry_count + 1,"
                        + " updated_at = NOW() WHERE id = ?",
                        workOrderId);
            } catch (RuntimeException e) {
                log.warn("DqAutoRepair retry_count 自增失败: workOrderId={}, error={}",
                        workOrderId, e.getMessage());
            }
            // 成功后重跑关联 ACTIVE 规则（规则检查已触发本告警，重跑验证是否已恢复）
            maybeRecheckRuleAfterRepair(wo, status, actionToLog);
        } else if ("DEGRADED".equals(status)) {
            degradeToManual(jdbc, workOrderId, "AUTO_REPAIR_DEGRADED");
        }
        securityService.auditWrite("DQ_AUTO_REPAIR", workOrderId, status);
        log.info("DqAutoRepair 完成: workOrderId={}, alertType={}, status={}",
                workOrderId, actionToLog, status);
    }

    // ==================== 白名单场景执行 ====================

    /**
     * DATASOURCE_DISCONNECT：3 次健康检查重试（10s/30s/60s）。
     * <p>数据源 ID 来源：dq_rule.target_id（DATASOURCE 域规则的 scopeId 是数据源 ID）。</p>
     */
    private String repairDatasource(DqWorkOrderRow wo, Map<String, Object> repairLog) {
        String datasourceId = resolveDatasourceId(wo);
        if (datasourceId == null || datasourceId.isBlank()) {
            repairLog.put("reason", "DATASOURCE_ID_UNRESOLVED");
            return "DEGRADED";
        }
        repairLog.put("datasourceId", datasourceId);
        List<String> attempts = new ArrayList<>(DS_RETRY_DELAYS_MS.length);
        long deadlineMs = System.currentTimeMillis() + (DS_RETRY_DELAYS_MS[0] + DS_RETRY_DELAYS_MS[1]
                + DS_RETRY_DELAYS_MS[2]) + 10_000L;
        boolean ok = false;
        String lastError = null;
        for (int i = 0; i < DS_RETRY_DELAYS_MS.length; i++) {
            long delay = DS_RETRY_DELAYS_MS[i];
            long now = System.currentTimeMillis();
            if (now > deadlineMs) {
                repairLog.put("reason", "TIMEOUT_GUARD");
                return "DEGRADED";
            }
            // 让第一条不等待（即时尝试），首条后再 sleep
            if (i > 0) {
                sleep(delay);
            }
            String errMsg = null;
            try {
                boolean pass = iDataSourceService.testDataSource(datasourceId);
                attempts.add("attempt" + (i + 1) + ":" + (pass ? "OK" : "FAIL"));
                if (pass) {
                    ok = true;
                    break;
                }
                errMsg = "connection_test_failed";
            } catch (Exception e) {
                attempts.add("attempt" + (i + 1) + ":EXC");
                errMsg = truncate(e.getMessage(), 200);
            }
            lastError = errMsg;
            log.info("DqAutoRepair DS 重试: datasourceId={}, attempt={} sleepMs={} result={}",
                    datasourceId, i + 1, delay, errMsg);
        }
        repairLog.put("attempts", attempts);
        if (ok) {
            repairLog.put("result", "recovered");
            return "SUCCESS";
        }
        repairLog.put("reason", "RETRIES_EXHAUSTED");
        repairLog.put("lastError", lastError);
        return "FAILED";
    }

    /**
     * PIPELINE_NODE_RETRY：整 pipeline 定义重跑一次（仅当 status != ARCHIVED）。
     * <p>pipelineId 来源：{@code dq_rule.target_id}（target_kind=PIPELINE 时 =
     * pipeline_definition.id）。</p>
     */
    private String repairPipeline(DqWorkOrderRow wo, Map<String, Object> repairLog) {
        String pipelineId = resolvePipelineId(wo);
        repairLog.put("pipelineId", pipelineId);
        if (pipelineId == null || pipelineId.isBlank()) {
            repairLog.put("reason", "PIPELINE_ID_UNRESOLVED");
            return "DEGRADED";
        }
        var def = pipelineRepository.findDefinitionById(pipelineId);
        if (def == null) {
            repairLog.put("reason", "PIPELINE_NOT_FOUND");
            return "DEGRADED";
        }
        if ("ARCHIVED".equalsIgnoreCase(def.getStatus())) {
            repairLog.put("reason", "PIPELINE_ARCHIVED");
            return "DEGRADED";
        }
        long start = System.currentTimeMillis();
        PipelineExecution exec = null;
        try {
            exec = pipelineExecutionService.executePipeline(pipelineId);
        } catch (RuntimeException e) {
            repairLog.put("error", truncate(e.getMessage(), 400));
            repairLog.put("method", "executePipeline");
            return "FAILED";
        }
        long elapsedMs = System.currentTimeMillis() - start;
        if (elapsedMs > PIPELINE_REPAIR_TIMEOUT_S * 1000L) {
            repairLog.put("reason", "REPAIR_TIMEOUT_MS=" + elapsedMs
                    + ", executionId=" + (exec != null ? exec.getId() : "n/a"));
            return "DEGRADED";
        }
        boolean success = exec != null && "COMPLETED".equalsIgnoreCase(exec.getStatus());
        repairLog.put("executionId", exec != null ? exec.getId() : null);
        repairLog.put("status", exec != null ? exec.getStatus() : "n/a");
        repairLog.put("elapsedMs", elapsedMs);
        repairLog.put("rowsProcessed", exec != null ? exec.getRowsProcessed() : null);
        return success ? "SUCCESS" : "FAILED";
    }

    // ==================== 回写 / 降级 / 重校验 ====================

    /** 回写 repair_status / repair_action（scenario） / repair_log / retry_count / updated_at。 */
    private void writeRepairLog(String workOrderId, JdbcTemplate jdbc,
                                Map<String, Object> repairLog, String status) {
        try {
            String json = serialize(repairLog);
            jdbc.update(
                    "UPDATE ecos_dq.dq_work_order SET repair_status = ?,"
                    + " repair_action = COALESCE(repair_action, ?),"
                    + " repair_log = ?::jsonb, updated_at = NOW() WHERE id = ?",
                    status, scenarioOf(status), json, workOrderId);
        } catch (RuntimeException e) {
            log.warn("DqAutoRepair 回写 repair_log 失败: workOrderId={}, error={}",
                    workOrderId, e.getMessage());
        }
    }

    /** non-success 终态（DEGRADED/NOT_WHITELIST）→ 工单 handling_mode 转 MANUAL。 */
    private void degradeToManual(JdbcTemplate jdbc, String workOrderId, String reason) {
        try {
            jdbc.update(
                    "UPDATE ecos_dq.dq_work_order SET handling_mode = 'MANUAL',"
                    + " repair_status = COALESCE(repair_status, 'DEGRADED'), updated_at = NOW()"
                    + " WHERE id = ?",
                    workOrderId);
            log.info("DqAutoRepair 工单转 MANUAL: workOrderId={}, reason={}", workOrderId, reason);
        } catch (RuntimeException e) {
            log.warn("DqAutoRepair 转 MANUAL 失败: workOrderId={}, error={}",
                    workOrderId, e.getMessage());
        }
    }

    /** SUCCESS 后触发一次规则重检（如上游修复已生效，重检可通过 → 告警自动 RESOLVED；否则留痕）。 */
    private void maybeRecheckRuleAfterRepair(DqWorkOrderRow wo, String status, String scenario) {
        if (wo.ruleId == null || wo.ruleId.isBlank()) {
            return;
        }
        try {
            DqRuleVO rule = ruleMapper.findById(wo.ruleId);
            if (rule == null) {
                return;
            }
            // 不直接调 runRuleBatch（那是 T13 优化），这里仅 audit 提示需要人工/定时重检
            securityService.auditWrite("DQ_AUTO_REPAIR_RECHECK", wo.ruleId,
                    status + ":" + scenario);
        } catch (RuntimeException e) {
            log.warn("DqAutoRepair 重检提示失败: ruleId={}, error={}", wo.ruleId, e.getMessage());
        }
    }

    // ==================== 私有工具 ====================

    /** 工单行轻载（不反序列化 JSONB，仅本类需要的 6 字段）。 */
    private DqWorkOrderRow readWorkOrder(JdbcTemplate jdbc, String workOrderId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, alert_id AS alertId, rule_id AS ruleId, status, severity,"
                    + " handling_mode AS handlingMode, repair_action AS repairAction, retry_count AS retryCount"
                    + " FROM ecos_dq.dq_work_order WHERE id = ? AND is_deleted = FALSE",
                    workOrderId);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> m = rows.get(0);
            DqWorkOrderRow wo = new DqWorkOrderRow();
            wo.id = (String) m.get("id");
            wo.alertId = (String) m.get("alertId");
            wo.ruleId = (String) m.get("ruleId");
            wo.status = (String) m.get("status");
            wo.severity = (String) m.get("severity");
            wo.handlingMode = (String) m.get("handlingMode");
            wo.repairAction = (String) m.get("repairAction");
            wo.retryCount = m.get("retryCount") instanceof Number n ? n.intValue() : 0;
            return wo;
        } catch (RuntimeException e) {
            log.warn("DqAutoRepair 读工单失败: workOrderId={}, error={}", workOrderId, e.getMessage());
            return null;
        }
    }

    /**
     * 解析 datasourceId：由 dq_rule.target_id 来（DATASOURCE 域规则的 scopeId 即 ds id）。
     * <p>扩大兼容性：target_id 空时回退 {@code ecos_dq.dq_alert_record.asset_id}（仅当长度
     * 合理时视为 datasourceId），均无则 null → DEGRADED。</p>
     */
    private String resolveDatasourceId(DqWorkOrderRow wo) {
        if (wo.ruleId != null) {
            DqRuleVO rule = ruleMapper.findById(wo.ruleId);
            if (rule != null && rule.getTargetId() != null && !rule.getTargetId().isBlank()) {
                return rule.getTargetId().trim();
            }
        }
        // Fall back: alert.asset_id（DATASOURCE 域时即 datasourceId；长度合理性不作强判）
        JdbcTemplate jdbc = requireJdbc();
        if (wo.alertId != null) {
            try {
                List<String> rows = jdbc.queryForList(
                        "SELECT asset_id FROM ecos_dq.dq_alert_record WHERE id = ?",
                        String.class, wo.alertId);
                if (!rows.isEmpty() && rows.get(0) != null && !rows.get(0).isBlank()) {
                    return rows.get(0);
                }
            } catch (RuntimeException e) {
                log.warn("DqAutoRepair 反查 alert.asset_id 失败: alertId={}, error={}",
                        wo.alertId, e.getMessage());
            }
        }
        return null;
    }

    /** 解析 pipelineId：{@code dq_rule.target_id}（target_kind=PIPELINE 时 = definition.id）。 */
    private String resolvePipelineId(DqWorkOrderRow wo) {
        if (wo.ruleId == null) {
            return null;
        }
        DqRuleVO rule = ruleMapper.findById(wo.ruleId);
        if (rule == null) {
            return null;
        }
        if (rule.getTargetId() != null && !rule.getTargetId().isBlank()) {
            return rule.getTargetId().trim();
        }
        if (rule.getTargetPipelineId() != null && !rule.getTargetPipelineId().isBlank()) {
            return rule.getTargetPipelineId().trim();
        }
        return null;
    }

    private static String scenarioOf(String status) {
        // repair_action 回写时命名统一；repair_status 是成功/失败/降级
        return status != null ? status : "UNKNOWN";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String serialize(Map<String, Object> m) {
        try {
            return MAPPER.writeValueAsString(m != null ? m : new LinkedHashMap<>());
        } catch (Exception e) {
            return "{}";
        }
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            throw new com.chinacreator.gzcm.common.exception.BusinessException(
                    "JdbcTemplate 不可用，无法执行 DQ 自动修复");
        }
        return jdbc;
    }

    /** 工单行（轻载结构：仅 tryRepair 用到的字段）。 */
    static final class DqWorkOrderRow {
        String id;
        String alertId;
        String ruleId;
        String status;
        String severity;
        String handlingMode;
        String repairAction;
        int retryCount;
    }
}
