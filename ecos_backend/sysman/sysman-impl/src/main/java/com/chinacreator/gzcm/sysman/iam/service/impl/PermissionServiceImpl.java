package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.sysman.iam.dao.PermissionDao;
import com.chinacreator.gzcm.sysman.iam.entity.Permission;
import com.chinacreator.gzcm.sysman.iam.service.IPermissionService;

@Service
public class PermissionServiceImpl implements IPermissionService {

    private final PermissionDao permissionDao;

    @Autowired
    public PermissionServiceImpl(PermissionDao permissionDao) {
        this.permissionDao = permissionDao;
    }

    @Override
    public Permission createPermission(Permission permission, String operator) throws PermissionException {
        try {
            permission.setPermissionId(permission.getPermissionId() == null ? UUID.randomUUID().toString() : permission.getPermissionId());
            permissionDao.insert(permission);
            return permissionDao.findById(permission.getPermissionId());
        } catch (Exception e) {
            throw new PermissionException("创建权限失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Permission updatePermission(Permission permission, String operator) throws PermissionException {
        try {
            Permission existing = permissionDao.findById(permission.getPermissionId());
            if (existing == null) throw new PermissionException("权限不存在");
            if (permission.getResource() != null) existing.setResource(permission.getResource());
            if (permission.getAction() != null) existing.setAction(permission.getAction());
            if (permission.getConditionExpr() != null) existing.setConditionExpr(permission.getConditionExpr());
            if (permission.getDescription() != null) existing.setDescription(permission.getDescription());
            permissionDao.update(existing);
            return permissionDao.findById(existing.getPermissionId());
        } catch (PermissionException e) {
            throw e;
        } catch (Exception e) {
            throw new PermissionException("更新权限失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deletePermission(String permissionId, String operator) throws PermissionException {
        try {
            permissionDao.delete(permissionId);
        } catch (Exception e) {
            throw new PermissionException("删除权限失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Permission getPermission(String permissionId) throws PermissionException {
        try {
            return permissionDao.findById(permissionId);
        } catch (Exception e) {
            throw new PermissionException("获取权限失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Permission> listPermissions() throws PermissionException {
        try {
            return permissionDao.listAll();
        } catch (Exception e) {
            throw new PermissionException("查询权限列表失败: " + e.getMessage(), e);
        }
    }
}


