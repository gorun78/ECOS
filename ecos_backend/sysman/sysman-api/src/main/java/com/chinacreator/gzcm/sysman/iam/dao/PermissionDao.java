package com.chinacreator.gzcm.sysman.iam.dao;

import java.util.List;

import com.chinacreator.gzcm.sysman.iam.entity.Permission;

public interface PermissionDao {
    void insert(Permission permission) throws Exception;
    void update(Permission permission) throws Exception;
    void delete(String permissionId) throws Exception;
    Permission findById(String permissionId) throws Exception;
    Permission findByResourceAction(String resource, String action) throws Exception;
    List<Permission> listAll() throws Exception;
}


