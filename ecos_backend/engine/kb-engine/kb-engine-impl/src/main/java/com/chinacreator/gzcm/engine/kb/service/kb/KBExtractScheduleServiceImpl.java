package com.chinacreator.gzcm.engine.kb.service.kb;

import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.dto.ScheduledExtractCreatedVO;
import com.chinacreator.gzcm.engine.kb.dto.ScheduledExtractRequest;
import com.chinacreator.gzcm.engine.kb.dto.ScheduledExtractVO;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 结构化（映射驱动）实例抽取任务调度服务实现 — TB-1 批次（PMO-73 W3）。
 *
 * <p>取代原 {@code ScheduledExtractController} 的私有 {@code scheduleTaskByPeriod /
 * triggerNow / immediate / periodic} 自造定时器逻辑（V141 形态，参考基线）。</p>
 *
 * <p>铁律：</p>
 * <ul>
 *   <li>§1.6-2 — 弃自建 {@code *_scheduled_*} 表，调度元信息单事实源 =
 *       {@code td_runtime_task_plan}（V152 W4 持久化）</li>
 *   <li>§2.5-3 — 调度全部委托 runtime-task，不另起 {@code ScheduledExecutorService}</li>
 *   <li>§3.1 — kb_scheduled_extract 表只加不删（V153 标 deprecate，仅 fallback 镜像行）</li>
 * </ul>
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>{@link #create} — 拼 6 位 Spring Cron → {@code taskSchedulerService.scheduleTask(desc, cron)}
 *       拿 scheduleId → 仅 INSERT kb_scheduled_extract 单向镜像行（schedule_id=scheduleId,
 *       prefers_runtime_task='td_runtime_task_plan'）</li>
 *   <li>{@link #list} — 主读 td_runtime_task_plan（按 task_type='KB_IMPORT_SCHEDULED'），
 *       fallback 合并旧 kb_scheduled_extract 行（仅 list 调用器允许回退 UI 显示）</li>
 *   <li>{@link #update} / {@link #delete} — 直接 UPDATE td_runtime_task_plan；
 *       delete 走 {@code is_deleted=1}（逻辑删除）</li>
 *   <li>{@link #pause} / {@link #resume} — td_runtime_task_plan.task_status ∈ {RUNNING, PAUSED}</li>
 *   <li>{@link #triggerNow} / {@link #triggerPeriodic} — 同 {@code StructuredExtractController 异步路径}
 *       形态：{@code submitTask(desc) + executeTask(taskId)}，回真实 taskId</li>
 * </ul>
 */
@Service
public class KBExtractScheduleServiceImpl implements IKBExtractScheduleService {

    private static final Logger log = LoggerFactory.getLogger(KBExtractScheduleServiceImpl.class);

    /** 默认抽取模式（与 K1 批次一致）。 */
    private static final String DEFAULT_MODE = "INCREMENTAL";

    /** 抽取任务 taskType（定时型独有，区分于 K1 同步型 KB_IMPORT）。 */
    private static final String KB_IMPORT_SCHEDULED_TASK_TYPE = "KB_IMPORT_SCHEDULED";

    /** 业务类别标记（铁律 §1.6-2 biz_kind 列） */
    private static final String BIZ_KIND_KB_EXTRACT = "KB_EXTRACT";

    /** 旧 fallback 镜像表（V141，已 deprecate）。 */
    private static final String LEGACY_TABLE = "ecos_knowledge.kb_scheduled_extract";

    /** runtime-task plan 事实源表（V152 W4 持久化）。 */
    private static final String PLAN_TABLE = "td_runtime_task_plan";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final TaskSchedulerService taskSchedulerService;
    private final ITaskManagementService taskManagementService;

    /** 构造器注入（禁 @Autowired 字段注入 — 铁律 §1.1）。 */
    public KBExtractScheduleServiceImpl(JdbcTemplate jdbc,
                                        TaskSchedulerService taskSchedulerService,
                                        ITaskManagementService taskManagementService) {
        this.jdbc = jdbc;
        this.taskSchedulerService = taskSchedulerService;
        this.taskManagementService = taskManagementService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // create / list / update / delete
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ScheduledExtractCreatedVO create(ScheduledExtractRequest req) {
        validateBase(req);
        String period = normalizePeriod(req.getPeriod());
        String timeOfDay = normalizeTimeOfDay(req.getTimeOfDay(), period);
        String mode = normalizeMode(req.getMode());
        String cron = buildCron(period, timeOfDay);
        Timestamp nextRunAt = computeNextRun(cron);

        // 1) 注册到 runtime-task（事实源 td_runtime_task_plan）
        TaskDescription desc = buildScheduledDesc(req.getName(), req.getOntologyIds(), mode, period, cron);
        String scheduleId;
        try {
            scheduleId = taskSchedulerService.scheduleTask(desc, cron);
        } catch (Exception e) {
            log.error("TB-1 定时任务注册失败 name={}: {}", req.getName(), e.getMessage(), e);
            throw new ValidationException("定时任务注册失败: " + e.getMessage());
        }
        if (scheduleId == null || scheduleId.isBlank()) {
            throw new ValidationException("runtime-task 未返回 scheduleId");
        }

        // 2) fallback 镜像行（kb_scheduled_extract.schedule_id=scheduleId,
        //    prefers_runtime_task='td_runtime_task_plan'）。失败 warn 不阻塞主流程。
        Long mirrorId = insertLegacyMirror(scheduleId, req, mode, period, cron, nextRunAt);

        log.info("TB-1 定时任务已创建: mirrorId={} scheduleId={} period={} cron={} nextRunAt={}",
                mirrorId, scheduleId, period, cron, nextRunAt.toLocalDateTime());

        ScheduledExtractCreatedVO out = new ScheduledExtractCreatedVO();
        out.setId(mirrorId == null ? 0L : mirrorId);
        out.setScheduleId(scheduleId);
        out.setNextRunAt(nextRunAt.toLocalDateTime().toString());
        return out;
    }

    @Override
    public List<ScheduledExtractVO> list(int pageNum, int pageSize, Boolean enabled) {
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        int offset = (safePageNum - 1) * safePageSize;

        // 1) 主源：td_runtime_task_plan（task_type='KB_IMPORT_SCHEDULED'）
        List<ScheduledExtractVO> out = new ArrayList<>();
        try {
            String enabledSql = enabled == null ? "" : (enabled ? " AND task_status = 'RUNNING'" : " AND task_status = 'PAUSED'");
            String totalSql = "SELECT COUNT(*) AS c FROM " + PLAN_TABLE +
                    " WHERE task_type = ? AND is_deleted = 0" + enabledSql;
            Number cnt = jdbc.queryForObject(totalSql, Number.class, KB_IMPORT_SCHEDULED_TASK_TYPE);
            int total = cnt == null ? 0 : cnt.intValue();
            String sql = "SELECT task_id, task_name, task_type, cron_expression, next_run_at, "
                    + "last_run_at, last_status, task_status, parameters, created_by, create_time "
                    + "FROM " + PLAN_TABLE +
                    " WHERE task_type = ? AND is_deleted = 0 " + enabledSql +
                    " ORDER BY create_time DESC LIMIT ? OFFSET ?";
            List<Map<String, Object>> primaryRows = jdbc.queryForList(sql,
                    KB_IMPORT_SCHEDULED_TASK_TYPE, safePageSize, offset);
            for (Map<String, Object> row : primaryRows) {
                out.add(toVoFromPlanRow(row));
            }
            if (total > out.size()) {
                // 部分页：本页 page 全从主源读，超出 total 部分走旧表 fallback
                int complement = total - out.size();
                out.addAll(queryLegacyFallback(enabled, complement, offset));
            }
        } catch (DataAccessException e) {
            log.warn("TB-1 主源查询失败，回退旧表: {}", e.getMessage());
            out.clear();
            out.addAll(queryLegacyFallback(enabled, safePageSize, offset));
        }
        return out;
    }

    @Override
    @Transactional
    public ScheduledExtractVO update(String scheduleId, ScheduledExtractRequest req) {
        validateBase(req);
        if (scheduleId == null || scheduleId.isBlank()) {
            throw new ValidationException("scheduleId 不能为空");
        }
        String period = normalizePeriod(req.getPeriod());
        String timeOfDay = normalizeTimeOfDay(req.getTimeOfDay(), period);
        String mode = normalizeMode(req.getMode());
        String cron = buildCron(period, timeOfDay);
        boolean enabledNow = req.getEnabled() == null || req.getEnabled();

        // 1) 取现有 plan 行（旧 scheduleId 可能在主源或 fallback）
        Map<String, Object> current = fetchPlanRow(scheduleId);
        boolean foundInPlan = current != null;
        String oldCron = foundInPlan ? asStr(current.get("cron_expression")) : null;
        boolean cronChanged = !cron.equals(oldCron);

        // 2) 若 cron/name/ontologyIds 变 → cancelSchedule(旧) + re-register(新)
        if (cronChanged) {
            try {
                taskSchedulerService.cancelSchedule(scheduleId);
            } catch (Exception e) {
                log.warn("TB-1 update cancelSchedule 失败（忽略）: {} for scheduleId={}",
                        e.getMessage(), scheduleId);
            }
            TaskDescription desc = buildScheduledDesc(req.getName(), req.getOntologyIds(), mode, period, cron);
            scheduleId = taskSchedulerService.scheduleTask(desc, cron);
        }

        // 3) 启用/禁用 → plan.task_status ∈ {RUNNING, PAUSED}
        if (foundInPlan) {
            jdbc.update("UPDATE " + PLAN_TABLE
                    + " SET task_name = ?, parameters = ?::jsonb, task_status = ? "
                    + " WHERE task_id = ? AND is_deleted = 0",
                    req.getName(),
                    toJsonString(req.getOntologyIds()),
                    enabledNow ? "RUNNING" : "PAUSED",
                    scheduleId);
        }

        // 4) 回读最新行
        Map<String, Object> updated = fetchPlanRow(scheduleId);
        if (updated == null) {
            // fallback：plan 不存在时从旧表回读
            List<ScheduledExtractVO> legacy = queryLegacyFallback(null, 1, 0);
            if (!legacy.isEmpty()) {
                return legacy.get(0);
            }
            return null;
        }
        return toVoFromPlanRow(updated);
    }

    @Override
    @Transactional
    public void delete(String scheduleId) {
        if (scheduleId == null || scheduleId.isBlank()) {
            throw new ValidationException("scheduleId 不能为空");
        }
        // plan 事实源：逻辑删除（R9 软删，is_deleted=1）
        jdbc.update("UPDATE " + PLAN_TABLE
                + " SET is_deleted = 1, update_time = NOW() "
                + " WHERE task_id = ? AND is_deleted = 0",
                scheduleId);
        // runtime-task 取消调度（忽略已删）
        try {
            taskSchedulerService.cancelSchedule(scheduleId);
        } catch (Exception e) {
            log.warn("TB-1 delete 取消调度失败（忽略）: {} for scheduleId={}", e.getMessage(), scheduleId);
        }
        // fallback 旧表同步软删（无该行则 0 行）
        try {
            jdbc.update("UPDATE " + LEGACY_TABLE
                    + " SET is_deleted = 1, updated_at = NOW() WHERE schedule_id = ?",
                    scheduleId);
        } catch (DataAccessException e) {
            log.warn("TB-1 delete 回退旧表软删失败（忽略）: {}", e.getMessage());
        }
        log.info("TB-1 定时任务已软删: scheduleId={}", scheduleId);
    }

    @Override
    public void pause(String scheduleId) {
        if (scheduleId == null || scheduleId.isBlank()) {
            throw new ValidationException("scheduleId 不能为空");
        }
        int n = jdbc.update("UPDATE " + PLAN_TABLE
                + " SET task_status = 'PAUSED', update_time = NOW() "
                + " WHERE task_id = ? AND is_deleted = 0", scheduleId);
        if (n == 0) {
            log.warn("TB-1 pause: 未命中 plan 行 scheduleId={}（不在主源或已软删）", scheduleId);
        }
        // 旧 fallback 行同步（幂等）
        safeUpdateLegacy(scheduleId, "is_paused", "1");
    }

    @Override
    public void resume(String scheduleId) {
        if (scheduleId == null || scheduleId.isBlank()) {
            throw new ValidationException("scheduleId 不能为空");
        }
        int n = jdbc.update("UPDATE " + PLAN_TABLE
                + " SET task_status = 'RUNNING', update_time = NOW() "
                + " WHERE task_id = ? AND is_deleted = 0", scheduleId);
        if (n == 0) {
            log.warn("TB-1 resume: 未命中 plan 行 scheduleId={}（不在主源或已软删）", scheduleId);
        }
        safeUpdateLegacy(scheduleId, "is_paused", "0");
    }

    // ─────────────────────────────────────────────────────────────────────
    // triggerNow / triggerPeriodic — 同 runOnce 形态 submitTask + executeTask
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public String triggerNow(String scheduleId) {
        return submitAndExecute(scheduleId, "triggerNow");
    }

    @Override
    public String triggerPeriodic(String scheduleId) {
        return submitAndExecute(scheduleId, "triggerPeriodic");
    }

    /** 共用 trigger 路径：构造 desc → submitTask → executeTask（同步派发）。 */
    private String submitAndExecute(String scheduleId, String op) {
        if (scheduleId == null || scheduleId.isBlank()) {
            throw new ValidationException("scheduleId 不能为空");
        }
        Map<String, Object> plan = fetchPlanRow(scheduleId);
        if (plan == null) {
            throw new ValidationException("未找到 scheduleId 对应 plan 行: " + scheduleId);
        }
        String mode = asStr(plan.get("task_name"));
        String cron = asStr(plan.get("cron_expression"));
        String desc = "verbatim";
        if (plan.get("description") != null) {
            desc = asStr(plan.get("description"));
        }
        TaskDescription d = buildTriggerDesc(scheduleId, mode, cron, desc);
        String taskId;
        try {
            taskId = taskManagementService.submitTask(d);
            taskManagementService.executeTask(taskId);
        } catch (Exception e) {
            log.error("TB-1 {} 提交失败 scheduleId={}: {}", op, scheduleId, e.getMessage(), e);
            throw new ValidationException(op + " 提交失败: " + e.getMessage());
        }
        log.info("TB-1 {} 已提交: taskId={} scheduleId={}", op, taskId, scheduleId);
        return taskId;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 内部辅助
    // ─────────────────────────────────────────────────────────────────────

    /** base 校验 — 名称非空。 */
    private static void validateBase(ScheduledExtractRequest req) {
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new ValidationException("任务名称不能为空");
        }
    }

    /** period ∈ {DAILY, WEEKLY, MONTHLY}，默认 DAILY。 */
    private static String normalizePeriod(String raw) {
        if (raw == null || raw.isBlank()) {
            return "DAILY";
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if ("DAILY".equals(value) || "WEEKLY".equals(value) || "MONTHLY".equals(value)) {
            return value;
        }
        throw new ValidationException("调度周期非法: period=" + raw + "（允许 DAILY / WEEKLY / MONTHLY）");
    }

    /** timeOfDay "HH:mm" 校验（默认 08:00）。 */
    private static String normalizeTimeOfDay(String raw, String period) {
        if (raw == null || raw.isBlank()) {
            return "08:00";
        }
        String trimmed = raw.trim();
        if (!trimmed.matches("\\d{2}:\\d{2}")) {
            throw new ValidationException("触发时刻格式非法: timeOfDay=" + raw + "（需 HH:mm）");
        }
        int hour = Integer.parseInt(trimmed.substring(0, 2));
        int minute = Integer.parseInt(trimmed.substring(3, 5));
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new ValidationException("触发时刻超出范围: timeOfDay=" + raw);
        }
        return trimmed;
    }

    /** 抽取模式校验（FULL / INCREMENTAL，默认 INCREMENTAL）。 */
    private static String normalizeMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MODE;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (DEFAULT_MODE.equals(value) || "FULL".equals(value)) {
            return value;
        }
        throw new ValidationException("抽取模式非法: mode=" + raw + "（允许 FULL / INCREMENTAL）");
    }

    /**
     * 拼 6 位 Spring Cron：
     * <ul>
     *   <li>DAILY → {@code 0 mm HH * * ?}</li>
     *   <li>WEEKLY → {@code 0 mm HH ? * MON}</li>
     *   <li>MONTHLY → {@code 0 mm HH 1 * ?}</li>
     * </ul>
     */
    private static String buildCron(String period, String timeOfDay) {
        int hour = Integer.parseInt(timeOfDay.substring(0, 2));
        int minute = Integer.parseInt(timeOfDay.substring(3, 5));
        switch (period) {
            case "WEEKLY":
                return String.format("0 %d %d ? * MON", minute, hour);
            case "MONTHLY":
                return String.format("0 %d %d 1 * ?", minute, hour);
            case "DAILY":
            default:
                return String.format("0 %d %d * * ?", minute, hour);
        }
    }

    /** cron 校验 + 计算下次运行时点（必须 ZonedDateTime，Instant 不支持 DayOfWeek 字段）。 */
    private static Timestamp computeNextRun(String cron) {
        CronExpression parsed;
        try {
            parsed = CronExpression.parse(cron);
        } catch (Exception e) {
            throw new ValidationException("cron 表达式非法: " + cron + "（" + e.getMessage() + "）");
        }
        ZonedDateTime next = parsed.next(ZonedDateTime.now());
        if (next == null) {
            throw new ValidationException("无法计算下次运行时间: " + cron);
        }
        return Timestamp.from(next.toInstant());
    }

    /** 构造定时调度 TaskDescription（taskType=KB_IMPORT_SCHEDULED, biz_kind=KB_EXTRACT）。 */
    private static TaskDescription buildScheduledDesc(String name, List<String> ontologyIds,
                                                       String mode, String period, String cron) {
        TaskDescription d = new TaskDescription();
        d.setTaskName("kb_scan_scheduled:" + name);
        d.setTaskType(KB_IMPORT_SCHEDULED_TASK_TYPE);
        d.setDescription(String.format("cron=%s period=%s biz_kind=%s", cron, period, BIZ_KIND_KB_EXTRACT));
        Map<String, Object> params = new HashMap<>();
        params.put("ontologyId", ontologyIds == null || ontologyIds.isEmpty()
                ? null : ontologyIds.get(0));
        params.put("ontologyIds", ontologyIds == null ? Collections.emptyList() : ontologyIds);
        params.put("mode", mode);
        params.put("dryRun", false);
        params.put("biz_kind", BIZ_KIND_KB_EXTRACT);
        params.put("scheduled", true);
        d.setParameters(params);
        d.setAsync(true);
        d.setCreatedBy("kb-scheduled-tb1");
        return d;
    }

    /** 构造 trigger 一次性 TaskDescription（与 StructuredExtractController 异步路径同名）。 */
    private static TaskDescription buildTriggerDesc(String scheduleId, String mode, String cron,
                                                     String description) {
        TaskDescription d = new TaskDescription();
        d.setTaskId(scheduleId + "-run-" + System.currentTimeMillis());
        d.setTaskName("kb_scan_once:" + scheduleId);
        d.setTaskType(KB_IMPORT_SCHEDULED_TASK_TYPE);
        d.setDescription(description == null || description.isBlank() ? "manual trigger" : description);
        Map<String, Object> params = new HashMap<>();
        params.put("mode", mode == null ? DEFAULT_MODE : mode);
        params.put("dryRun", false);
        params.put("biz_kind", BIZ_KIND_KB_EXTRACT);
        params.put("scheduled", false);
        params.put("fromSchedule", scheduleId);
        d.setParameters(params);
        d.setAsync(false);   // 即时同步派发
        d.setCreatedBy("kb-scheduled-tb1");
        return d;
    }

    /** 单事实源：读 td_runtime_task_plan 行（含 is_deleted 过滤）。 */
    private Map<String, Object> fetchPlanRow(String scheduleId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT task_id, task_name, task_type, cron_expression, next_run_at, "
                            + "last_run_at, last_status, task_status, parameters, description, "
                            + "created_by, create_time FROM " + PLAN_TABLE
                            + " WHERE task_id = ? AND is_deleted = 0",
                    scheduleId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (DataAccessException e) {
            log.warn("TB-1 读 plan 行失败 scheduleId={}: {}", scheduleId, e.getMessage());
            return null;
        }
    }

    /** 旧 fallback：旧表硬删镜像查询（仅淘宝 UI 默认列表使用，header flag 可短路）。 */
    private List<ScheduledExtractVO> queryLegacyFallback(Boolean enabled, int limit, int offset) {
        try {
            String enabledSql = enabled == null ? "" : (enabled ? " AND enabled = 1" : " AND enabled = 0");
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, schedule_id, name, "
                            + "convert_from(ontology_ids::bytea,'UTF8') AS ontology_ids, "
                            + "mode, period, cron_expression, next_run_at, enabled, last_run_at, "
                            + "last_status, created_at FROM " + LEGACY_TABLE
                            + " WHERE is_deleted = 0" + enabledSql
                            + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                    limit, offset);
            List<ScheduledExtractVO> out = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                out.add(toVoFromLegacyRow(row));
            }
            return out;
        } catch (DataAccessException e) {
            log.warn("TB-1 旧表 fallback 查询失败（忽略）: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** fallback 旧表写（幂等，失败 warn）。 */
    private void safeUpdateLegacy(String scheduleId, String column, String value) {
        try {
            jdbc.update("UPDATE " + LEGACY_TABLE + " SET " + column + " = ? WHERE schedule_id = ?",
                    value, scheduleId);
        } catch (DataAccessException e) {
            log.warn("TB-1 旧表同步失败（忽略）: scheduleId={} {}: {}", scheduleId, column, e.getMessage());
        }
    }

    /** 落旧镜像行（单向 fallback，失败 warn 不阻塞主流程）。 */
    private Long insertLegacyMirror(String scheduleId, ScheduledExtractRequest req,
                                    String mode, String period, String cron, Timestamp nextRunAt) {
        try {
            Number returned = jdbc.queryForObject(
                    "INSERT INTO " + LEGACY_TABLE
                            + " (schedule_id, name, ontology_ids, mode, period, cron_expression, "
                            + "next_run_at, enabled, created_by, prefers_runtime_task) "
                            + "VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, 1, 'kb-scheduled-tb1', 'td_runtime_task_plan') "
                            + "ON CONFLICT (schedule_id) DO UPDATE SET "
                            + " name = EXCLUDED.name, mode = EXCLUDED.mode, period = EXCLUDED.period, "
                            + " cron_expression = EXCLUDED.cron_expression, "
                            + " next_run_at = EXCLUDED.next_run_at, updated_at = NOW(), "
                            + " ontology_ids = EXCLUDED.ontology_ids "
                            + " RETURNING id",
                    Long.class, scheduleId, req.getName(),
                    toJsonString(req.getOntologyIds()), mode, period, cron, nextRunAt);
            return returned == null ? null : returned.longValue();
        } catch (DataAccessException e) {
            log.warn("TB-1 INSERT 旧表失败（不阻塞主流程）: scheduleId={} err={}",
                    scheduleId, e.getMessage());
            return null;
        }
    }

    /** plan 行 → VO（来自主源 td_runtime_task_plan）。 */
    private ScheduledExtractVO toVoFromPlanRow(Map<String, Object> row) {
        ScheduledExtractVO vo = new ScheduledExtractVO();
        vo.setScheduleId(row.get("task_id") == null ? null : String.valueOf(row.get("task_id")));
        vo.setName(row.get("task_name") == null ? null : String.valueOf(row.get("task_name")));
        vo.setMode(row.get("mode") == null ? DEFAULT_MODE : asStr(row.get("mode")));

        // ontologyIds 从 parameters JSON 解
        Object params = row.get("parameters");
        List<String> ontologyIds = parseOntologyIdsFromParams(params);
        vo.setOntologyIds(ontologyIds);

        // period/timeOfDay 从 cron 反解
        String cronExpr = row.get("cron_expression") == null ? null : String.valueOf(row.get("cron_expression"));
        if (cronExpr != null) {
            String[] parts = cronExpr.trim().split("\\s+");
            if (parts.length >= 6) {
                try {
                    vo.setTimeOfDay(String.format("%02d:%02d",
                            Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[1])));
                    // period 粗判：含 "1" → MONTHLY；含 "MON" → WEEKLY；否则 DAILY
                    if (parts[3].startsWith("1") && parts[5].equals("?")) {
                        vo.setPeriod("MONTHLY");
                    } else if ("MON".equals(parts[5])) {
                        vo.setPeriod("WEEKLY");
                    } else {
                        vo.setPeriod("DAILY");
                    }
                } catch (NumberFormatException ignore) {
                    // 主源 cron 解析失败保持 null
                }
            }
        }
        // enabled = task_status 不在 PAUSED 即启用
        String taskStatus = asStr(row.get("task_status"));
        vo.setEnabled(!"PAUSED".equalsIgnoreCase(taskStatus));
        vo.setLastRunAt(formatTs(row.get("last_run_at")));
        vo.setLastStatus(asStr(row.get("last_status")));
        vo.setCreatedAt(formatTs(row.get("create_time")));
        return vo;
    }

    /** fallback 旧行 → VO。 */
    private ScheduledExtractVO toVoFromLegacyRow(Map<String, Object> row) {
        ScheduledExtractVO vo = new ScheduledExtractVO();
        vo.setId(((Number) row.get("id")).longValue());
        vo.setScheduleId(String.valueOf(row.get("schedule_id")));
        vo.setName(String.valueOf(row.get("name")));
        vo.setOntologyIds(parseOntologyIds(row.get("ontology_ids")));
        vo.setMode(row.get("mode") == null ? DEFAULT_MODE : String.valueOf(row.get("mode")));
        vo.setPeriod(row.get("period") == null ? null : String.valueOf(row.get("period")));
        Object cronExprRaw = row.get("cron_expression");
        String cronExpr = cronExprRaw == null ? null : String.valueOf(cronExprRaw);
        if (cronExpr != null) {
            String[] parts = cronExpr.trim().split("\\s+");
            if (parts.length >= 5) {
                try {
                    vo.setTimeOfDay(String.format("%02d:%02d",
                            Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[1])));
                } catch (NumberFormatException ignore) {
                    // 保持 null，前端降级
                }
            }
        }
        vo.setEnabled(row.get("enabled") != null && ((Number) row.get("enabled")).intValue() == 1);
        vo.setLastRunAt(formatTs(row.get("last_run_at")));
        vo.setLastStatus(row.get("last_status") == null ? null : String.valueOf(row.get("last_status")));
        vo.setCreatedAt(formatTs(row.get("created_at")));
        return vo;
    }

    /** 反序列化 parameters 字段中的 ontologyIds（List&lt;String&gt;）。 */
    @SuppressWarnings("unchecked")
    private List<String> parseOntologyIdsFromParams(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        try {
            if (raw instanceof String s) {
                if (s.isBlank()) {
                    return Collections.emptyList();
                }
                Map<String, Object> parsed = MAPPER.readValue(s, new TypeReference<Map<String, Object>>() {});
                Object ids = parsed == null ? null : parsed.get("ontologyIds");
                if (ids instanceof List<?> list) {
                    List<String> out = new ArrayList<>(list.size());
                    for (Object element : list) {
                        if (element != null) {
                            out.add(String.valueOf(element));
                        }
                    }
                    return out;
                }
            }
        } catch (Exception e) {
            log.warn("解析 parameters.ontologyIds 失败 raw={}: {}", raw, e.getMessage());
        }
        return Collections.emptyList();
    }

    /** 反序列化旧表 ontology_ids JSONB 列。 */
    private List<String> parseOntologyIds(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        try {
            if (raw instanceof String s) {
                if (s.isBlank()) {
                    return Collections.emptyList();
                }
                List<String> parsed = MAPPER.readValue(s, new TypeReference<List<String>>() {});
                return parsed == null ? Collections.emptyList() : parsed;
            }
            if (raw instanceof List<?> list) {
                List<String> out = new ArrayList<>(list.size());
                for (Object element : list) {
                    if (element != null) {
                        out.add(String.valueOf(element));
                    }
                }
                return out;
            }
        } catch (Exception e) {
            log.warn("解析 ontology_ids 字段失败 raw={}: {}", raw, e.getMessage());
        }
        return Collections.emptyList();
    }

    /** List&lt;String&gt; → JSON 字符串（null → "[]"，过滤空白项）。 */
    private static String toJsonString(List<String> ontologyIds) {
        try {
            return MAPPER.writeValueAsString(
                    ontologyIds == null ? Collections.emptyList()
                            : ontologyIds.stream().filter(s -> s != null && !s.isBlank()).toList());
        } catch (Exception e) {
            return "[]";
        }
    }

    /** null 安全 String 转换。 */
    private static String asStr(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    /** Timestamp/Date 安全 toString。 */
    private static String formatTs(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (raw instanceof java.util.Date d) {
            return new Timestamp(d.getTime()).toLocalDateTime().toString();
        }
        return String.valueOf(raw);
    }
}
