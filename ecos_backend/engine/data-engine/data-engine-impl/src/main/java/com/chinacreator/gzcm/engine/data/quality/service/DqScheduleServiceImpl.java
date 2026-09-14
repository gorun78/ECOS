package com.chinacreator.gzcm.engine.data.quality.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.engine.data.quality.DqAlertService;
import com.chinacreator.gzcm.engine.data.quality.DqScheduleService;
import com.chinacreator.gzcm.engine.data.quality.DqScoreService;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqScheduleMapper;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqThrottleRow;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqScheduleDTO;
import com.chinacreator.gzcm.engine.data.quality.model.DqScheduleVO;
import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionScore;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DQ 监控调度服务实现（PMO-48-C T11）。
 *
 * <p>核心能力：</p>
 * <ol>
 *   <li>Schedule CRUD — 读写 {@code ecos_dq.dq_schedule}（MyBatis 显式 ecos_dq. 前缀）</li>
 *   <li>{@link #runRuleBatch} — 核心批执行：拉 ACTIVE 规则 → 限流检查 → 逐条评估写
 *       dq_rule_check → 重算资产评分（复用 T8 {@code DqScoreService.recomputeForAsset}）</li>
 *   <li>限流 — scope 级每日上限（dq_throttle，缺省 100），超限记 SKIP 日志提前返回防雪崩</li>
 *   <li>安全卡（铁律 2.4 #5）— 批执行完异步 {@code auditWrite(DQ_SCHEDULE_RUN, ...)}；
 *       规则参数 {@code DqSecurityService.maskParameters} 脱敏后再入评估上下文</li>
 * </ol>
 *
 * <p>限流阈值逻辑（{@link #checkThrottle}）：</p>
 * <pre>
 *   scope = (schedule.scopeType 缺省 DATASOURCE, schedule.scopeId 缺省 *)
 *   max   = dq_throttle.max_checks_per_day（无配置行按 DEFAULT_MAX_PER_DAY=100）
 *   cur   = dq_throttle.current_count（跨日由 SQL/Java 双重重置）
 *   cur &gt;= max → warn 日志 + 审计 THROTTLED → 提前返回 0
 * </pre>
 *
 * <p>Bean 名 {@code ecosDqScheduleService}（铁律 1.3 防多 Bean 冲突）。
 * implements 本波新建的 {@code DqScheduleService}（不与既有接口重名）。</p>
 *
 * @author PMO-48-C T11
 */
@Service("ecosDqScheduleService")
public class DqScheduleServiceImpl implements DqScheduleService {

    private static final Logger log = LoggerFactory.getLogger(DqScheduleServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STR_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** 触发类型常量（与 V113 注释同源） */
    public static final String TRIGGER_SCHEDULE = "SCHEDULE";
    public static final String TRIGGER_EVENT = "EVENT";
    public static final String TRIGGER_MANUAL = "MANUAL";

    /** 缺省限流上限（与 V113 dq_throttle.max_checks_per_day DEFAULT 一致） */
    static final int DEFAULT_MAX_PER_DAY = 100;

    /** 单条规则通过阈值：所有已评估维度分 >= 该值 → passed=true（0.90 介于 B/C 之间，过载 0.95 为 asset 级 A 级阈值） */
    static final double DIMENSION_PASS_THRESHOLD = 0.90D;

    private final DqScheduleMapper scheduleMapper;
    private final DqRuleMapper ruleMapper;
    private final DqScoreService scoreService;
    private final DqSecurityService securityService;
    private final DqAlertService alertService;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public DqScheduleServiceImpl(DqScheduleMapper scheduleMapper,
                                 DqRuleMapper ruleMapper,
                                 DqScoreService scoreService,
                                 DqSecurityService securityService,
                                 DqAlertService alertService,
                                 ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.scheduleMapper = scheduleMapper;
        this.ruleMapper = ruleMapper;
        this.scoreService = scoreService;
        this.securityService = securityService;
        this.alertService = alertService;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    // ==================== 1. CRUD ====================

    @Override
    @Transactional
    public String createSchedule(DqScheduleDTO dto) {
        validateDto(dto);
        DqScheduleVO vo = toVO(dto);
        String id = UUID.randomUUID().toString();
        scheduleMapper.insert(id, vo, "system");
        log.info("DqSchedule created: id={}, name={}, triggerType={}, rules={}",
                id, vo.getName(), vo.getTriggerType(), vo.getRuleIds() != null ? vo.getRuleIds().size() : 0);
        securityService.auditWrite("DQ_SCHEDULE_CREATE", id, "SUCCESS");
        return id;
    }

    @Override
    @Transactional
    public void updateSchedule(String id, DqScheduleDTO dto) {
        if (id == null || id.isBlank()) {
            throw new BusinessException("调度 ID 不能为空");
        }
        if (dto == null) {
            throw new BusinessException("DqScheduleDTO 不能为 null");
        }
        DqScheduleVO current = scheduleMapper.findById(id);
        if (current == null) {
            throw new NotFoundException("DQ 调度计划不存在: " + id);
        }
        // 合并策略：dto 非 null 字段覆盖 current（防裸 null 覆盖有效数据）
        DqScheduleVO merged = merge(dto, current);
        scheduleMapper.update(id, merged);
        log.info("DqSchedule updated: id={}, triggerType={}, rules={}",
                id, merged.getTriggerType(), merged.getRuleIds() != null ? merged.getRuleIds().size() : 0);
        securityService.auditWrite("DQ_SCHEDULE_UPDATE", id, "SUCCESS");
    }

    @Override
    @Transactional
    public void deleteSchedule(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException("调度 ID 不能为空");
        }
        int n = scheduleMapper.logicDelete(id);
        if (n == 0) {
            throw new NotFoundException("DQ 调度计划不存在: " + id);
        }
        log.info("DqSchedule deleted (logical): id={}", id);
        securityService.auditWrite("DQ_SCHEDULE_DELETE", id, "SUCCESS");
    }

    @Override
    public List<DqScheduleVO> listSchedules() {
        List<DqScheduleVO> rows = scheduleMapper.listAll();
        rows.forEach(this::parseRuleIds);
        securityService.auditRead("DQ_SCHEDULE_LIST", "batch");
        return rows;
    }

    @Override
    public DqScheduleVO getSchedule(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        DqScheduleVO vo = scheduleMapper.findById(id);
        if (vo == null) {
            return null;
        }
        parseRuleIds(vo);
        securityService.auditRead("DQ_SCHEDULE_DETAIL", id);
        return vo;
    }

    // ==================== 2. 触发入口 ====================

    @Override
    public void triggerManual(String scheduleId, String triggerBy) {
        if (scheduleId == null || scheduleId.isBlank()) {
            throw new BusinessException("调度 ID 不能为空");
        }
        String by = (triggerBy == null || triggerBy.isBlank()) ? "system" : triggerBy;
        log.info("DqSchedule manual trigger: id={}, by={}", scheduleId, by);
        int executed = runRuleBatch(scheduleId);
        securityService.auditWrite("DQ_SCHEDULE_MANUAL_TRIGGER", scheduleId, executed > 0 ? "SUCCESS" : "SKIPPED");
    }

    // ==================== 3. 核心批执行 ====================

    @Override
    public int runRuleBatch(String scheduleId) {
        if (scheduleId == null || scheduleId.isBlank()) {
            throw new BusinessException("调度 ID 不能为空");
        }
        DqScheduleVO schedule = scheduleMapper.findByIdForRun(scheduleId);
        if (schedule == null) {
            throw new NotFoundException("DQ 调度计划不存在或已删除: " + scheduleId);
        }
        parseRuleIds(schedule);
        if (schedule.getRuleIds() == null || schedule.getRuleIds().isEmpty()) {
            log.info("DqSchedule runRuleBatch 跳过: id={} 无关联规则", scheduleId);
            return 0;
        }

        // 1. 限流检查（scope 级，缺省 DATASOURCE:*）
        if (isThrottled(schedule)) {
            log.warn("DqSchedule runRuleBatch 限流跳过: id={}, scope={}:{} (max_per_day={})",
                    scheduleId, effectiveScopeType(schedule), effectiveScopeId(schedule),
                    effectiveMaxPerDay(effectiveScopeType(schedule), effectiveScopeId(schedule)));
            securityService.auditWrite("DQ_SCHEDULE_RUN", scheduleId, "THROTTLED");
            return 0;
        }

        // 2. 取 ACTIVE 规则（schedule.rule_ids 限定 + status='ACTIVE' AND is_deleted=FALSE）
        List<DqRuleVO> activeRules = loadActiveRules(schedule.getRuleIds());
        if (activeRules.isEmpty()) {
            log.info("DqSchedule runRuleBatch: id={} 无 ACTIVE 规则（关联 {} 个 ID）",
                    scheduleId, schedule.getRuleIds().size());
            securityService.auditWrite("DQ_SCHEDULE_RUN", scheduleId, "NO_ACTIVE_RULE");
            return 0;
        }

        // 3. 逐条评估 — per-rule try/catch 隔离，单条失败不中断整批
        String triggerType = normalizeTrigger(schedule.getTriggerType());
        int executed = 0;
        List<String> recomputeTargets = new ArrayList<>();
        for (DqRuleVO rule : activeRules) {
            try {
                if (evaluateOneRule(scheduleId, triggerType, rule)) {
                    executed++;
                    String tableId = (rule.getTargetId() != null && !rule.getTargetId().isBlank())
                            ? rule.getTargetId() : rule.getTargetTable();
                    if (tableId != null && !tableId.isBlank()) {
                        recomputeTargets.add(tableId);
                    }
                } else {
                    log.warn("DqSchedule 单条规则未通过: ruleId={}, ruleName={}", rule.getId(), rule.getRuleName());
                }
            } catch (Exception e) {
                log.error("DqSchedule 单条规则评估异常: ruleId={}, error={}", rule.getId(), e.getMessage(), e);
            }
        }

        // 4. 重算资产评分（复用 T8 DqScoreService → 写 dq_score_snapshot + dq_score_asset）
        for (String tableId : recomputeTargets) {
            try {
                scoreService.recomputeForAsset("TABLE", tableId);
            } catch (Exception e) {
                log.error("DqSchedule 资产评分重算失败: tableId={}, error={}", tableId, e.getMessage(), e);
            }
        }

        // 5. 异步审计（铁律 2.4 #5）
        securityService.auditWrite("DQ_SCHEDULE_RUN", scheduleId, executed > 0 ? "SUCCESS" : "SKIPPED");
        log.info("DqSchedule runRuleBatch 完成: id={}, trigger={}, 评估={}, 重算资产={}",
                scheduleId, triggerType, executed, recomputeTargets.size());
        return executed;
    }

    /**
     * 单条规则评估：组装脱敏上下文 → 调 DqScoreService.evaluateAll → 判通过 →
     * 写 dq_rule_check → 限流自增。
     *
     * @return true 表示成功完成评估并写 check 行（无论通过与否）
     */
    private boolean evaluateOneRule(String scheduleId, String triggerType, DqRuleVO rule) {
        String checkId = UUID.randomUUID().toString();
        long startMs = System.currentTimeMillis();
        ScoringContext ctx = buildContext(rule);

        // 调 T8 评分引擎（只算不写库，经 DqScoreService 暴露）
        Map<DqDimension, DimensionScore> scores;
        try {
            scores = scoreService.evaluateAll(ctx);
        } catch (RuntimeException e) {
            log.error("DqSchedule 评估器异常: ruleId={}, error={}", rule.getId(), e.getMessage(), e);
            writeCheckRow(checkId, scheduleId, rule.getId(), triggerType, false,
                    0L, 0L, 0.0D, "EVALUATOR_ERROR: " + maybeExSummary(e), 0,
                    (int) Math.min(System.currentTimeMillis() - startMs, 2147483647L));
            incrementThrottle(rule);
            return false;
        }

        // 判通过：所有已评估维度分 >= 阈值
        long hit = 0L;
        long pass = 0L;
        Map<DqDimension, DimensionScore> safeScores = scores != null ? scores : Map.of();
        for (DimensionScore s : safeScores.values()) {
            if (s == null) {
                continue;
            }
            hit++;
            if (s.getScoreValue() >= DIMENSION_PASS_THRESHOLD) {
                pass++;
            }
        }
        boolean passed = hit > 0 && (pass * 1.0D / hit) >= DIMENSION_PASS_THRESHOLD;
        double passRate = hit > 0 ? (pass * 1.0D / hit) : 0.0D;
        long latencyMs = System.currentTimeMillis() - startMs;

        writeCheckRow(checkId, scheduleId, rule.getId(), triggerType, passed,
                ctx.getTotalRows(), ctx.getFailedRows(), passRate,
                passed ? null : String.format("passRate=%.4f below threshold=%.2f", passRate, DIMENSION_PASS_THRESHOLD),
                (int) Math.min(ctx.getTotalRows(), 1000L),
                (int) Math.min(latencyMs, 2147483647L));
        incrementThrottle(rule);
        // T12: 未通过 → 调 DqAlertService 分发告警（内部 try/catch 隔离，单条失败不中断批）
        if (!passed) {
            dispatchAlert(rule, checkId);
        }
        return true;
    }

    /** T12 钩子：规则检查未通过时分发 DQ 告警（DqAlertService.dispatchOnRuleCheckFailed）。 */
    private void dispatchAlert(DqRuleVO rule, String checkId) {
        try {
            alertService.dispatchOnRuleCheckFailed(rule.getId(), checkId, scopeOf(rule), scopeIdOf(rule));
        } catch (RuntimeException e) {
            log.warn("DqSchedule 告警分发失败（已隔离）: ruleId={}, checkId={}, error={}",
                    rule.getId(), checkId, e.getMessage());
        }
    }

    /** 组装 ScoringContext（参数经 DqSecurityService.maskParameters 脱敏 — 铁律 2.4 #3）。 */
    private ScoringContext buildContext(DqRuleVO rule) {
        Map<String, Object> params = securityService.maskParameters(parseParamsJson(rule.getParametersJson()));
        ScoringContext ctx = new ScoringContext();
        ctx.setRuleId(rule.getId());
        ctx.setScopeType(scopeOf(rule));
        ctx.setScopeId(scopeIdOf(rule));
        ctx.setParameters(params);
        ctx.setConnectionConfig(new LinkedHashMap<>());
        ctx.setSampleFailures(new LinkedHashMap<>());
        ctx.setTotalRows(0L);
        ctx.setFailedRows(0L);
        ctx.setExecutedAt(LocalDateTime.now());
        ctx.setLastCheck(new LinkedHashMap<>());
        ctx.setTargetField(rule.getTargetField());
        return ctx;
    }

    /** 写 dq_rule_check（通过/未通过/评估失败统一语义）。 */
    private void writeCheckRow(String checkId, String scheduleId, String ruleId, String triggerType,
                               boolean passed, long totalRows, long failedRows, double passRate,
                               String errorMessage, int sampleSize, int latencyMs) {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            log.warn("DqSchedule 写 dq_rule_check 跳过（JdbcTemplate 不可用）: ruleId={}", ruleId);
            return;
        }
        try {
            jdbc.update(
                    "INSERT INTO ecos_dq.dq_rule_check" +
                    " (id, rule_id, schedule_id, trigger_type, executed_at, passed, total_rows, failed_rows," +
                    "  pass_rate, latency_ms, error_message, sample_size, sample_failures)" +
                    " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                    checkId, ruleId, scheduleId, triggerType, passed,
                    totalRows, failedRows, passRate, latencyMs,
                    errorMessage, sampleSize, "{}");
        } catch (RuntimeException e) {
            log.error("DqSchedule 写 dq_rule_check 失败: ruleId={}, error={}", ruleId, e.getMessage(), e);
        }
    }

    /** 限流自增（scope 维度跨日自动 reset，SQL upsert 语义）。 */
    private void incrementThrottle(DqRuleVO rule) {
        String scopeType = scopeOf(rule);
        String scopeId = scopeIdOf(rule);
        int max = effectiveMaxPerDay(scopeType, scopeId);
        try {
            scheduleMapper.incrementThrottle(throttleId(scopeType, scopeId), scopeType, scopeId, max);
        } catch (RuntimeException e) {
            log.warn("DqSchedule 限流自增失败（不影响主流程）: scope={}:{}, error={}",
                    scopeType, scopeId, e.getMessage());
        }
    }

    // ==================== 4. 限流判定 ====================

    /** scope 是否已超限（max = dq_throttle.max_checks_per_day，cur >= max → true）。 */
    private boolean isThrottled(DqScheduleVO schedule) {
        String scopeType = effectiveScopeType(schedule);
        String scopeId = effectiveScopeId(schedule);
        DqThrottleRow row = scheduleMapper.findThrottle(scopeType, scopeId);
        if (row == null) {
            return false; // 未配置限流行 → 首次执行由 incrementThrottle 初始化
        }
        int max = row.getMaxPerDay() != null ? row.getMaxPerDay() : DEFAULT_MAX_PER_DAY;
        int cur = row.getCurrentCount() != null ? row.getCurrentCount() : 0;
        if (row.getResetAt() != null && row.getResetAt().toLocalDate().isBefore(LocalDate.now())) {
            cur = 0; // 跨日 Java 侧兜底（SQL upsert 内也有同等判定）
        }
        return cur >= max;
    }

    /** 取 scope 当前限流上限（无配置行 → DEFAULT_MAX_PER_DAY）。 */
    private int effectiveMaxPerDay(String scopeType, String scopeId) {
        DqThrottleRow row = scheduleMapper.findThrottle(scopeType, scopeId);
        if (row == null || row.getMaxPerDay() == null) {
            return DEFAULT_MAX_PER_DAY;
        }
        return row.getMaxPerDay();
    }

    /** 限流行 ID（scopeType:scopeId 截断到 64 对齐表 PK）。 */
    private static String throttleId(String scopeType, String scopeId) {
        String raw = scopeType + ":" + scopeId;
        return raw.length() > 64 ? raw.substring(0, 64) : raw;
    }

    // ==================== 5. 私有工具 ====================

    /** 校验入参（必填 + 触发类型一致性）。 */
    private void validateDto(DqScheduleDTO dto) {
        if (dto == null) {
            throw new BusinessException("DqScheduleDTO 不能为 null");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException("name 不能为空");
        }
        if (dto.getTriggerType() == null || dto.getTriggerType().isBlank()) {
            throw new BusinessException("triggerType 不能为空");
        }
        String tt = dto.getTriggerType().trim().toUpperCase();
        if (!TRIGGER_SCHEDULE.equals(tt) && !TRIGGER_EVENT.equals(tt) && !TRIGGER_MANUAL.equals(tt)) {
            throw new BusinessException("triggerType 仅支持 SCHEDULE/EVENT/MANUAL");
        }
        if (TRIGGER_SCHEDULE.equals(tt)
                && (dto.getCronExpression() == null || dto.getCronExpression().isBlank())) {
            throw new BusinessException("SCHEDULE 触发类型必须提供 cronExpression");
        }
        if (TRIGGER_EVENT.equals(tt)
                && (dto.getEventType() == null || dto.getEventType().isBlank())) {
            throw new BusinessException("EVENT 触发类型必须提供 eventType");
        }
    }

    /** DTO → VO（DB 审计字段留空，insert 时 Service 统一赋 createTime）。 */
    private DqScheduleVO toVO(DqScheduleDTO dto) {
        DqScheduleVO vo = new DqScheduleVO();
        vo.setName(dto.getName());
        vo.setTriggerType(dto.getTriggerType().trim().toUpperCase());
        vo.setCronExpression(dto.getCronExpression());
        vo.setEventType(dto.getEventType());
        vo.setRuleIds(dto.getRuleIds());
        vo.setScopeType(dto.getScopeType() != null && !dto.getScopeType().isBlank()
                ? dto.getScopeType().trim().toUpperCase() : "DATASOURCE");
        vo.setScopeId(dto.getScopeId() != null && !dto.getScopeId().isBlank() ? dto.getScopeId() : "*");
        vo.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        vo.setMaxRuntimeSeconds(dto.getMaxRuntimeSeconds() != null ? dto.getMaxRuntimeSeconds() : 60);
        return vo;
    }

    /** 合并更新：dto 非 null 覆盖 current，null 保留 current（防 null 覆盖有效数据）。 */
    private DqScheduleVO merge(DqScheduleDTO dto, DqScheduleVO current) {
        DqScheduleVO m = new DqScheduleVO();
        m.setId(current.getId());
        m.setName(dto.getName() != null && !dto.getName().isBlank() ? dto.getName() : current.getName());
        m.setTriggerType(dto.getTriggerType() != null && !dto.getTriggerType().isBlank()
                ? dto.getTriggerType().trim().toUpperCase() : current.getTriggerType());
        m.setCronExpression(dto.getCronExpression() != null ? dto.getCronExpression() : current.getCronExpression());
        m.setEventType(dto.getEventType() != null ? dto.getEventType() : current.getEventType());
        m.setRuleIds(dto.getRuleIds() != null ? dto.getRuleIds() : current.getRuleIds());
        m.setScopeType(dto.getScopeType() != null && !dto.getScopeType().isBlank()
                ? dto.getScopeType().trim().toUpperCase() : current.getScopeType());
        m.setScopeId(dto.getScopeId() != null && !dto.getScopeId().isBlank()
                ? dto.getScopeId() : current.getScopeId());
        m.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : current.getEnabled());
        m.setMaxRuntimeSeconds(dto.getMaxRuntimeSeconds() != null
                ? dto.getMaxRuntimeSeconds() : current.getMaxRuntimeSeconds());
        return m;
    }

    /** 取 ACTIVE 规则（schedule.rule_ids 限定，status='ACTIVE' AND is_deleted=FALSE）。 */
    private List<DqRuleVO> loadActiveRules(List<String> ruleIds) {
        List<DqRuleVO> result = new ArrayList<>();
        if (ruleIds == null || ruleIds.isEmpty()) {
            return result;
        }
        // 规则数受限流上限约束（≤ 100/天），逐 ID 查可接受；如需批量可后续换 listByIds
        for (String rid : ruleIds) {
            try {
                DqRuleVO r = ruleMapper.findById(rid);
                if (r != null && "ACTIVE".equalsIgnoreCase(r.getStatus())) {
                    result.add(r);
                }
            } catch (RuntimeException e) {
                log.warn("DqSchedule 查规则失败: ruleId={}, error={}", rid, e.getMessage());
            }
        }
        return result;
    }

    /** 规则 scope 维度（target_kind 透传，缺省 TABLE）。 */
    private String scopeOf(DqRuleVO rule) {
        String kind = rule.getTargetKind();
        return (kind == null || kind.isBlank()) ? "TABLE" : kind.trim().toUpperCase();
    }

    /** 规则 scopeId（FIELD=table:field；其余=target_id 或 target_table，缺省 *）。 */
    private String scopeIdOf(DqRuleVO rule) {
        String kind = rule.getTargetKind() == null ? "" : rule.getTargetKind().toUpperCase();
        if ("FIELD".equals(kind)) {
            String t = rule.getTargetTable() != null ? rule.getTargetTable() : "";
            String f = rule.getTargetField() != null ? rule.getTargetField() : "";
            return t + ":" + f;
        }
        if (rule.getTargetId() != null && !rule.getTargetId().isBlank()) {
            return rule.getTargetId();
        }
        if (rule.getTargetTable() != null && !rule.getTargetTable().isBlank()) {
            return rule.getTargetTable();
        }
        return "*";
    }

    /** schedule 有效 scope 类型（缺省 DATASOURCE）。 */
    private static String effectiveScopeType(DqScheduleVO s) {
        return s.getScopeType() != null && !s.getScopeType().isBlank() ? s.getScopeType() : "DATASOURCE";
    }

    /** schedule 有效 scope ID（缺省 *）。 */
    private static String effectiveScopeId(DqScheduleVO s) {
        return s.getScopeId() != null && !s.getScopeId().isBlank() ? s.getScopeId() : "*";
    }

    /** 归一化触发类型（DB 容忍小写/空）。 */
    private static String normalizeTrigger(String t) {
        if (t == null || t.isBlank()) {
            return TRIGGER_MANUAL;
        }
        String up = t.trim().toUpperCase();
        if (TRIGGER_SCHEDULE.equals(up) || TRIGGER_EVENT.equals(up) || TRIGGER_MANUAL.equals(up)) {
            return up;
        }
        return TRIGGER_MANUAL;
    }

    /** 解析 schedule.rule_ids JSON 文本 → List&lt;String&gt; 覆盖 VO.ruleIds（读空容错）。 */
    private void parseRuleIds(DqScheduleVO vo) {
        if (vo == null) {
            return;
        }
        String json = extractRuleIdsJson(vo);
        if (json == null || json.isBlank()) {
            if (vo.getRuleIds() == null) {
                vo.setRuleIds(new ArrayList<>());
            }
            return;
        }
        try {
            vo.setRuleIds(MAPPER.readValue(json, STR_LIST_TYPE));
        } catch (JsonProcessingException e) {
            log.warn("DqSchedule rule_ids 解析失败: id={}, error={}", vo.getId(), e.getMessage());
            vo.setRuleIds(new ArrayList<>());
        }
    }

    /**
     * 从 VO 取 rule_ids JSON 原文。
     * <p>说明：Mapper SQL 用 {@code rule_ids::text AS ruleIdsJson} 读原始 JSON 文本；
     * 但 VO 未声明承接该列的字段（Lombok @Data 仅 {@code List<String> ruleIds}），
     * MyBatis 默认 StringTypeHandler 把 JSON 文本读入 List&lt;String&gt; 会静默失败 →
     * 因此此处统一走 DB 回查保证 rule_ids 内容可取。</p>
     */
    private String extractRuleIdsJson(DqScheduleVO vo) {
        // MyBatis 在 VO 没有 ruleIdsJson 字段时，rule_ids::text → ruleIds 映射会因 JSON 文本
        // 不是 List 结构而跳过；这里统一走 DB 回查以保证 rule_ids 内容可取
        if (vo.getId() == null) {
            return null;
        }
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            return null;
        }
        try {
            List<String> rows = jdbc.query(
                    "SELECT rule_ids::text FROM ecos_dq.dq_schedule WHERE id = ? AND is_deleted = FALSE",
                    (rs, i) -> rs.getString(1),
                    vo.getId());
            return rows.isEmpty() ? null : rows.get(0);
        } catch (RuntimeException e) {
            log.warn("DqSchedule 回查 rule_ids 失败: id={}, error={}", vo.getId(), e.getMessage());
            return null;
        }
    }

    /** 解析规则参数 JSONB 文本为 Map（解析失败返回空 Map，不阻断）。 */
    private Map<String, Object> parseParamsJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("DqSchedule parameters 解析失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /** 异常摘要（截断 200 字符，避免敏感原文进 check 行）。 */
    private static String maybeExSummary(Throwable t) {
        String msg = t == null ? "UNKNOWN" : t.getMessage();
        if (msg == null) {
            return t.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}
