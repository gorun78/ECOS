package com.chinacreator.gzcm.sysman.iam.dao;

import java.util.List;

import com.chinacreator.gzcm.sysman.iam.entity.UserRole;

public interface UserRoleDao {
    void insert(UserRole userRole) throws Exception;
    void delete(String userId, String roleId) throws Exception;
    List<UserRole> listByUserId(String userId) throws Exception;
}


