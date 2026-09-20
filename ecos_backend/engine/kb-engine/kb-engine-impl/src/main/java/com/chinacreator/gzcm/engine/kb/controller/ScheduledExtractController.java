package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.dto.ScheduledExtractCreatedVO;
import com.chinacreator.gzcm.engine.kb.dto.ScheduledExtractRequest;
import com.chinacreator.gzcm.engine.kb.dto.ScheduledExtractVO;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 结构化（映射驱动）实例抽取任务调度 Controller — Wave 2 T5 批次。
 *
 * <p>端点语义（均属 kb 引擎自有范围，业务数据端点默认 DENY，不写 permitAll）：
 * <ul>
 *   <li><b>T5-1</b> {@code POST /api/v1/knowledge/extract/scheduled} — 创建定时抽取任务：
 *       拼 6 位 Spring Cron（daily 8:00 → {@code '0 0 8 * * ?'}），调
 *       {@code TaskSchedulerService.scheduleTask(desc, cron)} 拿 {@code scheduleId}，
 *       INSERT {@code ecos_knowledge.kb_scheduled_extract}（V141），
 *       返回 {@code { id, scheduleId, nextRunAt }}。</li>
 *   <li><b>T5-2</b> {@code GET /api/v1/knowledge/extract/scheduled} —
 *       列出全部任务（{@code is_deleted=0}，按 {@code created_at DESC}），
 *       返回 {@link ScheduledExtractVO} 列表。</li>
 *   <li><b>T5-3</b> {@code PUT /api/v1/knowledge/extract/scheduled/{id}} —
 *       更新任务；若要改 cron → 先 {@code cancelSchedule(旧)} 再 {@code scheduleTask(新)}；
 *       {@code enabled=0} 仅取消调度不改 cron（下次 PUT 复活。</li>
 *   <li><b>T5-4</b> {@code DELETE /api/v1/knowledge/extract/scheduled/{id}} —
 *       软删除（{@code is_deleted=1}）+ {@code cancelSchedule}，历史 audit 不受影响。</li>
 * </ul>
 *
 * <p>铁律 §2.5-3：不 {@code new ScheduledExecutorService}，cron 注册完全委托 runtime-task
 * 的 {@link TaskSchedulerService}（Spring Bean，由 dccheng 的 {@code @ComponentScan}
 * 已扫到 {@code com.chinacreator.gzcm.runtime.core.task.scheduling}）。</p>
 *
 * <p>cron 校验：{@link CronExpression#parse} 失败 → 抛 {@link ValidationException}（6 位 Spring Cron）。</p>
 */
@RestController
@RequestMapping("/api/v1/knowledge/extract/scheduled")
public class ScheduledExtractController {

    private static final Logger log = LoggerFactory.getLogger(ScheduledExtractController.class);

    /** 默认抽取模式（与 K1 批次一致）。 */
    private static final String DEFAULT_MODE = "INCREMENTAL";

    /** 抽取任务 taskType（与 K1 batch KbImportTaskRegistrar / KbImportTaskExecutor 一致）。 */
    private static final String KB_IMPORT_TASK_TYPE = "KB_IMPORT";

    /** 调度型 jobId 前缀（与 K1 批次的 {@code KBK1S-} 区分，便于 T5-3 反查 schedule）。 */
    private static final String SCHED_JOB_ID_PREFIX = "SCHED-";

    /** V141 目标表。 */
    private static final String TABLE = "ecos_knowledge.kb_scheduled_extract";

    /** JSON 序列化 <s>仅本 Controller 用于反序列化 {@code ontology_ids} JSONB 列</s>。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;

    private final TaskSchedulerService taskSchedulerService;

    public ScheduledExtractController(JdbcTemplate jdbc,
                                       TaskSchedulerService taskSchedulerService) {
        this.jdbc = jdbc;
        this.taskSchedulerService = taskSchedulerService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // T5-1 — 创建定时任务
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 创建定时抽取任务：拼 cron → 注册 runtime-task → 落库。
     */
    @PostMapping
    public ApiResponse<ScheduledExtractCreatedVO> create(@RequestBody ScheduledExtractRequest req) {
        validateBase(req);
        String period = normalizePeriod(req.getPeriod());
        String timeOfDay = normalizeTimeOfDay(req.getTimeOfDay(), period);
        String mode = normalizeMode(req.getMode());
        String cron = buildCron(period, timeOfDay);
        Timestamp nextRunAt = computeNextRun(cron);

        // 注册 runtime-task
        java.util.Map<String, Object> params = new HashMap<>();
        params.put("ontologyIds", req.getOntologyIds() == null ? Collections.emptyList() : req.getOntologyIds());
        params.put("mode", mode);
        params.put("dryRun", false);
        params.put("jobId", SCHED_JOB_ID_PREFIX + System.currentTimeMillis());
        params.put("scheduled", true);

        TaskDescription desc = new TaskDescription();
        desc.setTaskName("定时结构化抽取: " + req.getName());
        desc.setTaskType(KB_IMPORT_TASK_TYPE);
        desc.setDescription("cron=" + cron + " period=" + period);
        desc.setParameters(params);
        desc.setAsync(true);
        desc.setCreatedBy("kb-scheduled");

        String scheduleId;
        try {
            scheduleId = taskSchedulerService.scheduleTask(desc, cron);
        } catch (Exception e) {
            log.error("T5-1 定时任务注册失败 name={}: {}", req.getName(), e.getMessage(), e);
            throw new ValidationException("定时任务注册失败: " + e.getMessage());
        }

        // 落库（INSERT ... RETURNING id 拿主键）
        Long id;
        try {
            Long returned = jdbc.queryForObject(
                    "INSERT INTO " + TABLE
                            + " (schedule_id, name, ontology_ids, mode, period, cron_expression, next_run_at, enabled, created_by) "
                            + "VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, 1, ?) RETURNING id",
                    Long.class,
                    scheduleId,
                    req.getName(),
                    toJsonString(req.getOntologyIds()),
                    mode,
                    period,
                    cron,
                    nextRunAt,
                    "kb-scheduled");
            if (returned == null) {
                throw new ValidationException("\"$TABLE\" INSERT 未返回 id");
            }
            id = returned;
        } catch (DataAccessException e) {
            // INSERT 失败时回滚 schedule，避免悬挂
            try {
                taskSchedulerService.cancelSchedule(scheduleId);
            } catch (Exception ignore) {
                log.warn("T5-1 INSERT 失败后回滚 scheduleId={} 异常（忽略）: {}", scheduleId, ignore.getMessage());
            }
            log.error("T5-1 落库失败: {}", e.getMessage(), e);
            throw new ValidationException("落库失败: " + e.getMessage());
        }

        log.info("T5-1 定时任务已创建: id={} scheduleId={} period={} cron={} nextRunAt={}",
                id, scheduleId, period, cron, nextRunAt.toLocalDateTime());
        ScheduledExtractCreatedVO out = new ScheduledExtractCreatedVO();
        out.setId(id);
        out.setScheduleId(scheduleId);
        out.setNextRunAt(nextRunAt.toLocalDateTime().toString());
        return ApiResponse.success(out);
    }

    // ─────────────────────────────────────────────────────────────────────
    // T5-2 — 列表
    // ─────────────────────────────────────────────────────────────────────

    /** 列表（未软删，按 created_at DESC）。 */
    @GetMapping
    public ApiResponse<List<ScheduledExtractVO>> list() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(
                    "SELECT id, schedule_id, name, "
                            + "convert_from(ontology_ids::bytea,'UTF8') AS ontology_ids, "
                            + "mode, period, cron_expression, "
                            + "next_run_at, enabled, last_run_at, last_status, created_at "
                            + "FROM " + TABLE + " WHERE is_deleted = 0 ORDER BY created_at DESC");
        } catch (DataAccessException e) {
            log.warn("T5-2 查询定时任务列表失败（表未建或不可访问）: {}", e.getMessage());
            return ApiResponse.success(new ArrayList<>());
        }
        List<ScheduledExtractVO> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            out.add(toVo(row));
        }
        return ApiResponse.success(out);
    }

    // ─────────────────────────────────────────────────────────────────────
    // T5-3 — 更新
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 更新定时任务。
     *
     * <p>语义：若 {@code enabled} 变化或需改 cron/ontologyIds，相应地 cancel + re-register；
     * 仅改 {@code name} / {@code enabled=false} 时只更新数据库不动调度。</p>
     */
    @PutMapping("/{id}")
    public ApiResponse<ScheduledExtractVO> update(@PathVariable Long id,
                                                   @RequestBody ScheduledExtractRequest req) {
        validateBase(req);
        Map<String, Object> current;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, schedule_id, name, "
                            + "convert_from(ontology_ids::bytea,'UTF8') AS ontology_ids, "
                            + "mode, period, cron_expression, next_run_at, enabled, "
                            + "last_run_at, last_status, created_at "
                            + "FROM " + TABLE + " WHERE id = ? AND is_deleted = 0", id);
            if (rows.isEmpty()) {
                return ApiResponse.notFound("未找到任务: id=" + id);
            }
            current = rows.get(0);
        } catch (DataAccessException e) {
            log.error("T5-3 查询现有任务失败 id={}: {}", id, e.getMessage());
            return ApiResponse.badRequest("查询现有任务失败: " + e.getMessage());
        }

        String curCron = String.valueOf(current.get("cron_expression"));
        String curName = String.valueOf(current.get("name"));
        String curOntologyIds = toJsonString(parseOntologyIds(current.get("ontology_ids")));
        String newMode = normalizeMode(req.getMode());
        String newPeriod = normalizePeriod(req.getPeriod());
        String newTimeOfDay = normalizeTimeOfDay(req.getTimeOfDay(), newPeriod);
        String newCron = buildCron(newPeriod, newTimeOfDay);
        boolean cronChanged = !newCron.equals(curCron);
        String newOntologyIds = toJsonString(req.getOntologyIds());
        boolean ontologyChanged = !newOntologyIds.equals(curOntologyIds);
        boolean enabledNow = req.getEnabled() == null || req.getEnabled();
        int curEnabled = ((Number) current.get("enabled")).intValue();

        String oldScheduleId = String.valueOf(current.get("schedule_id"));

        // 若 cron / ontologyIds 变 或 (disabled→enabled) → cancel + re-register
        boolean needReschedule = cronChanged
                || ontologyChanged
                || (curEnabled == 0 && enabledNow);
        String newScheduleId = oldScheduleId;
        if (needReschedule) {
            try {
                taskSchedulerService.cancelSchedule(oldScheduleId);
            } catch (Exception e) {
                log.warn("T5-3 cancelSchedule 失败（忽略）: {} for scheduleId={}", e.getMessage(), oldScheduleId);
            }
            newScheduleId = registerSchedule(req, newMode, newPeriod, newCron);
        }

        // 若 enabled 变化 → 更新 DB 的 enabled 字段；disabled 时还需 cancel 调度
        if (!needReschedule && curEnabled == 1 && !enabledNow) {
            try {
                taskSchedulerService.cancelSchedule(oldScheduleId);
            } catch (Exception e) {
                log.warn("T5-3 取消调度失败（忽略）: {}", e.getMessage());
            }
        }

        Timestamp newNextRun = computeNextRun(newCron);
        jdbc.update(
                "UPDATE " + TABLE
                        + " SET schedule_id=?, name=?, ontology_ids=?::jsonb, mode=?, period=?, cron_expression=?, next_run_at=?, enabled=?, updated_at=NOW() "
                        + "WHERE id = ? AND is_deleted = 0",
                newScheduleId, req.getName(), newOntologyIds,
                newMode, newPeriod, newCron, newNextRun, enabledNow ? 1 : 0, id);

        log.info("T5-3 定时任务已更新: id={} reschedule={} enabledNow={} newCron={}",
                id, needReschedule, enabledNow, newCron);
        Map<String, Object> updated;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, schedule_id, name, ontology_ids, mode, period, cron_expression, "
                            + "next_run_at, enabled, last_run_at, last_status, created_at "
                            + "FROM " + TABLE + " WHERE id = ? AND is_deleted = 0", id);
            if (rows.isEmpty()) {
                return ApiResponse.notFound("更新后未找到: id=" + id);
            }
            updated = rows.get(0);
        } catch (DataAccessException e) {
            log.error("T5-3 回读任务失败 id={}: {}", id, e.getMessage());
            return ApiResponse.badRequest("回读任务失败: " + e.getMessage());
        }
        return ApiResponse.success(toVo(updated));
    }

    // ─────────────────────────────────────────────────────────────────────
    // T5-4 — 软删除
    // ─────────────────────────────────────────────────────────────────────

    /** 软删除（is_deleted=1）+ cancelSchedule（取消 future，不影响已落盘的 audit）。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Map<String, Object> current;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT schedule_id, is_deleted FROM " + TABLE + " WHERE id = ?", id);
            if (rows.isEmpty()) {
                return ApiResponse.notFound("未找到任务: id=" + id);
            }
            current = rows.get(0);
        } catch (DataAccessException e) {
            log.error("T5-4 查询任务失败 id={}: {}", id, e.getMessage());
            return ApiResponse.badRequest("查询任务失败: " + e.getMessage());
        }
        if (((Number) current.get("is_deleted")).intValue() == 1) {
            return ApiResponse.notFound("已删除: id=" + id);
        }

        // 先软删（避免并发场景下调度器还能看到已删记录）
        jdbc.update("UPDATE " + TABLE + " SET is_deleted = 1, updated_at = NOW() WHERE id = ? AND is_deleted = 0", id);

        // 取消调度（失败忽略，DB 已软删，下次重启不会再注册）
        String scheduleId = String.valueOf(current.get("schedule_id"));
        try {
            taskSchedulerService.cancelSchedule(scheduleId);
        } catch (Exception e) {
            log.warn("T5-4 取消调度失败（忽略）: {} for scheduleId={}", e.getMessage(), scheduleId);
        }

        log.info("T5-4 定时任务已软删除: id={} scheduleId={}", id, scheduleId);
        return ApiResponse.success();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 内部辅助
    // ─────────────────────────────────────────────────────────────────────

    /** 校验 name 非空 / period / timeOfDay 合法性。 */
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

    /**
     * cron 校验 + 计算下次运行时点（ZonedDateTime.now → LocalDateTime）。
     *
     * <p>注意：Spring {@link CronExpression#next(TemporalAccessor)} 内部按
     * {@code DayOfWeek} 推进，{@code Instant} 无时区故不支持该字段（抛
     * {@code UnsupportedTemporalTypeException}）。必须传入带时区的
     * {@link ZonedDateTime}（系统默认时区），才能正确算出下次触发点。</p>
     */
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

    /** 调 runtime-task scheduleTask 拿 scheduleId。 */
    private String registerSchedule(ScheduledExtractRequest req, String mode, String period, String cron) {
        java.util.Map<String, Object> params = new HashMap<>();
        params.put("ontologyIds", req.getOntologyIds() == null ? Collections.emptyList() : req.getOntologyIds());
        params.put("mode", mode);
        params.put("dryRun", false);
        params.put("jobId", SCHED_JOB_ID_PREFIX + System.currentTimeMillis());
        params.put("scheduled", true);
        TaskDescription desc = new TaskDescription();
        desc.setTaskName("定时结构化抽取: " + req.getName());
        desc.setTaskType(KB_IMPORT_TASK_TYPE);
        desc.setDescription("cron=" + cron + " period=" + period);
        desc.setParameters(params);
        desc.setAsync(true);
        desc.setCreatedBy("kb-scheduled");
        try {
            return taskSchedulerService.scheduleTask(desc, cron);
        } catch (Exception e) {
            log.error("T5 registerSchedule 失败: {}", e.getMessage(), e);
            throw new ValidationException("定时任务注册失败: " + e.getMessage());
        }
    }

    /** {@code ontology_ids} JSONB 列序列化（null → "[]"）。 */
    private static String toJsonString(List<String> ontologyIds) {
        try {
            return MAPPER.writeValueAsString(
                    ontologyIds == null ? Collections.emptyList() :
                            ontologyIds.stream().filter(s -> s != null && !s.isBlank()).toList());
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 反序列化 {@code ontology_ids} JSONB 列。 */
    @SuppressWarnings("unchecked")
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

    /** 数据库行 → VO。 */
    private ScheduledExtractVO toVo(Map<String, Object> row) {
        ScheduledExtractVO vo = new ScheduledExtractVO();
        vo.setId(((Number) row.get("id")).longValue());
        vo.setScheduleId(String.valueOf(row.get("schedule_id")));
        vo.setName(String.valueOf(row.get("name")));
        vo.setOntologyIds(parseOntologyIds(row.get("ontology_ids")));
        vo.setMode(row.get("mode") == null ? DEFAULT_MODE : String.valueOf(row.get("mode")));
        vo.setPeriod(row.get("period") == null ? null : String.valueOf(row.get("period")));

        // timeOfDay 从 cron_expression 第 2/3 位推导（cron 形如 "0 m h * * ?"）
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
                    // 保持 null，前端自行降级
                }
            }
        }

        vo.setEnabled(row.get("enabled") != null && ((Number) row.get("enabled")).intValue() == 1);
        vo.setLastRunAt(formatTs(row.get("last_run_at")));
        vo.setLastStatus(row.get("last_status") == null ? null : String.valueOf(row.get("last_status")));
        vo.setCreatedAt(formatTs(row.get("created_at")));
        return vo;
    }

    /** Timestamp → ISO-8601（null 安全）。 */
    private static String formatTs(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        return String.valueOf(raw);
    }
}
