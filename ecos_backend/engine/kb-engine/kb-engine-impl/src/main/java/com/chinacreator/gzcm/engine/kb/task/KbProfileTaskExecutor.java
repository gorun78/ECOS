package com.chinacreator.gzcm.engine.kb.task;

import com.chinacreator.gzcm.engine.kb.profile.KbProfileServiceImpl;
import com.chinacreator.gzcm.engine.kb.profile.ProfileGenerateRequest;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史画像生成任务执行器（F04-06 / F04-14 runtime-task 收口）。
 * <p>
 * submit(KB_PROFILE_GENERATE) → parse(KbProfileTaskParser) → execute(本类)：
 * 读 config 的 metricCodes/dims/windowFrom/windowTo → 调
 * {@link KbProfileServiceImpl#generateSync(ProfileGenerateRequest, boolean)} 同步生成
 * → 回报进度 + 完成。禁自建线程（铁律 §2.5-3）：本执行器本身由 runtime-task 生命周期驱动。
 *
 * @author ECOS KB Team
 */
@Component
public class KbProfileTaskExecutor implements ITaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(KbProfileTaskExecutor.class);
    private static final String TASK_TYPE = "KB_PROFILE_GENERATE";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KbProfileServiceImpl profileService;

    public KbProfileTaskExecutor(KbProfileServiceImpl profileService) {
        this.profileService = profileService;
    }

    @Override
    public String execute(TaskExecutionPlan plan, ITaskStatusCallback callback) throws TaskExecutionException {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            throw new TaskExecutionException(TASK_TYPE + " execution plan has no steps");
        }
        String taskId = plan.getTaskId();
        Map<String, Object> cfg = new LinkedHashMap<>();
        if (plan.getSteps().get(0).getConfig() != null) {
            cfg.putAll(plan.getSteps().get(0).getConfig());
        }
        ProfileGenerateRequest req = new ProfileGenerateRequest();
        req.setMetricCodes(toStringList(cfg.get("metricCodes")));
        Map<String, Object> dims = new LinkedHashMap<>();
        Object dimsRaw = cfg.get("dims");
        if (dimsRaw instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                dims.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        req.setDims(dims);
        req.setWindowFrom(asStr(cfg.get("windowFrom")));
        req.setWindowTo(asStr(cfg.get("windowTo")));

        if (callback != null) {
            callback.onProgressUpdate(taskId, 0, "画像生成开始: metrics=" + req.getMetricCodes());
        }
        try {
            KbProfileServiceImpl.GenerationResult r = profileService.generateSync(req, false);
            if (callback != null) {
                callback.onProgressUpdate(taskId, 100, "画像生成完成");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("generated", r.generated);
            result.put("degraded", r.degraded);
            result.put("insufficient", r.insufficient);
            result.put("errorCodes", r.errorCodes);
            String json = MAPPER.writeValueAsString(result);
            if (callback != null) {
                callback.onTaskComplete(taskId, true, json, null);
            }
            log.info("KbProfileTaskExecutor.done: taskId={} result={}", taskId, json);
            return json;
        } catch (Exception e) {
            log.error("KbProfileTaskExecutor.failed: taskId={} err={}", taskId, e.getMessage(), e);
            if (callback != null) {
                callback.onTaskComplete(taskId, false, null, e.getMessage());
                callback.onError(taskId, e.getMessage(), stackTraceAsString(e));
            }
            throw new TaskExecutionException(TASK_TYPE + " 画像生成失败: " + e.getMessage(), e);
        }
    }

    private static List<String> toStringList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) {
                if (x != null) out.add(String.valueOf(x));
            }
        } else if (o != null) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    private static String asStr(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String stackTraceAsString(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    @Override
    public void cancel(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException(TASK_TYPE + " 不支持运行中取消");
    }

    @Override
    public void pause(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException(TASK_TYPE + " 不支持暂停");
    }

    @Override
    public void resume(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException(TASK_TYPE + " 不支持恢复");
    }

    @Override
    public TaskStatus getStatus(String taskId) throws TaskExecutionException {
        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setStatus(TaskStatus.Status.RUNNING);
        return status;
    }
}
