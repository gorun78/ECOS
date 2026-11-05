package com.chinacreator.gzcm.sysman.dto;

import java.util.List;
import java.util.Map;

/**
 * 市场仪表盘（{@code GET /api/v1/marketplace/dashboard}）：
 * 总量 + 分类/状态分布 + 最近 5 条 + 生成时刻毫秒。
 */
public record MarketplaceDashboardVO(
        int totalAssets,
        Map<String, Long> byCategory,
        Map<String, Long> byStatus,
        List<MarketplaceAssetVO> recentAssets,
        long timestamp) {
}
