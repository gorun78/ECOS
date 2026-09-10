package com.chinacreator.gzcm.engine.data.quality.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.engine.data.quality.DqRcaService;
import com.chinacreator.gzcm.engine.data.quality.DqWorkOrderService;
import com.chinacreator.gzcm.engine.data.quality.model.DqAlertVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRcaRequest;
import com.chinacreator.gzcm.engine.data.quality.model.DqRcaResult;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DQ 工单服务实现（PMO-48-C T12 + T13 协同）。
 *
 * <p><b>T12 范围</b>（最小集）：createWorkOrder（告警→工单 order_no=WO-yyyyMMdd-NNN）+
 * listWorkOrders / getWorkOrder + audit 安全卡。</p>
 *
 * <p><b>T13 状态机扩展</b>（7 态 PENDING / ASSIGNED / IN_WORK / RESOLVED / VERIFIED /
 * CLOSED / REJECTED）：</p>
 * <pre>
 *   PENDING  ──assign──▶ ASSIGNED  ──startWork──▶ IN_WORK
 *   ASSIGNED ──reject──▶ REJECTED  IN_WORK  ──resolve──▶ RESOLVED
 *   PENDING  ──reject──▶ REJECTED  RESOLVED ──verify(pass)──▶ VERIFIED
 *   IN_WORK  ──reject──▶ REJECTED  RESOLVED ──verify(fail)──▶ IN_WORK (+ retry_count++)
 *   RESOLVED ──reject──▶ REJECTED  VERIFIED  ──close──▶ CLOSED
 * </pre>
 *
 * <p>状态转换白名单 {@link #TRANSITIONS}：static Map{@literal <String, Set{@literal <String<b>}}.
 * 任何不在白名单内的转换抛 {@code BusinessException("illegal status transition: " + current +
 * "->" + target)}。</p>
 *
 * <p><b>自动 RCA 触发</b>：severity ∈ {CRITICAL, HIGH}（对应 P0/P1）的工单执行
 * {@link #startWork} 进入 IN_WORK 后，@Async 调 {@link #triggerAutoRca} —— 即
 * {@link #runRca(String)} 同实现（PMO-48-C §12 决策 #4：P0/P1 自动 RCA）。
 * {@link DqRcaService} 本波 stub 返回 confidence=0.0；真接 cognitive-engine 在 Phase 4。</p>
 *
 * <p><b>重试限制</b>：{@link #verify} 失败时 retry_count++；retry_count &gt;= 3 → 强制
 * handling_mode=MANUAL（铁律 决策 #4：达到上限后不再自动修复，强制人工介入）。</p>
 *
 * <p><b>升级策略</b>：{@link #escalateUnack(String)} 手动触发单工单升级（自动化
 * 定时任务留 Phase 5 走 runtime-task 全局调度，铁律 2.5 #3）。逻辑：PENDING + 5min 未 ack
 * + severity MEDIUM/LOW → HIGH；HIGH → CRITICAL；CRITICAL 已是最高级不动。失败留痕
 * error 日志不抛。</p>
 *
 * <p>安全卡（铁律 2.4 #5）：每个状态动作 1 处 audit（DQ_WO_*）；读操作 auditRead。</p>
 *
 * <p>Bean 名 {@code ecosDqWorkOrderService}（铁律 1.3 防多 Bean 冲突）。</p>
 *
 * @author PMO-48-C T12 + T13
 */
@Service("ecosDqWorkOrderService")
public class DqWorkOrderServiceImpl implements DqWorkOrderService {

    private static final Logger log = LoggerFactory.getLogger(DqWorkOrderServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==================== 状态机 7 态 ====================

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ASSIGNED = "ASSIGNED";
    private static final String STATUS_IN_WORK = "IN_WORK";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String STATUS_VERIFIED = "VERIFIED";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String STATUS_REJECTED = "REJECTED";

    /**
     * T13 状态转换白名单：{@code current -> allowedTargets}。任何不在白名单内的转换
     * 抛 {@code BusinessException("illegal status transition: current->target")}。
     */
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
            STATUS_PENDING,  Set.of(STATUS_ASSIGNED, STATUS_REJECTED),
            STATUS_ASSIGNED, Set.of(STATUS_IN_WORK, STATUS_REJECTED),
            STATUS_IN_WORK,  Set.of(STATUS_RESOLVED, STATUS_REJECTED),
            STATUS_RESOLVED, Set.of(STATUS_VERIFIED, STATUS_REJECTED, STATUS_IN_WORK),
            STATUS_VERIFIED, Set.of(STATUS_CLOSED));

    /** 允许被驳回的状态集合（终态 VERIFIED/CLOSED/REJECTED 不允许驳回 — 铁律顺序）。 */
    static final Set<String> REJECTABLE_STATES = Set.of(
            STATUS_PENDING, STATUS_ASSIGNED, STATUS_IN_WORK, STATUS_RESOLVED);

    /** P0/P1 自动 RCA 的 severity 集合（对应 dq 方案 P0=CRITICAL, P1=HIGH）。 */
    static final Set<String> AUTO_RCA_SEVERITIES = Set.of("CRITICAL", "HIGH");

    /** verify 失败达到上限时强制 MANUAL（铁律 决策 #4：3 次重试上限）。 */
    static final int MAX_RETRY_COUNT = 3;

    /** 升级策略：PENDING 超过该分钟数未认领即升级（需求 "5min 未认领"）。 */
    static final long ESCALATION_THRESHOLD_MINUTES = 5L;

    /** 风险排行 severity 权重（CRITICAL=4, HIGH=3, MEDIUM=2, LOW=1）。 */
    private static final Map<String, Integer> SEVERITY_WEIGHTS = Map.of(
            "CRITICAL", 4, "HIGH", 3, "MEDIUM", 2, "LOW", 1);

    /** SELECT 列清单（短别名 mapVo 据此映射；rca_result 强制 ::text 防 JSONB 不解）。 */
    private static final String WO_COLUMNS =
            "id, order_no AS orderNo, alert_id AS alertId, rule_id AS ruleId," +
            " asset_id AS assetId, title, description, status, handling_mode AS handlingMode," +
            " severity, assigned_to AS assignedTo, assigned_at AS assignedAt," +
            " repair_action AS repairAction, repair_status AS repairStatus, repair_log AS repairLog," +
            " verified_by AS verifiedBy, verified_at AS verifiedAt, verify_pass AS verifyPass," +
            " verify_note AS verifyNote, resolved_by AS resolvedBy, resolved_at AS resolvedAt," +
            " resolution_note AS resolutionNote," +
            " rca_result::text AS rcaResult, rca_confidence AS rcaConfidence," +
            " rca_analyzed_at AS rcaAnalyzedAt," +
            " retry_count AS retryCount, created_at AS createdAt, updated_at AS updatedAt," +
            " closed_at AS closedAt";

    private final DqSecurityService securityService;
    private final DqRcaService rcaService;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public DqWorkOrderServiceImpl(DqSecurityService securityService,
                                  DqRcaService rcaService,
                                  ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.securityService = securityService;
        this.rcaService = rcaService;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    // ==================== 1. T12: create / list / get ====================

    @Override
    @Transactional
    public String createWorkOrder(DqAlertVO alert, DqRuleVO rule) {
        if (alert == null) {
            throw new BusinessException("alert 不能为 null");
        }
        if (alert.getId() == null || alert.getId().isBlank()) {
            throw new BusinessException("alert.id 不能为空");
        }
        if (rule == null || rule.getId() == null || rule.getId().isBlank()) {
            throw new BusinessException("rule 与 rule.id 不能为空");
        }
        JdbcTemplate jdbc = jdbc();
        String orderNo = generateOrderNo(jdbc);
        String severity = rule.getSeverity() == null ? "MEDIUM" : rule.getSeverity();
        String handlingMode = resolveHandlingMode(rule);
        String status = STATUS_PENDING; // T13 状态机：初始 PENDING
        String assetId = alert.getAssetId() != null ? alert.getAssetId() : rule.getTargetId();
        String title = alert.getRuleName() != null
                ? "[" + severity + "] " + alert.getRuleName()
                : "[" + severity + "] DQ Rule Alert";
        String description = alert.getMessage() != null ? alert.getMessage() : "";
        String id = UUID.randomUUID().toString();

        jdbc.update(
                "INSERT INTO ecos_dq.dq_work_order" +
                " (id, order_no, alert_id, rule_id, asset_id, title, description, status, handling_mode, severity," +
                "  retry_count, created_at, updated_at, is_deleted)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NOW(), NOW(), FALSE)",
                id, orderNo, alert.getId(), rule.getId(), assetId, title, description,
                status, handlingMode, severity);

        log.info("DqWorkOrder created: id={}, orderNo={}, severity={}, mode={}, status={}",
                id, orderNo, severity, handlingMode, status);
        securityService.auditWrite("DQ_WO_CREATE", id, "SUCCESS");
        return id;
    }

    @Override
    public PageResult<DqWorkOrderVO> listWorkOrders(DqWorkOrderQuery query) {
        int pageNum = (query == null || query.getPageNum() == null || query.getPageNum() < 1) ? 1 : query.getPageNum();
        int pageSize = (query == null || query.getPageSize() == null || query.getPageSize() < 1) ? 20 : query.getPageSize();
        if (pageSize > 200) {
            throw new BusinessException("工单列表 pageSize 上限 200");
        }
        JdbcTemplate jdbc = jdbc();
        // 动态 WHERE（全字段可空 → 不过滤；? 占位防注入）
        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        where.add("is_deleted = FALSE");
        if (query != null) {
            if (clean(query.getStatus()) != null) {
                where.add("status = ?");
                args.add(query.getStatus().trim().toUpperCase());
            }
            if (clean(query.getSeverity()) != null) {
                where.add("severity = ?");
                args.add(query.getSeverity().trim().toUpperCase());
            }
            if (clean(query.getHandlingMode()) != null) {
                where.add("handling_mode = ?");
                args.add(query.getHandlingMode().trim().toUpperCase());
            }
            if (clean(query.getAssetId()) != null) {
                where.add("asset_id = ?");
                args.add(query.getAssetId().trim());
            }
            if (clean(query.getRuleId()) != null) {
                where.add("rule_id = ?");
                args.add(query.getRuleId().trim());
            }
            if (clean(query.getKeyword()) != null) {
                where.add("(title ILIKE ? OR order_no ILIKE ?)");
                String kw = "%" + query.getKeyword().trim() + "%";
                args.add(kw);
                args.add(kw);
            }
        }
        String whereSql = " WHERE " + String.join(" AND ", where);
        Long totalObj = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_dq.dq_work_order" + whereSql, Long.class,
                args.toArray());
        long total = totalObj != null ? totalObj : 0L;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<DqWorkOrderVO> page = jdbc.query(
                "SELECT " + WO_COLUMNS + whereSql +
                " ORDER BY created_at DESC, id LIMIT ? OFFSET ?",
                ROW_MAPPER,
                pageArgs.toArray());
        return new PageResult<>(page, total, pageNum, pageSize);
    }

    @Override
    public DqWorkOrderVO getWorkOrder(String id) {
        if (clean(id) == null) {
            return null;
        }
        JdbcTemplate jdbc = jdbc();
        List<DqWorkOrderVO> rows = jdbc.query(
                "SELECT " + WO_COLUMNS +
                " FROM ecos_dq.dq_work_order WHERE id = ? AND is_deleted = FALSE",
                ROW_MAPPER,
                id.trim());
        DqWorkOrderVO vo = rows.isEmpty() ? null : rows.get(0);
        securityService.auditRead("DQ_WO_DETAIL", id.trim());
        return vo;
    }

    // ==================== 2. T13: 状态机 ====================

    @Override
    @Transactional
    public void assign(String workOrderId, String assignedTo, String assigner) {
        if (clean(workOrderId) == null) {
            throw new BusinessException("workOrderId 不能为空");
        }
        if (clean(assignedTo) == null) {
            throw new BusinessException("assignedTo 不能为空");
        }
        JdbcTemplate jdbc = jdbc();
        DqWorkOrderVO cur = requireCurrent(jdbc, workOrderId.trim());
        checkTransition(cur.getStatus(), STATUS_ASSIGNED);
        jdbc.update(
                "UPDATE ecos_dq.dq_work_order" +
                " SET status = ?, assigned_to = ?, assigned_at = NOW()," +
                "     handling_mode = ?, updated_at = NOW()" +
                " WHERE id = ? AND status = ?",
                STATUS_ASSIGNED, assignedTo.trim(),
                cur.getHandlingMode() != null ? cur.getHandlingMode() : "MANUAL",
                workOrderId.trim(), cur.getStatus());
        log.info("DqWorkOrder assigned: id={}, by={}, to={}", workOrderId, assigner, assignedTo);
        securityService.auditWrite("DQ_WO_ASSIGN", workOrderId.trim(), "SUCCESS");
    }

    @Override
    @Transactional
    public void startWork(String workOrderId, String operator) {
        if (clean(workOrderId) == null) {
            throw new BusinessException("workOrderId 不能为空");
        }
        JdbcTemplate jdbc = jdbc();
        DqWorkOrderVO cur = requireCurrent(jdbc, workOrderId.trim());
        checkTransition(cur.getStatus(), STATUS_IN_WORK);
        jdbc.update(
                "UPDATE ecos_dq.dq_work_order SET status = ?, updated_at = NOW()" +
                " WHERE id = ? AND status = ?",
                STATUS_IN_WORK, workOrderId.trim(), cur.getStatus());
        log.info("DqWorkOrder started: id={}, operator={}", workOrderId, operator);
        securityService.auditWrite("DQ_WO_START", workOrderId.trim(), "SUCCESS");
        // T13 自动 RCA：severity ∈ {CRITICAL, HIGH}（P0/P1）异步触发，失败不阻塞 IN_WORK
        if (cur.getSeverity() != null && AUTO_RCA_SEVERITIES.contains(cur.getSeverity().toUpperCase())) {
            triggerAutoRca(workOrderId.trim());
        }
    }

    @Override
    @Transactional
    public void resolve(String workOrderId, String resolvedBy, String resolutionNote) {
        if (clean(workOrderId) == null) {
            throw new BusinessException("workOrderId 不能为空");
        }
        JdbcTemplate jdbc = jdbc();
        DqWorkOrderVO cur = requireCurrent(jdbc, workOrderId.trim());
        checkTransition(cur.getStatus(), STATUS_RESOLVED);
        jdbc.update(
                "UPDATE ecos_dq.dq_work_order" +
                " SET status = ?, resolved_by = ?, resolved_at = NOW()," +
                "     resolution_note = ?, updated_at = NOW()" +
                " WHERE id = ? AND status = ?",
                STATUS_RESOLVED,
                resolvedBy != null ? resolvedBy.trim() : "system",
                resolutionNote, workOrderId.trim(), cur.getStatus());
        log.info("DqWorkOrder resolved: id={}, by={}", workOrderId, resolvedBy);
        securityService.auditWrite("DQ_WO_RESOLVE", workOrderId.trim(), "SUCCESS");
    }

    @Override
    @Transactional
    public void verify(String workOrderId, String verifiedBy, boolean pass, String note) {
        if (clean(workOrderId) == null) {
            throw new BusinessException("workOrderId 不能为空");
        }
        JdbcTemplate jdbc = jdbc();
        DqWorkOrderVO cur = requireCurrent(jdbc, workOrderId.trim());
        String target = pass ? STATUS_VERIFIED : STATUS_IN_WORK; // verify fail 回 IN_WORK
        checkTransition(cur.getStatus(), target);
        int retry = cur.getRetryCount() != null ? cur.getRetryCount() : 0;
        String handlingMode = cur.getHandlingMode() != null ? cur.getHandlingMode() : "MANUAL";
        if (!pass) {
            retry++;
            if (retry >= MAX_RETRY_COUNT && "AUTO_REPAIR".equalsIgnoreCase(handlingMode)) {
                // 铁律 决策 #4：达到重试上限 → 强制 MANUAL 不再自动修复
                log.warn("DqWorkOrder verify fail &lt;={}: id={}, retry={}, forcing handling_mode=MANUAL",
                        MAX_RETRY_COUNT, workOrderId, retry);
                handlingMode = "MANUAL";
            }
        }
        jdbc.update(
                "UPDATE ecos_dq.dq_work_order" +
                " SET status = ?, verified_by = ?, verified_at = NOW()," +
                "     verify_pass = ?, verify_note = ?," +
                "     retry_count = ?, handling_mode = ?," +
                "     updated_at = NOW()" +
                " WHERE id = ? AND status = ?",
                target, verifiedBy != null ? verifiedBy.trim() : "system",
                pass, note, retry, handlingMode,
                workOrderId.trim(), cur.getStatus());
        log.info("DqWorkOrder verified: id={}, pass={}, retry={}", workOrderId, pass, retry);
        securityService.auditWrite("DQ_WO_VERIFY", workOrderId.trim(), pass ? "PASS" : "FAIL");
    }

    @Override
    @Transactional
    public void close(String workOrderId, String closer) {
        if (clean(workOrderId) == null) {
            throw new BusinessException("workOrderId 不能为空");
        }
        JdbcTemplate jdbc = jdbc();
        DqWorkOrderVO cur = requireCurrent(jdbc, workOrderId.trim());
        checkTransition(cur.getStatus(), STATUS_CLOSED);
        jdbc.update(
                "UPDATE ecos_dq.dq_work_order SET status = ?, closed_at = NOW(), updated_at = NOW()" +
                " WHERE id = ? AND status = ?",
                STATUS_CLOSED, workOrderId.trim(), cur.getStatus());
        log.info("DqWorkOrder closed: id={}, closer={}", workOrderId, closer);
        securityService.auditWrite("DQ_WO_CLOSE", workOrderId.trim(), "SUCCESS");
    }

    @Override
    @Transactional
    public void reject(String workOrderId, String rejector, String reason) {
        if (clean(workOrderId) == null) {
            throw new BusinessException("workOrderId 不能为空");
        }
        JdbcTemplate jdbc = jdbc();
        DqWorkOrderVO cur = requireCurrent(jdbc, workOrderId.trim());
        checkTransition(cur.getStatus(), STATUS_REJECTED);
        // 备注推到 description（DB 无 reject_note 列 — 铁律 3.1 不破坏 schema）
        jdbc.update(
                "UPDATE ecos_dq.dq_work_order" +
                " SET status = ?," +
                "     description = COALESCE(description, '') || ?," +
                "     updated_at = NOW()" +
                " WHERE id = ? AND status = ?",
                STATUS_REJECTED,
                "\n[REJECTED by " + (rejector != null ? rejector : "system") + "]: "
                        + (reason != null ? reason : "(no reason)"),
                workOrderId.trim(), cur.getStatus());
        log.info("DqWorkOrder rejected: id={}, by={}, reason={}", workOrderId, rejector, reason);
        securityService.auditWrite("DQ_WO_REJECT", workOrderId.trim(), "SUCCESS");
    }

    // ==================== 3. T13: 升级策略（手动；Phase 5 走 runtime-task） ====================

    @Override
    @Transactional
    public boolean escalateUnack(String workOrderId) {
        if (clean(workOrderId) == null) {
            throw new BusinessException("workOrderId 不能为空");
        }
        JdbcTemplate jdbc = jdbc();
        DqWorkOrderVO cur = requireCurrent(jdbc, workOrderId.trim());
        // 1. 状态必须 PENDING（其它状态提前返回不抛）
        if (!STATUS_PENDING.equalsIgnoreCase(cur.getStatus())) {
            log.debug("DqWorkOrder escalateUnack skipped (not PENDING): id={}, status={}",
                    workOrderId, cur.getStatus());
            return false;
        }
        // 2. created_at 5 分钟门槛
        if (cur.getCreatedAt() == null) {
            log.warn("DqWorkOrder escalateUnack skipped (createdAt null): id={}", workOrderId);
            return false;
        }
        long minutes = Duration.between(cur.getCreatedAt(), LocalDateTime.now()).toMinutes();
        if (minutes < ESCALATION_THRESHOLD_MINUTES) {
            log.debug("DqWorkOrder escalateUnack skipped (less than {}min): id={}, elapsed={}min",
                    ESCALATION_THRESHOLD_MINUTES, workOrderId, minutes);
            return false;
        }
        // 3. 按 severity 升级：MEDIUM/LOW → HIGH；HIGH → CRITICAL；CRITICAL 已是最高级不动
        String sev = cur.getSeverity() == null ? "MEDIUM" : cur.getSeverity().trim().toUpperCase();
        String newSev = null;
        if ("MEDIUM".equals(sev) || "LOW".equals(sev)) {
            newSev = "HIGH";
        } else if ("HIGH".equals(sev)) {
            newSev = "CRITICAL";
        }
        if (newSev == null) {
            // 已是 CRITICAL 或未知 severity，不升级
            log.info("DqWorkOrder escalateUnack skipped (no path): id={}, severity={}",
                    workOrderId, sev);
            return false;
        }
        jdbc.update(
                "UPDATE ecos_dq.dq_work_order SET severity = ?, updated_at = NOW()" +
                " WHERE id = ? AND status = ? AND is_deleted = FALSE",
                newSev, workOrderId.trim(), STATUS_PENDING);
        log.info("DqWorkOrder escalated: id={}, {} → {} ({}min unacknowledged)",
                workOrderId, sev, newSev, minutes);
        securityService.auditWrite("DQ_WO_ESCALATE", workOrderId.trim(), "SUCCESS");
        return true;
    }

    // ==================== 4. T13: RCA（手动 + 自动 stub） ====================

    @Override
    public Map<String, Object> runRca(String workOrderId) {
        if (clean(workOrderId) == null) {
            throw new BusinessException("workOrderId 不能为空");
        }
        JdbcTemplate jdbc = jdbc();
        DqWorkOrderVO cur = requireCurrent(jdbc, workOrderId.trim());
        // 构造 RCA 请求（ruleId / assetId 来自工单，workOrderId 留痕审计）
        DqRcaRequest req = DqRcaRequest.builder()
                .failedRuleId(cur.getRuleId())
                .assetId(cur.getAssetId())
                .timeWindow("1h")
                .workOrderId(workOrderId.trim())
                .relatedPipelineIds(new ArrayList<>())
                .build();
        DqRcaResult result;
        try {
            // 本波 stub 直返；Phase 4 真接 cognitive-engine 改 DqRcaServiceImpl
            result = rcaService.runDiagnose(req);
        } catch (RuntimeException e) {
            log.warn("DqWorkOrder runRca 调用失败（不阻塞工单）: id={}, error={}",
                    workOrderId, e.getMessage(), e);
            result = DqRcaResult.builder()
                    .rootCause("RCA_UNAVAILABLE: "
                            + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()))
                    .confidence(0.0D)
                    .causalChain(new ArrayList<>())
                    .candidates(new ArrayList<>())
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
        // 写 rca_result JSONB + rca_confidence + rca_analyzed_at（任何状态都允许）
        String resultJson = toJson(result);
        Double conf = result.getConfidence() != null ? result.getConfidence() : 0.0D;
        jdbc.update(
                "UPDATE ecos_dq.dq_work_order" +
                " SET rca_result = ?::jsonb, rca_confidence = ?, rca_analyzed_at = NOW()," +
                "     updated_at = NOW()" +
                " WHERE id = ? AND is_deleted = FALSE",
                resultJson, conf, workOrderId.trim());
        log.info("DqWorkOrder RCA done: id={}, confidence={}, rootCause={}",
                workOrderId, conf, result.getRootCause());
        securityService.auditWrite("DQ_WO_RUN_RCA", workOrderId.trim(), "SUCCESS");
        // 返回 Map 副本（stub 阶段 confidence=0.0，前端可断言）
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("workOrderId", workOrderId.trim());
        out.put("rootCause", result.getRootCause());
        out.put("confidence", conf);
        out.put("causalChain", result.getCausalChain() != null ? result.getCausalChain() : new ArrayList<>());
        out.put("candidates", result.getCandidates() != null ? result.getCandidates() : new ArrayList<>());
        out.put("analyzedAt", System.currentTimeMillis());
        return out;
    }

    /** 自动 RCA 异步触发（IN_WORK 进入 + severity P0/P1 时 @Async，不阻塞 IN_WORK）。 */
    @Async
    public void triggerAutoRca(String workOrderId) {
        String id = clean(workOrderId);
        if (id == null) {
            return;
        }
        try {
            Map<String, Object> r = runRca(id);
            Object conf = r.get("confidence");
            double c = conf instanceof Number n ? n.doubleValue() : 0.0D;
            log.info("DqWorkOrder autoRCA done (async): id={}, confidence={}", id, c);
        } catch (RuntimeException e) {
            log.error("DqWorkOrder autoRCA async failed (ignored, do not block): id={}, error={}",
                    id, e.getMessage(), e);
            // 写 rca_confidence=0 兜底（不阻塞工单）
            try {
                JdbcTemplate jdbc = jdbc();
                jdbc.update(
                        "UPDATE ecos_dq.dq_work_order SET rca_confidence = 0," +
                        "  rca_analyzed_at = NOW(), updated_at = NOW()" +
                        "  WHERE id = ? AND is_deleted = FALSE",
                        id);
            } catch (RuntimeException e2) {
                log.warn("DqWorkOrder autoRCA 标记 confidence=0 再次失败（不阻塞）: id={}, error={}",
                        id, e2.getMessage());
            }
        }
    }

    // ==================== 5. T13: 风险工单排行 ====================

    @Override
    public List<Map<String, Object>> riskRanking(String grade, int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 10 : limit, 100));
        String g = clean(grade) == null ? null : grade.trim().toUpperCase();
        JdbcTemplate jdbc = jdbc();
        // severity 优先级（CRITICAL>HIGH>MEDIUM>LOW 映射 CASE → 4/3/2/1）
        // + retry_count DESC + created_at DESC（同级同 retry 取最新先）
        String sevClause = (g == null)
                ? "(w.severity IS NOT NULL)"
                : "w.severity = ?";
        String sql =
                "SELECT w.id AS id, w.order_no AS orderNo, w.severity AS severity," +
                "       w.status AS status, w.asset_id AS assetId, w.rule_id AS ruleId," +
                "       CASE w.severity WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3" +
                "            WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END AS weight," +
                "       w.retry_count AS retryCount, w.created_at AS createdAt," +
                "       w.rca_confidence AS rcaConfidence, w.title AS title" +
                "  FROM ecos_dq.dq_work_order w" +
                " WHERE w.is_deleted = FALSE AND " + sevClause +
                " ORDER BY weight DESC, w.retry_count DESC, w.created_at DESC" +
                " LIMIT ?";
        List<Object> args = new ArrayList<>();
        if (g != null) {
            args.add(g);
        }
        args.add(safeLimit);
        return jdbc.query(sql, (rs, rowNum) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("orderNo", rs.getString("orderNo"));
            m.put("severity", rs.getString("severity"));
            m.put("status", rs.getString("status"));
            m.put("assetId", rs.getString("assetId"));
            m.put("ruleId", rs.getString("ruleId"));
            m.put("weight", rs.getInt("weight"));
            m.put("retryCount", rs.getInt("retryCount"));
            Timestamp ts = rs.getTimestamp("createdAt");
            m.put("createdAt", ts != null ? ts.toLocalDateTime() : null);
            Double conf = rs.getDouble("rcaConfidence");
            m.put("rcaConfidence", rs.wasNull() ? null : conf);
            m.put("title", rs.getString("title"));
            return m;
        }, args.toArray());
    }

    // ==================== 私有工具 ====================

    /** 行 → VO 映射器（JdbcTemplate RowMapper，复用 T12 风格，集中此处避免重复 lambda）。 */
    private static final RowMapper<DqWorkOrderVO> ROW_MAPPER = new RowMapper<>() {
        @Override
        public DqWorkOrderVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return mapVo(rs);
        }
    };

    /** 状态转换白名单校验 — current 必须在 TRANSITIONS 表内且 target 在 allowed 集合。 */
    private static void checkTransition(String current, String target) {
        if (current == null) {
            throw new BusinessException("工单当前状态不可读（current=null）");
        }
        if (target == null) {
            throw new BusinessException("工单目标状态不可读（target=null）");
        }
        Set<String> allowed = TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(target)) {
            throw new BusinessException("illegal status transition: " + current + "->" + target);
        }
    }

    /** 取工单当前行（不存在 / 已删除 → NotFoundException）。 */
    private DqWorkOrderVO requireCurrent(JdbcTemplate jdbc, String id) {
        List<DqWorkOrderVO> rows = jdbc.query(
                "SELECT " + WO_COLUMNS +
                " FROM ecos_dq.dq_work_order WHERE id = ? AND is_deleted = FALSE",
                ROW_MAPPER, id);
        if (rows.isEmpty()) {
            throw new NotFoundException("DQ 工单 " + id + " 不存在或已删除");
        }
        return rows.get(0);
    }

    /** 取 JdbcTemplate bean（ObjectProvider 软依赖；不可用抛 BusinessException）。 */
    private JdbcTemplate jdbc() {
        JdbcTemplate tpl = jdbcTemplateProvider.getIfAvailable();
        if (tpl == null) {
            throw new BusinessException("JdbcTemplate 不可用，无法执行 DQ 工单操作");
        }
        return tpl;
    }

    /** 行 → VO（驼峰列直读；verify_pass 用 wasNull 判空；rca_confidence 同样）。 */
    static DqWorkOrderVO mapVo(ResultSet rs) {
        try {
            DqWorkOrderVO vo = new DqWorkOrderVO();
            vo.setId(rs.getString("id"));
            vo.setOrderNo(rs.getString("orderNo"));
            vo.setAlertId(rs.getString("alertId"));
            vo.setRuleId(rs.getString("ruleId"));
            vo.setAssetId(rs.getString("assetId"));
            vo.setTitle(rs.getString("title"));
            vo.setDescription(rs.getString("description"));
            vo.setStatus(rs.getString("status"));
            vo.setHandlingMode(rs.getString("handlingMode"));
            vo.setSeverity(rs.getString("severity"));
            vo.setAssignedTo(rs.getString("assignedTo"));
            Timestamp ats = rs.getTimestamp("assignedAt");
            vo.setAssignedAt(ats != null ? ats.toLocalDateTime() : null);
            vo.setRepairAction(rs.getString("repairAction"));
            vo.setRepairStatus(rs.getString("repairStatus"));
            vo.setRepairLog(rs.getString("repairLog"));
            vo.setVerifiedBy(rs.getString("verifiedBy"));
            Timestamp vat = rs.getTimestamp("verifiedAt");
            vo.setVerifiedAt(vat != null ? vat.toLocalDateTime() : null);
            vo.setVerifyPass(rs.getBoolean("verifyPass") && !rs.wasNull());
            vo.setVerifyNote(rs.getString("verifyNote"));
            vo.setResolvedBy(rs.getString("resolvedBy"));
            Timestamp rat = rs.getTimestamp("resolvedAt");
            vo.setResolvedAt(rat != null ? rat.toLocalDateTime() : null);
            vo.setResolutionNote(rs.getString("resolutionNote"));
            // T13 RCA 字段
            vo.setRcaResult(rs.getString("rcaResult"));
            double rc = rs.getDouble("rcaConfidence");
            vo.setRcaConfidence(rs.wasNull() ? null : rc);
            Timestamp crct = rs.getTimestamp("rcaAnalyzedAt");
            vo.setRcaAnalyzedAt(crct != null ? crct.toLocalDateTime() : null);
            int rc2 = rs.getInt("retryCount");
            vo.setRetryCount(rc2);
            Timestamp ct = rs.getTimestamp("createdAt");
            vo.setCreatedAt(ct != null ? ct.toLocalDateTime() : null);
            Timestamp ut = rs.getTimestamp("updatedAt");
            vo.setUpdatedAt(ut != null ? ut.toLocalDateTime() : null);
            Timestamp clt = rs.getTimestamp("closedAt");
            vo.setClosedAt(clt != null ? clt.toLocalDateTime() : null);
            return vo;
        } catch (java.sql.SQLException e) {
            // RowMapper 契约允许抛 SQLException；此处包装为 BusinessException 保留友好语义
            throw new BusinessException("读取 DQ 工单行失败: " + e.getMessage());
        }
    }

    /** 生成可读工单号 WO-yyyyMMdd-NNN（同日期已有则 +1；空表 / 无匹配从 1 起）。 */
    private String generateOrderNo(JdbcTemplate jdbc) {
        String date = LocalDate.now().toString().replace("-", "");
        String prefix = "WO-" + date + "-";
        // PG: 取当日已用最大 3+ 位序号 + 1；空表时返回 1
        Integer maxSeq = jdbc.queryForObject(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(order_no FROM 'WO-\\d{8}-(\\d{3,})$') AS INTEGER)) + 1, 1)" +
                "  FROM ecos_dq.dq_work_order WHERE order_no LIKE ? || '%'",
                Integer.class, prefix);
        int seq = maxSeq != null ? maxSeq : 1;
        return prefix + String.format("%03d", seq);
    }

    /** 处理模式：AUTO_REPAIR 仅在 rule.parameters 含 autoRepair=true 时启用，缺省 MANUAL。 */
    private static String resolveHandlingMode(DqRuleVO rule) {
        String paramsJson = rule != null ? rule.getParametersJson() : null;
        if (paramsJson == null || paramsJson.isBlank()) {
            return "MANUAL";
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = MAPPER.readValue(paramsJson, Map.class);
            Object v = params.get("autoRepair");
            if (Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v))) {
                return "AUTO_REPAIR";
            }
        } catch (JsonProcessingException ignore) {
            // 参数 JSON 非法不影响创建（按 MANUAL 处理）
        }
        return "MANUAL";
    }

    /** 把 DqRcaResult 序列化成 JSON 文本（落 rca_result::jsonb）。 */
    private static String toJson(Object o) {
        if (o == null) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /** trim 后空串归一为 null。 */
    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
