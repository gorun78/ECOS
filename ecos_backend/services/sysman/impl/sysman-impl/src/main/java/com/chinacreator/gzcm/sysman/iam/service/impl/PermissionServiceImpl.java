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

    /**
     * 权限目录缓存失效腿（#147AF/D）：增删改后必须让 {@code PermissionCheckServiceImpl#catalog()}
     * 的缓存失效，否则授权判定会用到过期的 resource/action 元数据。可选装配，缺席即不缓存。
     */
    @Autowired(required = false)
    private org.springframework.beans.factory.ObjectProvider<
            com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager> catalogCacheProvider;

    @Autowired
    public PermissionServiceImpl(PermissionDao permissionDao) {
        this.permissionDao = permissionDao;
    }

    /** 目录缓存按「对象标识」整目录失效（同 key 段与读缝一致）。 */
    private void evictCatalogCache() {
        var manager = catalogCacheProvider == null ? null : catalogCacheProvider.getIfAvailable();
        if (manager == null) {
            return;
        }
        manager.evictObject(PermissionCheckServiceImpl.CATALOG_DOMAIN,
                PermissionCheckServiceImpl.CATALOG_TYPE,
                PermissionCheckServiceImpl.CATALOG_OBJECT);
    }

    @Override
    public Permission createPermission(Permission permission, String operator) throws PermissionException {
        try {
            permission.setPermissionId(permission.getPermissionId() == null ? UUID.randomUUID().toString() : permission.getPermissionId());
            permissionDao.insert(permission);
            evictCatalogCache();
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
            evictCatalogCache();
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
            evictCatalogCache();
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


