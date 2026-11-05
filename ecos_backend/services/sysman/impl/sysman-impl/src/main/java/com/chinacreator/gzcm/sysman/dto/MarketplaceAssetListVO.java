package com.chinacreator.gzcm.sysman.dto;

import java.util.List;

/** 资产列表（{@code GET /api/v1/marketplace/assets}）：{@code data + total}。 */
public record MarketplaceAssetListVO(List<MarketplaceAssetVO> data, int total) {
}
