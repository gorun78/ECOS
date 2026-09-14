package com.chinacreator.gzcm.sysman.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 登录请求 DTO。
 *
 * @param username 用户名
 * @param password 密码
 */
public record LoginRequest(
        @JsonProperty("username") String username,
        @JsonProperty("password") String password) {

    @JsonCreator
    public static LoginRequest of(@JsonProperty("username") String username,
                                  @JsonProperty("password") String password) {
        return new LoginRequest(username, password);
    }
}
