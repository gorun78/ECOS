package com.chinacreator.gzcm.sysman.dto;

import java.util.List;

/**
 * Portal 全局搜索应答（{@code GET /api/v1/portal/search}）：
 * {@code q/type/total/items/rlsApplied}；{@code rlsApplied} 表示请求已带认证身份。
 */
public record PortalSearchResultVO(
        String q,
        String type,
        int total,
        List<PortalSearchHitVO> items,
        boolean rlsApplied) {
}
