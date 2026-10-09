package com.chinacreator.gzcm.workspace.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 必需边 present 判定（卷07 F07-02/03 的 present 语义落地）。
 * <p>present 同时要求：两端均已绑定 + 存在连边行 + 契约校验通过。探测失败降级为 missing（记 probeError），
 * 绝不抛出——端点必须保持 200，且禁止把探测失败算 present。</p>
 *
 * <p>门面经 {@link ObjectProvider} 惰性获取（引擎探测端点属其它分册，缺省时视为未通过，诚实偏低）。</p>
 */
@Component
public class EdgeContractProbe {

    private static final Logger log = LoggerFactory.getLogger(EdgeContractProbe.class);

    private final ObjectProvider<ContractProbeFacade> facadeProvider;

    public EdgeContractProbe(ObjectProvider<ContractProbeFacade> facadeProvider) {
        this.facadeProvider = facadeProvider;
    }

    /**
     * @param edge           待探测的必需边
     * @param sourceBound    边源类型是否已绑定
     * @param targetBound    边目标类型是否已绑定
     * @param linkRowExists  连边表是否存在对应 (source,target) 行
     */
    public ProbeResult probe(RequiredEdge edge, boolean sourceBound, boolean targetBound, boolean linkRowExists) {
        if (!sourceBound || !targetBound) {
            return ProbeResult.missing();
        }
        if (!linkRowExists) {
            return ProbeResult.missing();
        }
        ContractProbeFacade facade = facadeProvider.getIfAvailable();
        if (facade == null) {
            log.debug("契约探测门面未装配，边判 missing: edge={}", edge);
            return ProbeResult.missing();
        }
        ContractSnapshot snapshot;
        try {
            snapshot = facade.fetch(edge);
        } catch (Exception e) {
            log.warn("契约探测失败，降级为 missing: edge={} reason={}", edge, e.getMessage());
            return ProbeResult.missingWithError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        if (snapshot == null || !snapshot.isVerified()) {
            return ProbeResult.missing();
        }
        return ProbeResult.present(snapshot.getContractCount(), snapshot.getLastVerifiedAt());
    }
}
