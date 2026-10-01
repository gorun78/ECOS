package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive2.dao.ModelRegistryDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 认知模型注册表服务（PMO-51 T3 / ADR-8）。
 *
 * <p>ADR-8 口径：推理"结果"不落盘；模型"资产"（注册/版本/回测）可落
 * {@code ecos_cognitive_model}。本服务只做模型资产的 CRUD，不参与推理计算。
 * SQL 落点已收口至 {@link ModelRegistryDao}（架构铁律 §2.5 #1 / IR06，B2-b）。</p>
 */
@Service
public class ModelRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ModelRegistryService.class);
    private static final Set<String> ALLOWED_TYPES = Set.of("FORECAST", "CAUSAL", "SIMULATION", "DECISION");
    private static final Set<String> ALLOWED_STATUS = Set.of("active", "retired");

    private final ModelRegistryDao modelRegistryDao;

    public ModelRegistryService(ModelRegistryDao modelRegistryDao) {
        this.modelRegistryDao = modelRegistryDao;
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
     * 注册模型：同 model_id 新增下一个 version（version = max+1，首版为 1）。
     *
     * @param body 入参 modelId(必填) / modelType(必填) / features / backtestScore / status
     * @return 注册后的行
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
        String status = asString(body.get("status"));
        if (status == null || !ALLOWED_STATUS.contains(status.toLowerCase())) {
            status = "active";
        }

        int nextVer = modelRegistryDao.maxVersion(modelId) + 1;

        String id = "corm_" + UUID.randomUUID().toString().substring(0, 12);
        String featuresJson = body.get("features") == null ? null : String.valueOf(body.get("features"));
        BigDecimal backtest = body.get("backtestScore") instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : null;

        modelRegistryDao.insertModel(
            id, modelId, modelType.toUpperCase(), nextVer, featuresJson, backtest, status.toLowerCase());
        log.info("注册认知模型 {} v{} type={}", modelId, nextVer, modelType);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("modelId", modelId);
        row.put("modelType", modelType.toUpperCase());
        row.put("version", nextVer);
        row.put("status", status.toLowerCase());
        return row;
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
