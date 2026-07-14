package com.chinacreator.gzcm.runtime.core.quality;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 质量评估结果 — 一次质量检查的输出。
 */
public class QualityResult {

    private final String ruleId;
    private final String target;
    private final boolean passed;
    private final String message;
    private final long totalRows;
    private final long failedRows;
    private final List<String> sampleFailures;
    private final Instant evaluatedAt;

    public QualityResult(String ruleId, String target, boolean passed, String message,
                         long totalRows, long failedRows, List<String> sampleFailures) {
        this.ruleId = ruleId;
        this.target = target;
        this.passed = passed;
        this.message = message;
        this.totalRows = totalRows;
        this.failedRows = failedRows;
        this.sampleFailures = sampleFailures != null
            ? Collections.unmodifiableList(sampleFailures)
            : Collections.emptyList();
        this.evaluatedAt = Instant.now();
    }

    public String getRuleId() { return ruleId; }
    public String getTarget() { return target; }
    public boolean isPassed() { return passed; }
    public String getMessage() { return message; }
    public long getTotalRows() { return totalRows; }
    public long getFailedRows() { return failedRows; }
    public List<String> getSampleFailures() { return sampleFailures; }
    public Instant getEvaluatedAt() { return evaluatedAt; }

    public double getPassRate() {
        return totalRows > 0 ? (double) (totalRows - failedRows) / totalRows : 1.0;
    }

    @Override
    public String toString() {
        return "QualityResult{" + ruleId + " " + (passed ? "PASS" : "FAIL")
            + " [" + (totalRows - failedRows) + "/" + totalRows + "]}";
    }
}
