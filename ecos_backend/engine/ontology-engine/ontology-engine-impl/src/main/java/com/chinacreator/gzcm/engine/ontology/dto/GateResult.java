package com.chinacreator.gzcm.engine.ontology.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * F03-03 / 详细设计-03 §D.3 — {@code PublishGateService.validate(...)} orchestrator result.
 *
 * <p>Three-state semantics (铁律 §2.4-8):</p>
 * <ul>
 *   <li><b>passed=true, violations empty</b> → proceed with {@code executeAndPublish}.</li>
 *   <li><b>passed=false, violations non-empty</b> → return 400 with the violation list; proposal stays PENDING.</li>
 *   <li><b>dependencyErrors non-empty</b> → return 503 {@code ECOS-ONTO-050}; MUST NOT proceed (fail-closed).</li>
 * </ul>
 *
 * <p>{@code dependencyErrors} takes precedence over {@code passed} in the controller wiring:
 * a non-empty dependency list is fatal regardless of {@code violations}.</p>
 */
public class GateResult {

    private boolean passed;
    private List<GateViolation> violations = new ArrayList<>();
    private Instant evaluatedAt = Instant.now();
    private List<DependencyError> dependencyErrors = new ArrayList<>();

    /** Whether all three gates (V1/V2/V3) passed AND no dependencyErrors were raised. */
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public List<GateViolation> getViolations() { return violations == null ? (violations = new ArrayList<>()) : violations; }
    public void setViolations(List<GateViolation> violations) { this.violations = violations; }

    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    public List<DependencyError> getDependencyErrors() { return dependencyErrors == null ? (dependencyErrors = new ArrayList<>()) : dependencyErrors; }
    public void setDependencyErrors(List<DependencyError> dependencyErrors) { this.dependencyErrors = dependencyErrors; }

    /** True when a required cross-engine dependency (data-engine metadata, caliber service) is unavailable. */
    public boolean hasDependencyErrors() {
        return dependencyErrors != null && !dependencyErrors.isEmpty();
    }

    // ── F03-03 门禁标签 (与 D.3 契约一致) ──────────────────────────────

    public static final String GATE_V1_CALIBER = "V1_CALIBER";
    public static final String GATE_V2_UNIT = "V2_UNIT";
    public static final String GATE_V3_LINEAGE = "V3_LINEAGE";

    // ── F03-03 错误码 (详细设计-03 §C.3 中央错误码表) ────────────────

    /** V1: 发布态指标缺 caliber_id 或口径版本非 APPROVED. */
    public static final String CODE_CALIBER_METRIC_MISSING = "ECOS-ONTO-040";
    /** V1: 口径服务内部失败（fail-closed，500）. */
    public static final String CODE_CALIBER_SERVICE_UNAVAILABLE = "ECOS-ONTO-042";
    /** V2: 单位推导冲突（四则代数不适用当前组合）. */
    public static final String CODE_UNIT_CONFLICT = "ECOS-ONTO-021";
    /** V2: 不可加指标（比率/月）被声明为可加聚合. */
    public static final String CODE_ADDITIVITY_CONFLICT = "ECOS-ONTO-022";
    /** V3: 血缘列在事实表上不存在（真实元数据已成功返回，但列缺失）. */
    public static final String CODE_LINEAGE_COLUMN_MISSING = "ECOS-ONTO-030";
    /** V3: data-engine 元数据 REST 不可用（resolver 空 / 404 / 5xx）= 503 fail-closed. */
    public static final String CODE_DEPENDENCY_UNAVAILABLE = "ECOS-ONTO-050";

    // ── 嵌套 DTO ────────────────────────────────────────────────────

    /** F03-03 §D.3 — single gate violation; {@code gate ∈ [V1_CALIBER,V2_UNIT,V3_LINEAGE]}. */
    public static class GateViolation {
        private String gate;
        private String code;
        private String message;
        private List<String> refs = new ArrayList<>();

        public GateViolation() {
        }

        public GateViolation(String gate, String code, String message) {
            this.gate = gate;
            this.code = code;
            this.message = message;
        }

        public GateViolation addRef(String ref) {
            if (refs == null) refs = new ArrayList<>();
            if (ref != null && !ref.isBlank()) refs.add(ref);
            return this;
        }

        public String getGate() { return gate; }
        public void setGate(String gate) { this.gate = gate; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<String> getRefs() { return refs == null ? (refs = new ArrayList<>()) : refs; }
        public void setRefs(List<String> refs) { this.refs = refs; }

        @Override
        public String toString() {
            return gate + "/" + code + " " + message + " refs=" + refs;
        }
    }

    /** F03-03 §D.3 — cross-engine dependency unavailable; non-empty implies 503 ECOS-ONTO-050. */
    public static class DependencyError {
        private String dependency;
        private String code;

        public DependencyError() {
        }

        public DependencyError(String dependency, String code) {
            this.dependency = dependency;
            this.code = code;
        }

        public String getDependency() { return dependency; }
        public void setDependency(String dependency) { this.dependency = dependency; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        @Override
        public String toString() {
            return "DependencyError{dependency=" + dependency + ", code=" + code + "}";
        }
    }
}
