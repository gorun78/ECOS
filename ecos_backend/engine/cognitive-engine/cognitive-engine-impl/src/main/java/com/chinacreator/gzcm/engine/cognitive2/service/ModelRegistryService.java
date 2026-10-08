package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveModelType;
import com.chinacreator.gzcm.engine.cognitive.api.GateOutcome;
import com.chinacreator.gzcm.engine.cognitive.api.RegistryState;
import com.chinacreator.gzcm.engine.cognitive2.dao.ModelRegistryDao;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ModelContentHasher;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 认知模型注册表服务（PMO-51 T3 / ADR-8；REQ-COG-07 注册中心应用侧，本批 #86）。
 *
 * <p>ADR-8 口径：推理"结果"不落盘；模型"资产"（注册/版本/回测）可落
 * {@code ecos_cognitive.ecos_cognitive_model}（V261 收口，权威载体 = 控制域认知 schema，
 * 规格 5 不建 {@code ecos_ai} 副本）。本服务只做模型资产的注册/门禁/升格，不参与推理计算；
 * SQL 落点全部收口 {@link ModelRegistryDao}（架构铁律 §2.5 #1 / IR06，B2-b）。</p>
 *
 * <p><b>REQ-COG-07 三条契约的落点</b>：</p>
 * <ol>
 *   <li>类型枚举单源 {@link CognitiveModelType}（CM-01~CM-07）——旧四值到 CM 的映射与 V261
 *       补形段逐字一致；既有 {@code POST /models}（registerModel）行为向后兼容不变，
 *       新注册中心端点（{@code POST /models/registry}）在 {@code cognitive_model_type}
 *       未映射/未知枚举字面量时 <b>fail-loud 400</b>（{@code ECOS-COG-400C}），禁静默默认值；</li>
 *   <li>版本不可变（规格 3）——已 PUBLISHED 行禁任何内容 UPDATE（哈希/门禁/状态列的写 SQL
 *       均带 {@code registry_state <> 'PUBLISHED'} 守卫，0 行受影响即显式 409），变更只准走
 *       {@code registerModel}/{@code registerOnRegistry} 新建版本行并 {@code supersedes_id} 指旧版；</li>
 *   <li>四类发布门禁（规格 4）——{@link ModelPublishGateEvaluator} 对已登记证据真实裁决
 *       （regression 在本仓不可评测 ⇒ PENDING fail-closed），
 *       {@code publish} 判据 = content_hash 已冻结 且 四门禁列全 PASS，否则 422
 *       （{@code ECOS-COG-422R}，data.missing 列出未过项），禁静默成功。</li>
 * </ol>
 */
