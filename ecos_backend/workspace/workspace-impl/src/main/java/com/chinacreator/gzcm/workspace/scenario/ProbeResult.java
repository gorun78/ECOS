package com.chinacreator.gzcm.workspace.scenario;

import java.time.Instant;

/**
 * 单条必需边的探测结果（present 语义落地）。
 *
 * <p>present = 两端均已绑定 ∧ 存在连边行 ∧ 契约通过。契约探测失败时 present=false 且 probeError 非空，
 * 但绝不抛出（端点仍 200，见 F07-03-2）。contractCount/lastVerifiedAt 作为 hover 契约来源随 presentEdges 返回。</p>
 */
public final class ProbeResult {

    private final boolean present;
    private final long contractCount;
    private final Instant lastVerifiedAt;
    private final String probeError;

    private ProbeResult(boolean present, long contractCount, Instant lastVerifiedAt, String probeError) {
        this.present = present;
        this.contractCount = contractCount;
        this.lastVerifiedAt = lastVerifiedAt;
        this.probeError = probeError;
    }

    public static ProbeResult present(long contractCount, Instant lastVerifiedAt) {
        return new ProbeResult(true, contractCount, lastVerifiedAt, null);
    }

    public static ProbeResult missing() {
        return new ProbeResult(false, 0, null, null);
    }

    public static ProbeResult missingWithError(String probeError) {
        return new ProbeResult(false, 0, null, probeError);
    }

    public boolean isPresent() {
        return present;
    }

    public long getContractCount() {
        return contractCount;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public String getProbeError() {
        return probeError;
    }
}
