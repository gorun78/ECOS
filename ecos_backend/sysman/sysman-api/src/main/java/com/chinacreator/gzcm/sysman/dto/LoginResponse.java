package com.chinacreator.gzcm.sysman.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 登录成功响应 DTO。
 *
 * @param accessToken  JWT Access Token
 * @param refreshToken Refresh Token（用于续期）
 * @param username     用户名
 * @param userId       用户 ID
 * @param roles        角色列表
 */
public record LoginResponse(
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("username") String username,
        @JsonProperty("userId") String userId,
        @JsonProperty("roles") List<String> roles) {
}
