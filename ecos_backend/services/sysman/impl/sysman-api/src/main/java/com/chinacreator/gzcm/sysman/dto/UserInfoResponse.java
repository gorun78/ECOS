package com.chinacreator.gzcm.sysman.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 当前用户信息响应 DTO。
 *
 * @param username 用户名
 * @param userId   用户 ID
 * @param roles    角色列表
 */
public record UserInfoResponse(
        @JsonProperty("username") String username,
        @JsonProperty("userId") String userId,
        @JsonProperty("roles") List<String> roles) {
}
