package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService.TaskManagementException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 知识抽取「提交 / 预演」单腿服务 — 详设-07 §7.3.2 端点总表第 12 行
 * （{@code POST /api/v1/knowledge/extract/runs}）与 {@code POST /extract/dry-run} 的共同编排。
 *
 * <p><b>为什么单腿</b>：E3（{@code POST /extract/structured}）与 runs 族是同一件抽取行为的两个入口，
 * 提交形态（{@code TaskDescription} 参数键面、{@code KB_IMPORT} 任务类型、{@code biz_kind=KB_EXTRACT}、
 * {@code jobId=KBK1S-} 前缀、{@code submitTask+executeTask} 同步派发）若各写一遍即双源，
 * 任一入口改参数键面都会让另一入口在 runtime-task 侧读不到参数而静默降级（如 dryRun 丢失 → 预演写库）。
 * 故本类持有该腿，两个 Controller 均委托（路径/入参/信封三样零改动）。</p>
 *
 * <p>铁律遵循：不另起线程（§1.6-1，{@code desc.setAsync(false)} + {@code executeTask} 同步派发）；
 * 不自建调度（§2.5-3）；{@code ontologySnapshotId} 直接作为抽取范围 {@code ontologyId} 使用
 * （kb 快照表 {@code kb_ontology_snapshot} 以 {@code ontology_id} 为外部业务键）。</p>
 */
@Service
public class KnowledgeExtractRunService {

    /** 业务 jobId 前缀（与 E3 同族；落 {@code ecos_kb_extract_audit.job_id} 供溯源）。 */
    public static final String JOB_ID_PREFIX = "KBK1S-";

    /** 模式：全量（忽略水位线，重读全部实例行）。 */
    public static final String MODE_FULL = "FULL";

    /** 模式：增量（按持久化水位线续读），默认。 */
    public static final String MODE_INCREMENTAL = "INCREMENTAL";

    /** runtime-task 任务类型（与 KbImportTaskRegistrar／KbImportTaskExecutor 同名）。 */
    public static final String KB_IMPORT_TASK_TYPE = "KB_IMPORT";

    /** 当前唯一承载的抽取来源类型：本体映射台账驱动（映射契约不落 kb 表，见抽取服务类注释）。 */
    public static final String SOURCE_TYPE_ONTOLOGY_MAPPING = "ONTOLOGY_MAPPING";

    private static final String BIZ_KIND = "KB_EXTRACT";

    private final KbEntityInstanceExtractionService extractionService;

    private final ITaskManagementService taskManagement;

    public KnowledgeExtractRunService(KbEntityInstanceExtractionService extractionService,
                                      ITaskManagementService taskManagement) {
        this.extractionService = extractionService;
        this.taskManagement = taskManagement;
    }

    /** 生成一次抽取的业务 jobId（时间戳形态，与既有两个入口同前缀）。 */
    public String newJobId() {
        return JOB_ID_PREFIX + System.currentTimeMillis();
    }

    /** 模式归一化：null/空白 / INCREMENTAL → INCREMENTAL；FULL → FULL；其他抛 {@code ValidationException}。 */
    public static String normalizeMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return MODE_INCREMENTAL;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (MODE_FULL.equals(value)) {
            return MODE_FULL;
        }
        if (MODE_INCREMENTAL.equals(value)) {
            return MODE_INCREMENTAL;
        }
        throw new ValidationException(
                "抽取模式非法: mode=" + raw + "（允许 FULL / INCREMENTAL）");
    }

    /**
     * 抽取前置闸：无可用本体快照即拒绝（详设-07 C.1 ①「禁无约束自由抽取」），
     * 以本域 409 {@code ECOS-KB-011} 上抛，由 {@code KbErrorAdvice} 落状态行。
     *
     * <p>仅 runs 族调用：E3（{@code /extract/structured}）既有的「无快照返回 NO_SNAPSHOT 报告」语义
     * 属存量契约，不因本批收紧（API 只增不改）。</p>
     */
    public void requireSnapshot(String ontologySnapshotId) {
        if (extractionService.snapshotAvailable(ontologySnapshotId)) {
            return;
        }
        String scope = (ontologySnapshotId == null || ontologySnapshotId.isBlank())
                ? "ALL" : ontologySnapshotId.trim();
        throw KbErrorCodes.SNAPSHOT_MISSING.exception(
                "本体快照缺失：ontologySnapshotId=" + scope + "（禁无约束自由抽取）");
    }

    /** 同步预演（dryRun=true，水位线不推进、不写库）。 */
    public EntityInstanceExtractionReportVO preview(String ontologySnapshotId, String jobId, boolean incremental) {
        return extractionService.extract(ontologySnapshotId, jobId, incremental, true);
    }

    /**
     * 异步提交一次抽取（runtime-task {@code KB_IMPORT}）：返回真实 {@code taskId} 与业务 {@code jobId}。
     *
     * @throws TaskManagementException task 底座（提交／派发）不可用
     */
    public SubmitOutcome submit(String ontologySnapshotId, String mode, boolean dryRun, String jobId)
            throws TaskManagementException {
        TaskDescription desc = new TaskDescription();
        desc.setTaskName("结构化映射实例抽取: " + jobId);
        desc.setTaskType(KB_IMPORT_TASK_TYPE);
        desc.setDescription("结构化映射驱动实例抽取 jobId=" + jobId);
        Map<String, Object> params = new HashMap<>();
        params.put("ontologyId", ontologySnapshotId);
        params.put("mode", mode);
        params.put("dryRun", dryRun);
        params.put("jobId", jobId);
        params.put("biz_kind", BIZ_KIND);
        desc.setParameters(params);
        desc.setAsync(false);   // 即时任务同步派发（不进入调度循环，立即 executeTask）
        String taskId = taskManagement.submitTask(desc);
        taskManagement.executeTask(taskId);
        return new SubmitOutcome(taskId, jobId);
    }

    /** 提交结果：runtime-task 任务 ID + 业务 jobId（后者落 audit 供 E5/T6 溯源）。 */
    public record SubmitOutcome(String taskId, String jobId) {
    }
}
