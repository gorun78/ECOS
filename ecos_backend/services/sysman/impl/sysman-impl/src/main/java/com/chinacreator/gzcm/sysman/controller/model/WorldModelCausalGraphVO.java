package com.chinacreator.gzcm.sysman.controller.model;

import java.util.List;

/** 因果图：{@code nodes/edges/nodeCount/edgeCount}。 */
public record WorldModelCausalGraphVO(
        List<WorldModelCausalNodeVO> nodes,
        List<WorldModelCausalEdgeVO> edges,
        int nodeCount,
        int edgeCount) {
}
