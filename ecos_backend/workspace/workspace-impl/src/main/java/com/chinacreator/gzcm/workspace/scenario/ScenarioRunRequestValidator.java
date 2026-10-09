package com.chinacreator.gzcm.workspace.scenario;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 场景运行请求校验（卷07 F07-08/09/14：runMode 解析默认 FORMAL + scenarioId 一致性 + baseline 守卫）。
 * <p>请求体附加字段全部可选，旧 body（仅 runTypes/metric/mindId）行为不变、默认 FORMAL（A31 向后兼容）。
 * 未知 run_mode → 400 INVALID_RUN_MODE；FORMAL 引用演练基线 → 400 SANDBOX_REF_FORBIDDEN。</p>
 */
@Component
public class ScenarioRunRequestValidator {

    private final BaselineReferenceGuard baselineGuard;

    public ScenarioRunRequestValidator(BaselineReferenceGuard baselineGuard) {
        this.baselineGuard = baselineGuard;
    }

    /** 解析并校验附加字段，返回规范化运行命令。 */
    public RunCommand validate(String pathScenarioId, Map<String, Object> param) {
        Map<String, Object> p = param == null ? Map.of() : param;
        RunMode mode = RunMode.parseOrDefault(asString(p.get("runMode")));

        String bodyScenarioId = asString(p.get("scenarioId"));
        if (bodyScenarioId != null && !bodyScenarioId.equals(pathScenarioId)) {
            throw new com.chinacreator.gzcm.common.exception.BusinessException(400,
                    "RUN-400: 请求体 scenarioId 与路径不一致: body=" + bodyScenarioId + " path=" + pathScenarioId);
        }

        String baselineRunId = asString(p.get("baselineRunId"));
        if (baselineRunId != null) {
            baselineGuard.assertAllowed(mode, baselineRunId);
        }
        return new RunCommand(pathScenarioId, mode, baselineRunId);
    }

    private String asString(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    /** 规范化运行命令。 */
    public static final class RunCommand {
        private final String scenarioId;
        private final RunMode runMode;
        private final String baselineRunId;

        public RunCommand(String scenarioId, RunMode runMode, String baselineRunId) {
            this.scenarioId = scenarioId;
            this.runMode = runMode;
            this.baselineRunId = baselineRunId;
        }

        public String getScenarioId() {
            return scenarioId;
        }

        public RunMode getRunMode() {
            return runMode;
        }

        public String getBaselineRunId() {
            return baselineRunId;
        }
    }
}
