package com.chinacreator.gzcm.engine.kb.nav.model;

import java.util.List;

/**
 * 资产批量状态 VO — {@code GET /api/v1/knowledge/assets/status?ids=1,2,3} 返回体（F10）。
 *
 * <p>批量查询知识资产的图谱/向量双写状态，单次 ≤ 100 ids，
 * 双引擎（graph_node + knowledge_embedding）并行批查（CompletableFuture.allOf），
 * 各侧 ≤ 30ms timeout，总响应 ≤ 50ms。
 *
 * @since PMO-D 2026-09-24
 */
public class AssetStatusVO {

    /** 资产状态项列表（每项含 articleId + graph + vector） */
    private List<AssetStatusItemVO> items;

    public AssetStatusVO() {
    }

    public List<AssetStatusItemVO> getItems() { return items; }
    public void setItems(List<AssetStatusItemVO> items) { this.items = items; }
}
