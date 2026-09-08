package com.chinacreator.gzcm.sysman.dto;

import java.util.List;

/**
 * 批量保存用户 DTO — 一次最多 100 条。
 * 用于 POST /api/v1/system/users/batch
 */
public class UserBatchSaveDTO {

    /** 批量导入标志（true=导入模式，重复 username 跳过；false=严格模式，重复报错） */
    private boolean importMode = true;

    /** 新用户列表，<=100 条 */
    private List<UserSaveDTO> users;

    public boolean isImportMode() { return importMode; }
    public void setImportMode(boolean importMode) { this.importMode = importMode; }

    public List<UserSaveDTO> getUsers() { return users; }
    public void setUsers(List<UserSaveDTO> users) { this.users = users; }
}
