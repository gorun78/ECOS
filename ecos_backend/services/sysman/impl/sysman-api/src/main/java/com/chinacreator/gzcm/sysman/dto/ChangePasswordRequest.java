package com.chinacreator.gzcm.sysman.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 修改密码请求 DTO（首次登录强制修改 / 主动修改）。
 *
 * @param changeToken  change-password JWT（登录时颁发）
 * @param newPassword  新密码
 */
public record ChangePasswordRequest(
        @JsonProperty("changeToken") String changeToken,
        @JsonProperty("newPassword") String newPassword) {

    @JsonCreator
    public static ChangePasswordRequest of(
            @JsonProperty("changeToken") String changeToken,
            @JsonProperty("newPassword") String newPassword) {
        return new ChangePasswordRequest(changeToken, newPassword);
    }
}
