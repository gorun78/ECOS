package com.chinacreator.gzcm.sysman.iam.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.iam.entity.Role;

public interface RoleDao {
    void insert(Role role) throws Exception;
    void update(Role role) throws Exception;
    void delete(String roleId) throws Exception;
    Role findById(String roleId) throws Exception;
    Role findByCode(String roleCode) throws Exception;
    List<Role> query(Map<String, Object> condition) throws Exception;
}


