package com.chinacreator.gzcm.sysman.dto;

/**
 * 访问申请回执（{@code POST /api/v1/marketplace/request-access}）：
 * {@code requestId/assetId/requester/reason/status/createdAt}（状态恒 pending）。
 */
public record MarketplaceAccessRequestVO(
        String requestId,
        String assetId,
        String requester,
        String reason,
        String status,
        long createdAt) {
}
