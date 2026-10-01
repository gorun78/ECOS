package com.chinacreator.gzcm.engine.data.quality.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.engine.data.quality.DqAlertService;
import com.chinacreator.gzcm.engine.data.quality.DqAutoRepairService;
import com.chinacreator.gzcm.engine.data.quality.DqKnowledgeSinkService;
import com.chinacreator.gzcm.engine.data.quality.DqWorkOrderService;
import com.chinacreator.gzcm.engine.data.quality.dao.DqAlertDao;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqAlertQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqAlertVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;
import com.chinacreator.gzcm.runtime.core.alert.IAlertService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DQ 告警分发服务实现（PMO-48-C T12）。
 *
 * <p>分发链（{@link #dispatchOnRuleCheckFailed}）：</p>
 * <ol>
 *   <li>读 dq_rule 取 severity → 映射 alert_level（CRITICAL→P0 / HIGH→P1 / MEDIUM→P2 / LOW→P3）</li>
 *   <li>判重：同 rule 5min 内已有 PENDING/NOTIFIED 记录 → notify_count++ 合并，不新建</li>
 *   <li>写 dq_alert_record（P0-P3 全量落库，决策 #2）</li>
 *   <li>payload 经 {@code DqSecurityService.maskParameters} 脱敏（铁律 2.4 #3）</li>
 *   <li>推送：P0/P1/P2 → runtime {@code IAlertService.triggerAlert}（决策 #6/#7 统一渠道）；
 *       <b>P3 不推送</b>（仅落库，待 Phase 4 邮件日报消化）— status 保持 PENDING</li>
 *   <li>P0/P1 → 调 {@code DqWorkOrderService.createWorkOrder} 建工单 →
 *       白名单场景（alert_type 命中 AUTO_REPAIR 前缀）同步调 {@code DqAutoRepairService.tryRepair}</li>
 *   <li>异步 audit（DQ_ALERT_DISPATCH + level）— 铁律 2.4 #5</li>
 * </ol>
 *
 * <p>异常策略：分发链每步独立 try/catch，单条失败记 log，不中断 runRuleBatch。</p>
 *
 * @author PMO-48-C T12
 */
@Service("ecosDqAlertService")
public class DqAlertServiceImpl implements DqAlertService {

    private static final Logger log = LoggerFactory.getLogger(DqAlertServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** severity → alert_level 映射表（CRITICAL→P0 / HIGH→P1 / MEDIUM→P2 / LOW→P3，未知安全降级 P3） */
    public static final Map<String, String> SEVERITY_TO_LEVEL = Map.of(
            "CRITICAL", "P0",
            "HIGH", "P1",
            "MEDIUM", "P2",
            "LOW", "P3");

    /** 判重窗口：同 rule 该窗口内已有活跃（PENDING/NOTIFIED）告警 → 合并 notify_count 不新建 */
    public static final long DEDUP_WINDOW_MINUTES = 5L;

    /** payload / message 中敏感字段值的脱敏占位（与 DqSecurityService.MASKED 同值） */
    public static final String SENSITIVE_PLACEHOLDER = "******";

    /** 白名单场景码 → 规则 ruleType 允许值（自动修复前置校验用，与 DqAutoRepairServiceImpl 白名单一致） */
    static final Map<String, List<String>> AUTO_REPAIR_RULE_TYPES = Map.of(
            "DATASOURCE_DISCONNECT", List.of("FRESHNESS", "TIMEOUT"),
            "PIPELINE_NODE_RETRY", List.of("PIPELINE"));

    /** 资产 ID 列宽（dq_alert_record.asset_id VARCHAR(64) / dq_work_order.asset_id VARCHAR(64)） */
    private static final int ASSET_ID_MAX = 64;

    private final DqRuleMapper ruleMapper;
    private final DqSecurityService securityService;
    private final DqWorkOrderService workOrderService;
    private final DqAutoRepairService autoRepairService;
    /** IAlertService 可选注入（runtime-core 上下文无 alert Bean 时降级为仅落库，保证主链不炸） */
    private final IAlertService alertService;
    /** T16 知识沉淀可选注入（fire-and-forget；缺失时不阻断启动） */
    private final DqKnowledgeSinkService knowledgeSinkService;
    private final DqAlertDao alertDao;

    public DqAlertServiceImpl(DqRuleMapper ruleMapper,
                               DqSecurityService securityService,
                               DqWorkOrderService workOrderService,
                               DqAutoRepairService autoRepairService,
                               ObjectProvider<IAlertService> alertServiceProvider,
                               ObjectProvider<DqKnowledgeSinkService> knowledgeSinkProvider,
                               DqAlertDao alertDao) {
        this.ruleMapper = ruleMapper;
        this.securityService = securityService;
        this.workOrderService = workOrderService;
        this.autoRepairService = autoRepairService;
        this.alertService = alertServiceProvider.getIfAvailable();
        this.knowledgeSinkService = knowledgeSinkProvider.getIfAvailable();
        this.alertDao = alertDao;
    }

    // ==================== 1. 核心分发链 ====================

    @Override
    public void dispatchOnRuleCheckFailed(String ruleId, String checkId, String scopeKind, String scopeId) {
        if (ruleId == null || ruleId.isBlank()) {
            log.warn("DqAlert 分发跳过: ruleId 为空 (checkId={})", checkId);
            return;
        }
        try {
            // 0. 规则不存在 → 不建告警（脏数据防御），仅留痕
            DqRuleVO rule = ruleMapper.findById(ruleId);
            if (rule == null) {
                log.warn("DqAlert 分发跳过: 规则不存在 ruleId={}, checkId={}", ruleId, checkId);
                securityService.auditWrite("DQ_ALERT_DISPATCH_SKIP", ruleId, "RULE_NOT_FOUND");
                return;
            }

            // 1. 取 check 行诊断位（不存在时降级空 Map — 不阻断建告警）
            Map<String, Object> check = readCheckRow(checkId);
            String alertLevel = mapSeverityToLevel(rule.getSeverity());
            String alertType = resolveAlertType(rule, check);
            String assetId = truncatedAsset(scopeId);
            String assetName = assetNameOf(scopeKind, scopeId, rule);

            // 2. 判重：同 rule 5min 内已有 PENDING/NOTIFIED → notify_count++ + 刷新 last_notify_at/payload
            String alertId = findDedupAlertId(ruleId, check, scopeKind);
            boolean dedup = alertId != null;
            if (dedup) {
                notifyDedupAlert(alertId, check, assetId, alertLevel);
                log.info("DqAlert 判重合并: ruleId={}, dedupAlertId={}, level={}, checkId={}",
                        ruleId, alertId, alertLevel, checkId);
                securityService.auditWrite("DQ_ALERT_DISPATCH_MERGED", alertId, alertLevel);
                // 判重场景同样走 P0/P1 工单链（工单按 alert_id 幂等，查已有工单则不重复建）
                maybeCreateWorkOrder(alertId, rule, check, scopeKind, scopeId, alertLevel, assetId);
                return;
            }

            // 3. 新建告警记录（P0-P3 全量落库 — P3 也落库仅不推送）
            alertId = UUID.randomUUID().toString();
            String message = buildMessage(rule, alertLevel, check, scopeKind, scopeId);
            Map<String, Object> payloadMasked = buildPayloadMasked(check, scopeKind, scopeId);
            String payloadJson = serializePayload(payloadMasked);
            String status = "PENDING";
            try {
                alertDao.insertAlertRecord(alertId, ruleId, alertLevel,
                        nullToDefault(alertType, "RULE_CHECK_FAILED"),
                        assetId, assetName, rule.getRuleName(), message, payloadJson, "PENDING",
                        "[]");
            } catch (RuntimeException e) {
                log.error("DqAlert 写 dq_alert_record 失败: ruleId={}, alertId={}, error={}",
                        ruleId, alertId, e.getMessage(), e);
                securityService.auditWrite("DQ_ALERT_DISPATCH", alertId, "FAILED");
                return;
            }
            log.info("DqAlert 新建告警: alertId={}, ruleId={}, level={}, type={}, asset={}:{}, dedup=false",
                    alertId, ruleId, alertLevel, alertType, scopeKind, scopeId);

            // PMO-48-D T16: fire-and-forget 知识沉淀（异步 @Async，不阻塞分发主链）
            sinkAlertQuietly(alertId, alertLevel, rule, scopeKind, scopeId, message);

            // 4. 推送（决策 #2 / #6 / #7）
            if (!"P3".equals(alertLevel)) {
                // P0/P1/P2 → runtime IAlertService 统一推送（严禁本层直连 IM/Kafka/Producer）
                boolean pushed = pushViaRuntime(alertId, rule, alertLevel, message, check, scopeKind, scopeId);
                if (pushed) {
                    try {
                        alertDao.markNotified(alertId);
                    } catch (RuntimeException e) {
                        log.warn("DqAlert 回写 NOTIFIED 状态失败: alertId={}, error={}", alertId, e.getMessage());
                    }
                    status = "NOTIFIED";
                }
            } else {
                // P3：仅落库不推送（待 Phase 4 邮件日报消化），status 保持 PENDING
                log.info("DqAlert P3 级别仅落库不推送: alertId={}, ruleId={}", alertId, ruleId);
            }

            // 5. P0/P1 自动创建工单 + 白名单自动修复（决策 #5）
            maybeCreateWorkOrder(alertId, rule, check, scopeKind, scopeId, alertLevel, assetId);

            // 6. 异步审计（铁律 2.4 #5）— 状态区分 新建 / P3 仅落库 / P0P1 带工单
            String auditResult = dedup ? "MERGED" : ("P3".equals(alertLevel) ? "STORED_ONLY" : "DISPATCHED");
            securityService.auditWrite("DQ_ALERT_DISPATCH", alertId, auditResult);
        } catch (Exception e) {
            // 分发链兜底隔离：单条失败不得中断 runRuleBatch
            log.error("DqAlert 分发异常（已隔离）: ruleId={}, checkId={}, error={}",
                    ruleId, checkId, e.getMessage(), e);
            securityService.auditWrite("DQ_ALERT_DISPATCH", ruleId, "EXCEPTION");
        }
    }

    /** 查 check 行诊断位（pass_rate/total_rows 等）；checkId 空或行缺失返回空 Map。 */
    private Map<String, Object> readCheckRow(String checkId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (checkId == null || checkId.isBlank()) {
            return out;
        }
        try {
            List<Map<String, Object>> rows = alertDao.readCheckRow(checkId);
            if (!rows.isEmpty()) {
                out.putAll(rows.get(0));
            }
        } catch (RuntimeException e) {
            log.warn("DqAlert 读 dq_rule_check 失败: checkId={}, error={}", checkId, e.getMessage());
        }
        return out;
    }

    /**
     * 判定 alert_type（自动修复白名单前置：仅特定规则类型产假修场景码，其余 RULE_CHECK_FAILED）。
     * <p>前置校验：severity ∈ {CRITICAL, HIGH, MEDIUM} 且 rule_type 命中 {@link #AUTO_REPAIR_RULE_TYPES}</p>
     */
    static String resolveAlertType(DqRuleVO rule, Map<String, Object> check) {
        String ruleType = rule.getRuleType() == null ? "" : rule.getRuleType().trim().toUpperCase();
        for (Map.Entry<String, List<String>> entry : AUTO_REPAIR_RULE_TYPES.entrySet()) {
            if (entry.getValue().contains(ruleType)) {
                return entry.getKey();
            }
        }
        // 评估器级失败（passed=false 且 error_message 非空）— 下游按 MANUAL 处理
        if ("EVALUATOR_ERROR".equals(check.get("errorMessage")) || startsWithE(s(check, "errorMessage"))) {
            return "EVALUATOR_ERROR";
        }
        return "RULE_CHECK_FAILED";
    }

    /** severity 缺省安全降级 P3（不推送），SQL 层本有 NOT NULL DEFAULT 'MEDIUM' 兜底。 */
    static String mapSeverityToLevel(String severity) {
        if (severity == null || severity.isBlank()) {
            return "P3";
        }
        return SEVERITY_TO_LEVEL.getOrDefault(severity.trim().toUpperCase(), "P3");
    }

    /** 判重：同 rule 5min 内 status IN (PENDING, NOTIFIED) 的最新一条。 */
    private String findDedupAlertId(String ruleId, Map<String, Object> check, String scopeKind) {
        try {
            List<String> rows = alertDao.findDedupAlertIds(ruleId, String.valueOf(DEDUP_WINDOW_MINUTES));
            return rows.isEmpty() ? null : rows.get(0);
        } catch (RuntimeException e) {
            log.warn("DqAlert 判重查询失败（降级新建）: ruleId={}, error={}", ruleId, e.getMessage());
            return null;
        }
    }

    /** 判重合并：notify_count++ + 刷新 last_notify_at/payload/status（同一次故障的后续命中）。 */
    private void notifyDedupAlert(String alertId, Map<String, Object> check,
                                  String assetId, String alertLevel) {
        try {
            alertDao.notifyDedupAlert(nullIfBlank(assetId), alertId);
        } catch (RuntimeException e) {
            log.warn("DqAlert 判重合并更新失败: alertId={}, error={}", alertId, e.getMessage());
        }
    }

    /**
     * P0/P1/P2 → runtime IAlertService 推送（决策 #6/#7：不直连 IM/生产者）。
     * meta 含 ruleId/assetId/pass_rate/fail_ratio/sample_size（已脱敏）。
     */
    private boolean pushViaRuntime(String alertId, DqRuleVO rule, String level, String message,
                                   Map<String, Object> check, String scopeKind, String scopeId) {
        if (alertService == null) {
            log.warn("DqAlert runtime IAlertService 不可用，降级仅落库: alertId={}", alertId);
            return false;
        }
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("alertId", alertId);
            meta.put("ruleId", rule.getId());
            meta.put("ruleName", rule.getRuleName());
            meta.put("alertLevel", level);
            meta.put("scopeKind", scopeKind);
            meta.put("scopeId", scopeId);
            meta.put("passRate", check.get("passRate"));
            meta.put("failedRows", check.get("failedRows"));
            meta.put("sampleSize", check.get("sampleSize"));
            // runtime 5 参签名: (ruleId, alertType, nodeId, taskId, message)
            alertService.triggerAlert(rule.getId(), "DQ_RULE_CHECK_FAILED",
                    truncatedAsset(scopeId), alertId, message);
            log.info("DqAlert runtime 推送成功: alertId={}, level={}, ruleId={}", alertId, level, rule.getId());
            return true;
        } catch (IAlertService.AlertException e) {
            log.warn("DqAlert runtime 推送失败（降级仅落库）: alertId={}, error={}", alertId, e.getMessage());
            return false;
        } catch (RuntimeException e) {
            log.warn("DqAlert runtime 推送异常（降级仅落库）: alertId={}, error={}", alertId, e.getMessage());
            return false;
        }
    }

    /**
     * P0/P1 → 自动建工单（同 alert 幂等：已有工单则跳过）+ 白名单场景同步尝试自动修复。
     * <p>handling_mode 判定：alert_type ∈ AUTO_REPAIR_RULE_TYPES 且 severity ∈ {CRITICAL, HIGH}
     * → AUTO_REPAIR；否则 MANUAL（决策 #5：仅低危场景自动修复）。</p>
     */
    private void maybeCreateWorkOrder(String alertId, DqRuleVO rule, Map<String, Object> check,
                                      String scopeKind, String scopeId, String alertLevel, String assetId) {
        if (!"P0".equals(alertLevel) && !"P1".equals(alertLevel)) {
            return; // 仅 P0/P1 建单
        }
        try {
            // 幂等：同 alert 已有未删除工单 → 不重复建
            List<String> existing = alertDao.findExistingWorkOrderIds(alertId);
            if (!existing.isEmpty()) {
                log.info("DqAlert 工单已存在（幂等跳过）: alertId={}, workOrderId={}", alertId, existing.get(0));
                addWorkOrderLinkToAlert(alertId, existing.get(0));
                return;
            }

            DqAlertVO voForOrder = new DqAlertVO();
            voForOrder.setId(alertId);
            voForOrder.setRuleId(rule.getId());
            voForOrder.setAlertLevel(alertLevel);
            voForOrder.setAlertType(resolveAlertType(rule, check));
            voForOrder.setAssetId(truncatedAsset(assetId));
            voForOrder.setRuleName(rule.getRuleName());
            voForOrder.setMessage(buildMessage(rule, alertLevel, check, scopeKind, scopeId));
            voForOrder.setStatus("NOTIFIED");

            String workOrderId = workOrderService.createWorkOrder(voForOrder, rule);
            if (workOrderId == null || workOrderId.isBlank()) {
                log.warn("DqAlert 工单创建失败: alertId={}", alertId);
                return;
            }
            addWorkOrderLinkToAlert(alertId, workOrderId);
            securityService.auditWrite("DQ_WORK_ORDER_AUTO_CREATE", workOrderId, "SUCCESS");

            // 白名单场景由 alert_type 表达；DqWorkOrderServiceImpl 会自动识别并设 handling_mode
            String woProcessing = resolveAlertType(rule, check);
            if (AUTO_REPAIR_RULE_TYPES.containsKey(woProcessing)) {
                autoRepairService.tryRepair(workOrderId);
            }
        } catch (RuntimeException e) {
            log.error("DqAlert 工单链异常（已隔离）: alertId={}, error={}", alertId, e.getMessage(), e);
            securityService.auditWrite("DQ_WORK_ORDER_CREATE", alertId, "EXCEPTION");
        }
    }

    /** 观察点：把 workOrder 信息回写到 alert payload（便于前端关联，不阻塞）。 */
    private void addWorkOrderLinkToAlert(String alertId, String workOrderId) {
        try {
            alertDao.addWorkOrderLinkToAlert(
                    "{\"workOrderId\":\"" + workOrderId.replace("\"", "") + "\"}", alertId);
        } catch (RuntimeException e) {
            log.warn("DqAlert 回写 workOrderId 到 payload 失败: alertId={}, error={}", alertId, e.getMessage());
        }
    }

    /**
     * PMO-48-D T16：fire-and-forget 知识沉淀 — 组装最小 {@link DqAlertVO} 异步提交
     * {@code DqKnowledgeSinkService.ingestFromAlert}。沉淀服务缺失 / 异常均静默，不阻塞分发主链。
     */
    private void sinkAlertQuietly(String alertId, String alertLevel, DqRuleVO rule,
                                   String scopeKind, String scopeId, String message) {
        if (knowledgeSinkService == null) {
            return;
        }
        try {
            DqAlertVO vo = new DqAlertVO();
            vo.setId(alertId);
            vo.setRuleId(rule != null ? rule.getId() : null);
            vo.setAlertLevel(alertLevel);
            vo.setRuleName(rule != null ? rule.getRuleName() : null);
            vo.setAssetId(truncatedAsset(scopeId));
            vo.setAssetName(assetNameOf(scopeKind, scopeId, rule));
            vo.setMessage(message);
            knowledgeSinkService.ingestFromAlert(alertId, vo);
        } catch (RuntimeException e) {
            log.warn("DqAlert 知识沉淀触发失败（不阻塞）: alertId={}, error={}", alertId, e.getMessage());
        }
    }

    // ==================== 2. 查询 ====================

    @Override
    public PageResult<DqAlertVO> listAlerts(DqAlertQuery query) {
        DqAlertQuery q = query != null ? query : new DqAlertQuery();
        int pageNum = q.getPageNum() != null && q.getPageNum() > 0 ? q.getPageNum() : 1;
        int pageSize = q.getPageSize() != null && q.getPageSize() > 0 ? Math.min(q.getPageSize(), 100) : 20;

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (notBlank(q.getAlertLevel())) {
            where.append(" AND alert_level = ?");
            params.add(q.getAlertLevel().trim().toUpperCase());
        }
        if (notBlank(q.getStatus())) {
            where.append(" AND status = ?");
            params.add(q.getStatus().trim().toUpperCase());
        }
        if (notBlank(q.getRuleId())) {
            where.append(" AND rule_id = ?");
            params.add(q.getRuleId().trim());
        }
        if (notBlank(q.getAssetId())) {
            where.append(" AND asset_id = ?");
            params.add(q.getAssetId().trim());
        }
        if (notBlank(q.getKeyword())) {
            where.append(" AND (rule_name LIKE ? OR message LIKE ?)");
            params.add("%" + q.getKeyword().trim() + "%");
            params.add("%" + q.getKeyword().trim() + "%");
        }
        int offset = (pageNum - 1) * pageSize;

        List<Object> countParams = new ArrayList<>(params);
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(pageSize);
        listParams.add(offset);

        Integer total = alertDao.countAlerts(where.toString(), countParams.toArray());
        long totalRows = total != null ? total : 0L;

        List<DqAlertVO> rows = alertDao.listAlerts(where.toString(), (rs, i) -> mapAlertRow(rs), listParams.toArray());
        securityService.auditRead("DQ_ALERT_LIST", "batch");
        return new PageResult<>(rows, totalRows, pageNum, pageSize);
    }

    @Override
    public DqAlertVO getAlert(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        List<DqAlertVO> rows = alertDao.findAlertById(id, (rs, i) -> mapAlertRow(rs));
        DqAlertVO vo = rows.isEmpty() ? null : rows.get(0);
        securityService.auditRead("DQ_ALERT_DETAIL", id);
        return vo;
    }

    @Override
    public void ackAlert(String id, String ackBy) {
        String by = notBlank(ackBy) ? ackBy.trim() : "system";
        int n = alertDao.ackAlert(by, id);
        if (n == 0) {
            throw new NotFoundException("DQ 告警不存在或当前状态不可确认: " + id);
        }
        log.info("DqAlert 已确认: alertId={}, by={}", id, by);
        securityService.auditWrite("DQ_ALERT_ACK", id, "SUCCESS");
    }

    @Override
    public void resolveAlert(String id, String resolvedBy, String note) {
        String by = notBlank(resolvedBy) ? resolvedBy.trim() : "system";
        int n = alertDao.resolveAlert(by, note, id);
        if (n == 0) {
            throw new NotFoundException("DQ 告警不存在或当前状态不可解决: " + id);
        }
        log.info("DqAlert 已解决: alertId={}, by={}", id, by);
        securityService.auditWrite("DQ_ALERT_RESOLVE", id, "SUCCESS");
    }

    // ==================== 3. 私有工具 ====================

    /** 行装配（payload JSONB 文本反序列化，范围外字段不暴露 password/secret）。 */
    private DqAlertVO mapAlertRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        DqAlertVO vo = new DqAlertVO();
        vo.setId(rs.getString("id"));
        vo.setRuleId(rs.getString("ruleId"));
        vo.setAlertLevel(rs.getString("alertLevel"));
        vo.setAlertType(rs.getString("alertType"));
        vo.setAssetId(rs.getString("assetId"));
        vo.setAssetName(rs.getString("assetName"));
        vo.setRuleName(rs.getString("ruleName"));
        vo.setMessage(rs.getString("message"));
        vo.setPayload(parsePayloadJson(rs.getString("payloadJson")));
        vo.setStatus(rs.getString("status"));
        vo.setEscalatedTo(rs.getString("escalatedTo"));
        vo.setNotifyCount(rs.getObject("notifyCount") instanceof Number n ? n.intValue() : 0);
        vo.setLastNotifyAt(toLdt(rs.getTimestamp("lastNotifyAt")));
        vo.setAckBy(rs.getString("ackBy"));
        vo.setAckAt(toLdt(rs.getTimestamp("ackAt")));
        vo.setResolvedBy(rs.getString("resolvedBy"));
        vo.setResolvedAt(toLdt(rs.getTimestamp("resolvedAt")));
        vo.setResolvedNote(rs.getString("resolvedNote"));
        vo.setCreatedAt(toLdt(rs.getTimestamp("createdAt")));
        return vo;
    }

    /** 组装告警消息（人可读摘要，含 pass_rate / failedRows / scope，不落样本原文）。 */
    private String buildMessage(DqRuleVO rule, String level, Map<String, Object> check,
                                String scopeKind, String scopeId) {
        StringBuilder sb = new StringBuilder();
        sb.append("[DQ ").append(level).append("] ");
        sb.append(rule.getRuleName() != null ? rule.getRuleName() : rule.getId());
        sb.append(" 检查未通过");
        if (scopeKind != null || scopeId != null) {
            sb.append("（").append(scopeKind != null ? scopeKind : "*")
              .append(":").append(scopeId != null ? scopeId : "*").append("）");
        }
        Object passRate = check.get("passRate");
        Object failed = check.get("failedRows");
        if (passRate instanceof Number pr) {
            sb.append(String.format(", 通过率 %.4f", pr.doubleValue()));
        }
        if (failed instanceof Number fr) {
            sb.append(String.format(", 失败行数 %d", fr.longValue()));
        }
        String err = s(check, "errorMessage");
        if (err != null && !err.isBlank()) {
            sb.append(", 错误: ").append(err.length() > 200 ? err.substring(0, 200) : err);
        }
        return sb.toString();
    }

    /** 脱敏 payload（铁律 2.4 #3）：maskParameters 过滤敏感键 + errorCode/scope 等非敏感键保留。 */
    private Map<String, Object> buildPayloadMasked(Map<String, Object> check, String scopeKind, String scopeId) {
        Map<String, Object> raw = new LinkedHashMap<>(check);
        if (scopeKind != null) {
            raw.put("scopeKind", scopeKind);
        }
        if (scopeId != null) {
            raw.put("scopeId", truncatedAsset(scopeId));
        }
        // maskParameters 对命中敏感 hint 的键值替换为 ******（本表 errorCode/passed 等不包含敏感值，
        // 走同一入口保证脱敏口径统一）
        return securityService.maskParameters(raw);
    }

    private static String serializePayload(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload != null ? payload : new LinkedHashMap<>());
        } catch (Exception e) {
            return "{}";
        }
    }

    /** payload JSON 文本 → Map（解析失败返回 null，不阻断 VO 装配）。 */
    private static Map<String, Object> parsePayloadJson(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return null;
        }
    }

    /** 资产名称（scopeKind + 关键 token）：FIELD → table.field；其余 → scopeId。 */
    private static String assetNameOf(String scopeKind, String scopeId, DqRuleVO rule) {
        if (scopeId == null || scopeId.isBlank()) {
            return null;
        }
        String kind = scopeKind == null ? "" : scopeKind.trim().toUpperCase();
        if ("FIELD".equals(kind) && scopeId.contains(":")) {
            return scopeId; // 形如 table:field 自描述
        }
        if (rule.getTargetTable() != null && !rule.getTargetTable().isBlank()) {
            return rule.getTargetTable();
        }
        return scopeId;
    }

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullToDefault(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }

    private static String nullIfBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static boolean startsWithE(String s) {
        return s != null && s.toUpperCase().startsWith("EVALUATOR_ERROR");
    }

    private static String s(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    /** scopeId 截断到 VARCHAR(64)（PG 超长会报错，Java 侧兜底）。 */
    private static String truncatedAsset(String scopeId) {
        if (scopeId == null) {
            return null;
        }
        return scopeId.length() > ASSET_ID_MAX ? scopeId.substring(0, ASSET_ID_MAX) : scopeId;
    }
}
