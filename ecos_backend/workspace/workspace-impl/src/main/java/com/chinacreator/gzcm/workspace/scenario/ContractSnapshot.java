package com.chinacreator.gzcm.workspace.scenario;

import java.time.Instant;

/**
 * 契约探测快照（经各引擎 api 门面取回，禁直读对方 schema）。
 *
 * @param contractCount  契约记录数（映射边=ecos_entity_table_mapping 计数）
 * @param lastVerifiedAt 最近校验时间；null 表示从未校验
 */
public final class ContractSnapshot {

    private final long contractCount;
    private final Instant lastVerifiedAt;

    public ContractSnapshot(long contractCount, Instant lastVerifiedAt) {
        this.contractCount = contractCount;
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public long getContractCount() {
        return contractCount;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    /** 契约通过 = 记录数 > 0 且最近校验时间非空（卷07 F07-03-2 映射边口径）。 */
    public boolean isVerified() {
        return contractCount > 0 && lastVerifiedAt != null;
    }
}
