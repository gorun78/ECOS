package com.chinacreator.gzcm.sysman.dto;

/**
 * 用户需求字段 DTO — 单条用户信息，可单独使用也可嵌套进 UserBatchSaveDTO。
 * 批量创建时 password 可为空（缺省则随机生成 12 位）。
 */
public class UserSaveDTO {
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String orgId;
    /** 密码；批量场景下可为空（服务端生成随机密码，仅在本次响应中返回） */
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
