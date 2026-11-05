package com.chinacreator.gzcm.engine.data.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 配置缓存刷新回执（{@code POST /api/v1/engine/data/settings/refresh}）：
 * {@code refreshed} + {@code cache_size}（键名沿用既有 snake 形态）。
 */
public record DataEngineConfigRefreshVO(boolean refreshed,
                                        @JsonProperty("cache_size") int cacheSize) {
}
