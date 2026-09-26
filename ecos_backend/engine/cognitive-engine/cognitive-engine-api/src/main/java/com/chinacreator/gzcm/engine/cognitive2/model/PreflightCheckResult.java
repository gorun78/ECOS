package com.chinacreator.gzcm.engine.cognitive2.model;

import lombok.Data;

import java.util.List;

/**
 * 推理前预检结果 DTO — V2 原型 §02 情境诊断 Step 5。
 *
 * <p>包含 5 项预检的明细（上下文加载 / 模型就绪 / 权限 / Neo4j 预期 / LLM 网关可达性），
 * 以及整体状态（PASSED / DEGRADED / FAILED）。</p>
 */
@Data
public class PreflightCheckResult {

    /** 整体状态：PASSED / DEGRADED / FAILED */
    private String status;

    /** 各预检项明细 */
    private List<CheckItem> checks;

    /** 预检执行时间（Unix 毫秒） */
    private long timestamp;

    /**
     * 单项预检结果。
     */
    @Data
    public static class CheckItem {
        /** 检查项名称，如 "context_loaded"、"model_ready" */
        private String name;
        /** 是否通过 */
        private boolean passed;
        /** 人类可读的原因说明 */
        private String message;
    }
}
