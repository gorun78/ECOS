package com.chinacreator.gzcm.sysman.dto;

import java.util.List;

/**
 * 能力市场资产行（内存 store 的唯一行模型）。
 *
 * <p>键面与顺序：{@code id/name/nameZh/description/category/status/owner/version/tags/createdAt/
 * updatedAt}；{@code tags} 为字符串数组，时间字段为毫秒数（与旧 Map 同值）。</p>
 */
public record MarketplaceAssetVO(
        String id,
        String name,
        String nameZh,
        String description,
        String category,
        String status,
        String owner,
        String version,
        List<String> tags,
        long createdAt,
        long updatedAt) {
}
