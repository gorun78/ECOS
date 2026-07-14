package com.chinacreator.gzcm.sysman.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token 刷新成功响应 DTO。
 *
 * @param accessToken  新的 Access Token
 * @param refreshToken 新的 Refresh Token（轮换）
 */
public record TokenRefreshResponse(
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("refreshToken") String refreshToken) {
}
