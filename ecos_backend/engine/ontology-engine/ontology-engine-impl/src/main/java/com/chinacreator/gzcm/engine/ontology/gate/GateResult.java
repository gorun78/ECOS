package com.chinacreator.gzcm.engine.ontology.gate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 门禁评估结果（F03-03 / D.3 GateResult）。
 *
 * <p>三态语义：{@code passed=true} 继续执行；{@code passed=false} 且 {@code dependencyErrors} 为空
 * → 提案停留原状态并返回逐条违反项；{@code dependencyErrors} 非空 → 整体 503 {@code ECOS-ONTO-050}
 * （依赖不可用，fail-closed，禁默认通过，铁律 §2.4-8）。
 */
public class GateResult {

    private boolean passed;
    private List<GateViolation> violations = new ArrayList<>();
    private Instant evaluatedAt = Instant.now();
    private List<DependencyError> dependencyErrors = new ArrayList<>();
    /** 口径服务内部失败（fail-closed，非空即按 serviceHttpStatus 拒绝，一般为 500 ECOS-ONTO-042）。 */
    private String serviceErrorCode;
    private int serviceHttpStatus;

    // ── 依赖不可用（如 V3 调 data-engine 失败）。非空即整体 503
    public static class DependencyError {
        public final String dependency;
        public final String code;
        public final String message;

        public DependencyError(String dependency, String code, String message) {
            this.dependency = dependency;
            this.code = code;
            this.message = message;
        }
    }

    public static GateResult of(boolean passed, List<GateViolation> violations, List<DependencyError> deps) {
        GateResult r = new GateResult();
        r.passed = passed;
        r.violations = violations != null ? violations : new ArrayList<>();
        r.dependencyErrors = deps != null ? deps : new ArrayList<>();
        r.evaluatedAt = Instant.now();
        return r;
    }

    /** 依赖可用性短路：任一 V3 依赖不可用 → 整体不可通过且附 dependencyError（503）。 */
    public static GateResult dependencyDown(String dependency, String code, String message) {
        List<DependencyError> deps = new ArrayList<>();
        deps.add(new DependencyError(dependency, code, message));
        GateResult r = new GateResult();
        r.passed = false;
        r.dependencyErrors = deps;
        r.evaluatedAt = Instant.now();
        return r;
    }

    /** 口径服务内部失败 → fail-closed，按给定 httpStatus（500 ECOS-ONTO-042）拒绝。 */
    public static GateResult serviceUnavailable(String code, int httpStatus, String message) {
        GateResult r = new GateResult();
        r.passed = false;
        r.serviceErrorCode = code;
        r.serviceHttpStatus = httpStatus;
        r.evaluatedAt = Instant.now();
        r.violations.add(new GateViolation(GateViolation.Gate.V1_CALIBER, code, message + "（口径服务不可用，fail-closed 未发布）"));
        return r;
    }

    public boolean isPassed() { return passed; }
    public boolean hasDependencyErrors() { return !dependencyErrors.isEmpty(); }
    public boolean hasServiceError() { return serviceErrorCode != null; }
    public String getServiceErrorCode() { return serviceErrorCode; }
    public int getServiceHttpStatus() { return serviceHttpStatus; }
    public List<GateViolation> getViolations() { return violations; }
    public List<DependencyError> getDependencyErrors() { return dependencyErrors; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
}
