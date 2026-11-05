package com.chinacreator.gzcm.sysman.dto;

import java.util.List;

/**
 * 资产搜索（{@code GET /api/v1/marketplace/search}）：{@code data/total/query}；
 * {@code query} 键恒出现（未传时为 null，与旧 Map 同形）。
 */
public record MarketplaceAssetSearchVO(List<MarketplaceAssetVO> data, int total, String query) {
}
