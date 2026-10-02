package com.chinacreator.gzcm.workspace.scenario;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 必需边契约探测（详细设计-07 F07-03-2）。
 *
 * <p>判定一条必需边「present」的必要条件：两端类型均已绑定 + 存在该类型连边 + 契约校验通过。
 * 探测<b>失败一律降级为 missing</b>（present=false, probeError 非空），既不把探测失败算 present，
 * 也不让端点 500（C-1 步骤 4 / 验收 probeFailureMarksEdgeMissingNotError）。</p>
 *
 * <p>本实现（{@link LinkContractProbe}）是最小本地探针：读连边表事实（{@code source_contract}），
 * MAPPING 边要求契约引用非空且非 {@code placeholder-}（破 X-48 占位契约骗画布）；抽取/认知边的
 * 权威校验在跨引擎 api 门面（分册 02/04/06 属主），当前以连边存在为本地判据并诚实标注
 * {@code lastVerifiedAt} 为空，跨引擎探测点是后续接缝（8.5），不在此虚构远端可达。</p>
 */
public interface EdgeContractProbe {

    /** 绑定节点引用（探测输入）。 */
    record BindingRef(String id, String type, String targetRef) {
    }

    /** 连边引用（探测输入，来自 ecos_scenario_binding_link）。 */
    record LinkRef(String sourceBindingId, String targetBindingId, String linkType, String sourceContract) {
    }

    /** 探测结果。 */
    record EdgePresence(boolean present, int contractCount, String lastVerifiedAt, String probeError) {
        static EdgePresence missing(String error) {
            return new EdgePresence(false, 0, null, error);
        }
    }

    /** 探测一条必需边是否在场景内成立。 */
    EdgePresence probe(RequiredEdgePolicy.RequiredEdge edge,
                       List<BindingRef> bindings, List<LinkRef> links);
}

/**
 * 默认本地契约探针（F07-03-2 最小可测实现）。
 */
@Component
class LinkContractProbe implements EdgeContractProbe {

    private static final String PLACEHOLDER_PREFIX = "placeholder-";

    @Override
    public EdgePresence probe(RequiredEdgePolicy.RequiredEdge edge,
                              List<BindingRef> bindings, List<LinkRef> links) {
        // 两端类型均已绑定
        boolean sourceBound = bindings.stream().anyMatch(b -> edge.sourceType().equals(b.type()));
        boolean targetBound = bindings.stream().anyMatch(b -> edge.targetType().equals(b.type()));
        if (!sourceBound || !targetBound) {
            return EdgePresence.missing(null); // 端点未齐 → 边缺失，非错误
        }

        // 存在该类型连边，且双端节点类型符合规范起/终
        int count = 0;
        String verifiedAt = null;
        for (LinkRef l : links) {
            if (!edge.edgeType().equalsIgnoreCase(l.linkType())) {
                continue;
            }
            BindingRef s = find(bindings, l.sourceBindingId());
            BindingRef t = find(bindings, l.targetBindingId());
            if (s == null || t == null) {
                continue;
            }
            if (!edge.sourceType().equals(s.type()) || !edge.targetType().equals(t.type())) {
                continue;
            }
            // 契约校验：MAPPING 要求 source_contract 非空且非占位
            if ("MAPPING".equals(edge.edgeType())) {
                String c = l.sourceContract();
                if (c == null || c.isBlank() || c.startsWith(PLACEHOLDER_PREFIX)) {
                    continue; // 占位/空契约不算 present（X-48）
                }
            }
            count++;
        }
        if (count == 0) {
            return EdgePresence.missing(null);
        }
        return new EdgePresence(true, count, verifiedAt, null);
    }

    private BindingRef find(List<BindingRef> bindings, String id) {
        for (BindingRef b : bindings) {
            if (b.id().equals(id)) {
                return b;
            }
        }
        return null;
    }
}
