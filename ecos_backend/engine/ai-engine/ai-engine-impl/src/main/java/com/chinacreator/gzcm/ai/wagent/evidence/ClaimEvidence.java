package com.chinacreator.gzcm.ai.wagent.evidence;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ClaimType;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 分册10 F10-24 · Claim / Evidence / ClaimEvidenceRel 三 contracts
 * （Claim 数值来源必须归 {@link ValueSource}，否则 E-WA-NARRATIVE-NUM）。
 *
 * <p>不含 double/float。{@code numericValue} 一律 {@link BigDecimal}；
 * {@code evidenceId} 由 wiring 层生成；Kafka 事件由 wiring 层发。</p>
 */
public final class ClaimEvidence {

    private ClaimEvidence() {}

    /** 单条证据（感知层溯源，Readonly 从引擎回读）。 */
    public record Evidence(
            String evidenceId,
            String engine,
            String engineRefPath,
            String contentJson,
            Instant capturedAt) {
        public Evidence {
            if (evidenceId == null) throw new IllegalArgumentException("evidenceId null");
            if (engine == null) throw new IllegalArgumentException("engine null");
            if (engineRefPath == null) throw new IllegalArgumentException("engineRefPath null");
        }
    }

    /** 单条 Claim：数值必带 value_source；非可数值 claim 允许 numericValue 为 null。 */
    public record Claim(
            String claimId,
            String runId,
            ClaimType type,
            boolean isNumeric,
            BigDecimal numericValue,
            ValueSource valueSource,
            String narrativeText,
            String evidenceGrade) {
        public Claim {
            if (claimId == null) throw new IllegalArgumentException("claimId null");
            if (runId == null) throw new IllegalArgumentException("runId null");
            if (type == null) throw new IllegalArgumentException("type null");
        }
    }

    /** Claim ↔ Evidence 多对多关联（含引擎端追溯指向）。 */
    public record ClaimEvidenceRel(
            String claimId,
            String evidenceId,
            int relevance,
            String provenanceNote) {}
}
