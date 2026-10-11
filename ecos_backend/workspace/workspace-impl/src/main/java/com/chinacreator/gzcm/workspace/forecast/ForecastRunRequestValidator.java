package com.chinacreator.gzcm.workspace.forecast;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.scenario.BaselineReferenceGuard;
import com.chinacreator.gzcm.workspace.scenario.RunMode;

/**
 * 预测 run 创建请求校验（H-5）：六要素齐备 + 幂等键/内容指纹必填 + runMode 正交解析 + baseline 守卫。
 *
 * <p>缺任一要素 → 400 fail-loud（对齐 {@code DeterministicForecastCalculator} 缺要素即拒口径，禁静默默认）。
 * runMode 复用场景面单源解析（null→FORMAL，未知→400 INVALID_RUN_MODE），
 * FORMAL 引用 SANDBOX 基线经 {@link BaselineReferenceGuard} 拦截。</p>
 */
@Component
public class ForecastRunRequestValidator {

    private final BaselineReferenceGuard baselineGuard;

    public ForecastRunRequestValidator(BaselineReferenceGuard baselineGuard) {
        this.baselineGuard = baselineGuard;
    }

    public ForecastRunCommand validate(Map<String, Object> param) {
        Map<String, Object> p = param == null ? Map.of() : param;
        String scenarioId = require(p, "scenarioId");
        String runType = require(p, "runType");
        String caliberId = require(p, "caliberId");
        String caliberVersion = require(p, "caliberVersion");
        String asOfTime = require(p, "asOfTime");
        String snapshotId = require(p, "snapshotId");
        String formulaVersion = require(p, "formulaVersion");
        String idempotencyKey = require(p, "idempotencyKey");
        String contentHash = require(p, "contentHash");

        RunMode mode = RunMode.parseOrDefault(asString(p.get("runMode")));
        String baselineRunId = asString(p.get("baselineRunId"));
        if (baselineRunId != null) {
            baselineGuard.assertAllowed(mode, baselineRunId);
        }
        // H-6 异步阈值入参（PRD-09 §七-1）：可选数值；非法数值 fail-loud 400，禁静默按 0 吞掉
        int projectCount = optionalInt(p, "projectCount");
        long rowEstimate = optionalLong(p, "rowEstimate");
        return new ForecastRunCommand(scenarioId, runType, caliberId, caliberVersion, asOfTime,
                snapshotId, formulaVersion, idempotencyKey, contentHash, mode, baselineRunId,
                null, projectCount, rowEstimate);
    }

    private int optionalInt(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "FC-RUN-400: 预测 run 阈值字段非数值: " + key);
        }
    }

    private long optionalLong(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "FC-RUN-400: 预测 run 阈值字段非数值: " + key);
        }
    }

    private String require(Map<String, Object> p, String key) {
        String v = asString(p.get(key));
        if (v == null) {
            throw new BusinessException(400, "FC-RUN-400: 预测 run 缺少必需要素: " + key);
        }
        return v;
    }

    private String asString(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