@Service
public class ModelRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ModelRegistryService.class);
    /** 旧四值白名单（PMO-51 既有契约，只增不改：registerModel 校验面维持原词表与原错误消息）。 */
    private static final Set<String> ALLOWED_TYPES = Set.of("FORECAST", "CAUSAL", "SIMULATION", "DECISION");
    private static final Set<String> ALLOWED_STATUS = Set.of("active", "retired");

    private final ModelRegistryDao modelRegistryDao;
    private final ModelPublishGateEvaluator gateEvaluator;

    public ModelRegistryService(ModelRegistryDao modelRegistryDao,
                                ModelPublishGateEvaluator gateEvaluator) {
        this.modelRegistryDao = modelRegistryDao;
        this.gateEvaluator = gateEvaluator;
    }

    /** 模型列表（按 modelType 可选过滤，未删除，按 model_id/version 排序）。 */
    public List<Map<String, Object>> listModels(String modelType) {
        return modelRegistryDao.listModels(modelType);
    }

    /** 某 model_id 的全部版本（未删除，version 倒序）。 */
    public List<Map<String, Object>> getModel(String modelId) {
        return modelRegistryDao.getModel(modelId);
    }

    /**
     * 注册模型（PMO-51 既有端点语义，向后兼容）：同 model_id 新增下一个 version
     * （version = max+1，首版为 1）；REQ-COG-07 附加义务 = 旧 modelType 按 V261 映射写
     * {@code cognitive_model_type}，并把 {@code supersedes_id} 指向上一版本行（版本链）。
     *
     * <p>旧四值全部可无歧义映射 ⇒ 本路径不产生"未映射"失败；fail-loud 面在新注册中心端点
     * （{@link #registerOnRegistry}）。registry_state 依 V261 默认 DRAFT，发布必走门禁。</p>
     *
     * @param body 入参 modelId(必填) / modelType(必填) / features / backtestScore / status
     *             / cognitiveModelType(可选，CM-01~CM-07 显式覆盖旧值映射)
     * @return 注册后的行（含新增键 cognitiveModelType/registryState/supersedesId，只增不改）
     */
    public Map<String, Object> registerModel(Map<String, Object> body) {
        String modelId = asString(body.get("modelId"));
        String modelType = asString(body.get("modelType"));
        if (modelId == null || modelId.isBlank()) {
            throw new BusinessException(400, "CORM-400: modelId 必填");
        }
        if (modelType == null || !ALLOWED_TYPES.contains(modelType.toUpperCase())) {
            throw new BusinessException(400, "CORM-400: 非法 modelType（FORECAST/CAUSAL/SIMULATION/DECISION）");
        }
        Optional<CognitiveModelType> explicit = explicitCmTypeOrReject(body);
        CognitiveModelType cmType = explicit.isPresent() ? explicit.get()
                : CognitiveModelType.fromLegacyModelType(modelType).orElseThrow(() -> new BusinessException(
                        400, "CORM-400: 非法 modelType（FORECAST/CAUSAL/SIMULATION/DECISION）"));
        insertVersion(modelId, modelType.toUpperCase(), cmType, body, /*legacyStatusError=*/true);
        return buildRegisteredRow(modelId, body, cmType);
    }

    /**
     * 注册中心端点（REQ-COG-07 规格 1，新增加法）：类型必须可解析为冻结枚举，否则 fail-loud
     * 400（{@code ECOS-COG-400C}），禁静默默认 —— 与 registerModel 的差异即"未映射即拒"。
     *
     * <p>CM-02/CM-06/CM-07 等无旧值对应类型：{@code cognitiveModelType} 显式给出即可，
     * 此时旧判别列 {@code model_type} 落 CM 字面量本身（旧读路径按四旧值过滤不会误命中）。
     * 未知枚举字面量（CM-08 等）一律 400，新增类型必须先改 {@link CognitiveModelType} 契约。</p>
     */
    public Map<String, Object> registerOnRegistry(Map<String, Object> body) {
        String modelId = asString(body.get("modelId"));
        if (modelId == null || modelId.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "modelId");
        }
        String rawCm = asString(body.get("cognitiveModelType"));
        String modelType = asString(body.get("modelType"));

        Optional<CognitiveModelType> cm;
        if (rawCm != null && !rawCm.isBlank()) {
            cm = CognitiveModelType.fromCode(rawCm);           // 未知字面量 ⇒ empty ⇒ 400C（无静默默认）
            if (cm.isEmpty()) {
                throw unmapped(rawCm);
            }
        } else {
            cm = CognitiveModelType.fromLegacyModelType(modelType);
            if (cm.isEmpty()) {
                throw unmapped(modelType);
            }
        }
        String legacyColumn = modelType == null || modelType.isBlank()
                ? cm.get().code() : modelType.toUpperCase();
        insertVersion(modelId, legacyColumn, cm.get(), body, false);
        return buildRegisteredRow(modelId, body, cm.get());
    }

    /** 计算并冻结规范化内容哈希（仅 DRAFT 行；已 PUBLISHED 行内容不可变 ⇒ 409）。 */
    public Map<String, Object> computeAndFreezeContentHash(String rowId) {
        Map<String, Object> row = requireRow(rowId);
        requireDraft(row, rowId);
        String hash = ModelContentHasher.sha256Hex(row);
        int updated = modelRegistryDao.updateContentHashIfDraft(rowId, hash);
        if (updated == 0) {
            throw immutable(rowId);
        }
        log.info("冻结认知模型版本内容哈希 rowId={} hash={}", rowId, hash);
        return requireRow(rowId);
    }

    /** 真实裁决四类发布门禁并落列（仅 DRAFT 行；返回各门 outcome+reason）。 */
    public Map<String, Object> evaluateAndRecordGates(String rowId) {
        Map<String, Object> row = requireRow(rowId);
        requireDraft(row, rowId);
        Map<String, ModelPublishGateEvaluator.GateResult> results = gateEvaluator.evaluate(row);
        int updated = modelRegistryDao.updateGatesIfDraft(rowId,
                outcome(results, GateOutcome.GATE_CONTRACT),
                outcome(results, GateOutcome.GATE_REGRESSION),
                outcome(results, GateOutcome.GATE_BACKTEST),
                outcome(results, GateOutcome.GATE_CALIBRATION));
        if (updated == 0) {
            throw immutable(rowId);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rowId", rowId);
        Map<String, Object> gates = new LinkedHashMap<>();
        results.forEach((gate, r) -> {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("outcome", r.outcome().name());
            one.put("reason", r.reason());
            gates.put(gate, one);
        });
        payload.put("gates", gates);
        payload.put("allPassed", ModelPublishGateEvaluator.allPassed(results));
        return payload;
    }

    /**
     * DRAFT → PUBLISHED 升格（规格 4）：判据 = content_hash 已冻结 且 库内四门禁列全 PASS。
     * 任一未过 ⇒ {@code ECOS-COG-422R} 显式 422 并在 data.missing 列出未过项（禁静默成功）；
     * 已 PUBLISHED ⇒ 409（不可变，重复升格亦拒）。
     */
    public Map<String, Object> publish(String rowId, String publishedBy) {
        if (publishedBy == null || publishedBy.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "publishedBy");
        }
        Map<String, Object> row = requireRow(rowId);
        if (RegistryState.isImmutable(asString(row.get("registry_state")))) {
            throw immutable(rowId);
        }
        String hash = asString(row.get("content_hash"));
        if (hash == null || hash.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E422_GATES_NOT_PASSED,
                    CognitiveErrorCode.MSG_GATES_NOT_PASSED, "content_hash");
        }
        List<String> missing = new ArrayList<>();
        for (String gate : GateOutcome.gateNames()) {
            GateOutcome outcome = GateOutcome.orPending(asString(row.get(GateOutcome.gateColumn(gate))));
            if (outcome != GateOutcome.PASS) {
                missing.add(gate + ":" + outcome.name());
            }
        }
        if (!missing.isEmpty()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E422_GATES_NOT_PASSED,
                    CognitiveErrorCode.MSG_GATES_NOT_PASSED, String.join(",", missing));
        }
        int updated = modelRegistryDao.promoteToPublishedIfReady(rowId, publishedBy);
        if (updated == 0) {
            // 库内二次防线未放行（并发升格/留痕漂移）⇒ 显式拒绝，禁静默
            throw new CognitiveGuardException(CognitiveErrorCode.E409_MODEL_IMMUTABLE,
                    CognitiveErrorCode.MSG_MODEL_IMMUTABLE, rowId);
        }
        log.info("认知模型版本升格 PUBLISHED rowId={} by={}", rowId, publishedBy);
        return requireRow(rowId);
    }

    // ── internals ──────────────────────────────────────────

    private void insertVersion(String modelId, String legacyModelColumn, CognitiveModelType cmType,
                               Map<String, Object> body, boolean legacyStatusRules) {
        String status = asString(body.get("status"));
        if (legacyStatusRules) {
            // PMO-51 原语义保留：非法 status 静默回落 active（既有端点行为，只增不改）
            if (status == null || !ALLOWED_STATUS.contains(status.toLowerCase())) {
                status = "active";
            }
        } else if (status != null && !ALLOWED_STATUS.contains(status.toLowerCase())) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "status");
        } else if (status == null) {
            status = "active";
        }

        int nextVer = modelRegistryDao.maxVersion(modelId) + 1;
        String supersedesId = modelRegistryDao.latestRowId(modelId);   // 版本链：新行指旧版（首版 null）

        String id = "corm_" + UUID.randomUUID().toString().substring(0, 12);
        String featuresJson = body.get("features") == null ? null : String.valueOf(body.get("features"));
        BigDecimal backtest = body.get("backtestScore") instanceof Number n
                ? BigDecimal.valueOf(n.doubleValue()) : null;

        modelRegistryDao.insertModel(id, modelId, legacyModelColumn, nextVer, featuresJson, backtest,
                status.toLowerCase(), cmType.code(), supersedesId);
        log.info("注册认知模型 {} v{} type={} cmType={} supersedes={}",
                modelId, nextVer, legacyModelColumn, cmType.code(), supersedesId);
    }

    private Map<String, Object> buildRegisteredRow(String modelId, Map<String, Object> body,
                                                   CognitiveModelType cmType) {
        // 回读新行以获得库内真值（registry_state/门禁默认列 + status 回落后的实际值）
        List<Map<String, Object>> rows = modelRegistryDao.getModel(modelId);
        Map<String, Object> fresh = rows.isEmpty() ? null : rows.get(0);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", fresh != null ? fresh.get("id") : null);
        row.put("modelId", modelId);
        row.put("modelType", fresh != null ? fresh.get("model_type") : cmType.code());
        row.put("cognitiveModelType", cmType.code());
        row.put("version", fresh != null ? fresh.get("version") : null);
        row.put("status", fresh != null ? fresh.get("status") : "active");
        // 只增不改的新键（REQ-COG-07）：
        row.put("registryState", fresh != null ? fresh.get("registry_state") : null);
        row.put("supersedesId", fresh != null ? fresh.get("supersedes_id") : null);
        row.put("contentHash", fresh != null ? fresh.get("content_hash") : null);
        return row;
    }

    /**
     * 显式 {@code cognitiveModelType} 处理：未给出 ⇒ empty（走旧值映射）；给出但非法字面量 ⇒
     * 直接 400 拒绝（禁静默忽略新字段退化为默认映射）。
     */
    private Optional<CognitiveModelType> explicitCmTypeOrReject(Map<String, Object> body) {
        String raw = asString(body.get("cognitiveModelType"));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Optional<CognitiveModelType> parsed = CognitiveModelType.fromCode(raw);
        if (parsed.isEmpty()) {
            throw new BusinessException(400, "CORM-400: 非法 cognitiveModelType（CM-01~CM-07）: " + raw);
        }
        return parsed;
    }

    private CognitiveGuardException unmapped(String rawValue) {
        return new CognitiveGuardException(CognitiveErrorCode.E400_MODEL_TYPE_UNMAPPED,
                CognitiveErrorCode.MSG_MODEL_TYPE_UNMAPPED,
                "cognitive_model_type=" + (rawValue == null ? "NULL" : rawValue));
    }

    private Map<String, Object> requireRow(String rowId) {
        Map<String, Object> row = modelRegistryDao.selectRowById(rowId);
        if (row == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E404_MODEL_ROW,
                    CognitiveErrorCode.MSG_MODEL_ROW_NOT_FOUND, rowId);
        }
        return row;
    }

    private void requireDraft(Map<String, Object> row, String rowId) {
        if (RegistryState.isImmutable(asString(row.get("registry_state")))) {
            throw immutable(rowId);
        }
    }

    private CognitiveGuardException immutable(String rowId) {
        return new CognitiveGuardException(CognitiveErrorCode.E409_MODEL_IMMUTABLE,
                CognitiveErrorCode.MSG_MODEL_IMMUTABLE, rowId);
    }

    private static String outcome(Map<String, ModelPublishGateEvaluator.GateResult> results, String gate) {
        return results.get(gate).outcome().name();
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
