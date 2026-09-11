package com.chinacreator.gzcm.sysman.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Refresh Token 请求 DTO。
 *
 * @param refreshToken 刷新令牌
 */
public record RefreshTokenRequest(
        @JsonProperty("refreshToken") String refreshToken) {
}
