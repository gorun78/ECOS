package com.chinacreator.gzcm.engine.cognitive2.service.framework;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.framework.CognitiveFrameworkStatus;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认知执行记录服务（详细设计-认知引擎 v1.0 §7）。
 *
 * <p><b>持久化口径</b>：本服务只保存请求态执行记录（进程内，与 {@code CognitivePlannerController}
 * 同口径），不新增执行表/任务表；长任务的实际调度仍归 {@code runtime-task}，执行归 W Agent Runtime。
 * 记录携带统一关联键（trace/request/scenario/blueprint/contextDigest/framework 版本）与
 * 业务结论状态（{@code businessConclusionStatus}），后者与流程状态严格分离。</p>
 *
 * <p>幂等：{@code idempotencyKey} 相同的创建请求返回同一条记录并标记 {@code idempotentReplay=true}，
 * 不产生第二次副作用。非法状态迁移 409；终态不可再迁移。</p>
 */
@Service
public class CognitiveExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CognitiveExecutionService.class);

    private final Map<String, Map<String, Object>> executions = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    public Map<String, Object> create(Map<String, Object> request) {
        Map<String, Object> req = request == null ? Map.of() : request;
        String idempotencyKey = str(req.get("idempotencyKey"));
        if (!idempotencyKey.isBlank()) {
            String existingId = idempotencyIndex.get(idempotencyKey);
            if (existingId != null) {
                Map<String, Object> existing = executions.get(existingId);
                if (existing != null) {
                    Map<String, Object> replay = new LinkedHashMap<>(existing);
                    replay.put("idempotentReplay", true);
                    return replay;
                }
            }
        }
        Object frameworkRefs = req.getOrDefault("frameworkRefs", List.of());
        if (!(frameworkRefs instanceof List<?>)) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "frameworkRefs");
        }
        List<Map<String, Object>> contextSources = normalizeContextSources(req.get("contextSources"));

        String executionId = "cog-exec-" + UUID.randomUUID();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("executionId", executionId);
        record.put("idempotencyKey", idempotencyKey.isBlank() ? null : idempotencyKey);
        record.put("status", CognitiveExecutionStateMachine.Status.CREATED.name());
        record.put("businessConclusionStatus", normalizeBusinessStatus(str(req.get("businessConclusionStatus"))));
        record.put("traceId", str(req.get("traceId")));
        record.put("requestId", str(req.get("requestId")));
        record.put("scenarioId", str(req.get("scenarioId")));
        record.put("blueprintId", str(req.get("blueprintId")));
        record.put("blueprintVersion", req.get("blueprintVersion"));
        record.put("contextDigest", str(req.get("contextDigest")));
        record.put("contextSources", contextSources);
        record.put("taskId", str(req.get("taskId")));
        record.put("planId", str(req.get("planId")));
        record.put("baselineRunId", str(req.get("baselineRunId")));
        record.put("runMode", "SANDBOX".equalsIgnoreCase(str(req.get("runMode"))) ? "SANDBOX" : "FORMAL");
        record.put("frameworkRefs", frameworkRefs);
        record.put("createdAt", Instant.now().toString());
        record.put("updatedAt", Instant.now().toString());
        record.put("history", new ArrayList<>(List.of(historyEntry(null,
                CognitiveExecutionStateMachine.Status.CREATED, "created", Map.of()))));
        record.put("idempotentReplay", false);

        executions.put(executionId, record);
        if (!idempotencyKey.isBlank()) {
            idempotencyIndex.putIfAbsent(idempotencyKey, executionId);
        }
        log.info("认知执行记录创建: executionId={} scenario={} runMode={}",
                executionId, record.get("scenarioId"), record.get("runMode"));
        return record;
    }

    public Map<String, Object> get(String executionId) {
        Map<String, Object> record = executions.get(executionId);
        if (record == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E404_MODEL_ROW, "执行记录不存在: " + executionId);
        }
        return record;
    }

    public Map<String, Object> transition(String executionId, Map<String, Object> request) {
        Map<String, Object> req = request == null ? Map.of() : request;
        Map<String, Object> record = get(executionId);
        CognitiveExecutionStateMachine.Status from =
                CognitiveExecutionStateMachine.parse(str(record.get("status")));
        CognitiveExecutionStateMachine.Status to =
                CognitiveExecutionStateMachine.parse(str(req.get("targetStatus")));
        if (to == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "targetStatus");
        }
        if (from == null || from.terminal() || !CognitiveExecutionStateMachine.isLegal(from, to)) {
            throw new CognitiveGuardException(CognitiveErrorCode.E409_STATE_ILLEGAL,
                    "非法认知执行状态迁移: " + from + " -> " + to);
        }
        if (to == CognitiveExecutionStateMachine.Status.SUCCEEDED) {
            boolean schemaValid = Boolean.TRUE.equals(req.get("schemaValid"));
            boolean businessValid = Boolean.TRUE.equals(req.get("businessValid"));
            if (!CognitiveExecutionStateMachine.maySucceed(from, schemaValid, businessValid)) {
                throw new CognitiveGuardException(CognitiveErrorCode.E422_GATES_NOT_PASSED,
                        "SUCCEEDED 需 VERIFYING 且 Schema/业务验证均通过",
                        "schemaValid=" + schemaValid + ",businessValid=" + businessValid);
            }
        }

        record.put("status", to.name());
        record.put("updatedAt", Instant.now().toString());
        if (req.containsKey("businessConclusionStatus")) {
            record.put("businessConclusionStatus",
                    normalizeBusinessStatus(str(req.get("businessConclusionStatus"))));
        }
        if (req.containsKey("resultRef")) {
            record.put("resultRef", req.get("resultRef"));
        }
        if (req.containsKey("failureStage")) {
            record.put("failureStage", req.get("failureStage"));
        }
        if (req.containsKey("reason")) {
            record.put("reason", req.get("reason"));
        }
        if (req.containsKey("progress")) {
            record.put("progress", req.get("progress"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) record.get("history");
        history.add(historyEntry(from, to, str(req.get("reason")), req));
        log.info("认知执行状态迁移: executionId={} {} -> {}", executionId, from, to);
        return record;
    }

    /** 框架版本是否仍可用于新选择（供执行前二次校验；已停用不得静默切换到最新版）。 */
    public static void requireSelectable(String status) {
        CognitiveFrameworkStatus s = CognitiveFrameworkStatus.fromValue(status);
        if (s == null || !s.selectable()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E409_VERSION_STALE,
                    "框架版本不可用（不静默切换版本）: status=" + status);
        }
    }

    private static Map<String, Object> historyEntry(CognitiveExecutionStateMachine.Status from,
                                                    CognitiveExecutionStateMachine.Status to,
                                                    String reason, Map<String, Object> extra) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("from", from == null ? null : from.name());
        e.put("to", to.name());
        e.put("at", Instant.now().toString());
        e.put("reason", reason == null || reason.isBlank() ? null : reason);
        if (extra != null && extra.get("failureStage") != null) {
            e.put("failureStage", extra.get("failureStage"));
        }
        return e;
    }

    /** 来源资产版本三要素（sourceType/sourceId/version）缺一即 400，禁无版本引用。 */
    private static List<Map<String, Object>> normalizeContextSources(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "contextSources");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                        CognitiveErrorCode.MSG_INVALID_PARAM, "contextSources");
            }
            String sourceType = str(m.get("sourceType")).trim();
            String sourceId = str(m.get("sourceId")).trim();
            String version = str(m.get("version")).trim();
            if (sourceType.isEmpty() || sourceId.isEmpty() || version.isEmpty()) {
                throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                        CognitiveErrorCode.MSG_INVALID_PARAM,
                        "contextSources 须含 sourceType/sourceId/version");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sourceType", sourceType);
            entry.put("sourceId", sourceId);
            entry.put("version", version);
            if (m.get("asOfTime") != null) {
                entry.put("asOfTime", String.valueOf(m.get("asOfTime")));
            }
            out.add(entry);
        }
        return out;
    }

    private static String normalizeBusinessStatus(String raw) {        if (raw == null || raw.isBlank()) {
            return "NOT_EVALUATED";
        }
        String v = raw.trim().toUpperCase();
        return switch (v) {
            case "CORRECT", "INCORRECT", "PARTIAL", "UNVERIFIED", "NOT_EVALUATED" -> v;
            default -> "NOT_EVALUATED";
        };
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
