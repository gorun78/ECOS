package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.sysman.iam.cache.PermissionCacheService;
import com.chinacreator.gzcm.sysman.iam.dao.PermissionDao;
import com.chinacreator.gzcm.sysman.iam.dao.RoleDao;
import com.chinacreator.gzcm.sysman.iam.dao.RolePermissionDao;
import com.chinacreator.gzcm.sysman.iam.entity.Permission;
import com.chinacreator.gzcm.sysman.iam.entity.Role;
import com.chinacreator.gzcm.sysman.iam.entity.RolePermission;
import com.chinacreator.gzcm.sysman.iam.service.IRoleService;

@Service
public class RoleServiceImpl implements IRoleService {

    private final RoleDao roleDao;
    private final PermissionDao permissionDao;
    private final RolePermissionDao rolePermissionDao;
    private final PermissionCacheService permissionCacheService;

    @Autowired
    public RoleServiceImpl(RoleDao roleDao, PermissionDao permissionDao, RolePermissionDao rolePermissionDao) {
        this(roleDao, permissionDao, rolePermissionDao, null);
    }

    public RoleServiceImpl(RoleDao roleDao, PermissionDao permissionDao, RolePermissionDao rolePermissionDao, PermissionCacheService permissionCacheService) {
        this.roleDao = roleDao;
        this.permissionDao = permissionDao;
        this.rolePermissionDao = rolePermissionDao;
        this.permissionCacheService = permissionCacheService;
    }

    @Override
    public Role createRole(Role role, String operator) throws RoleException {
        try {
            if (role.getRoleCode() != null && roleDao.findByCode(role.getRoleCode()) != null) {
                throw new RoleException("角色编码已存在");
            }
            
            // 验证父角色是否存在，防止循环继承
            if (role.getParentRoleId() != null) {
                Role parentRole = roleDao.findById(role.getParentRoleId());
                if (parentRole == null) {
                    throw new RoleException("父角色不存在: " + role.getParentRoleId());
                }
                // 检查是否会造成循环继承
                if (wouldCauseCircularInheritance(role.getParentRoleId(), null)) {
                    throw new RoleException("设置父角色会导致循环继承");
                }
            }
            
            role.setRoleId(role.getRoleId() == null ? UUID.randomUUID().toString() : role.getRoleId());
            if (role.getStatus() == null) {
                role.setStatus("ACTIVE");
            }
            Date now = new Date();
            role.setCreatedTime(now);
            role.setUpdatedTime(now);
            role.setCreatedBy(operator);
            role.setUpdatedBy(operator);
            roleDao.insert(role);
            return roleDao.findById(role.getRoleId());
        } catch (RoleException e) {
            throw e;
        } catch (Exception e) {
            throw new RoleException("创建角色失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查是否会造成循环继承
     */
    private boolean wouldCauseCircularInheritance(String parentRoleId, String currentRoleId) throws Exception {
        if (parentRoleId == null) {
            return false;
        }
        if (parentRoleId.equals(currentRoleId)) {
            return true; // 检测到循环
        }
        Role parentRole = roleDao.findById(parentRoleId);
        if (parentRole == null || parentRole.getParentRoleId() == null) {
            return false;
        }
        // 递归检查父角色的父角色
        return wouldCauseCircularInheritance(parentRole.getParentRoleId(), currentRoleId);
    }

    @Override
    public Role updateRole(Role role, String operator) throws RoleException {
        try {
            Role existing = roleDao.findById(role.getRoleId());
            if (existing == null) throw new RoleException("角色不存在");
            
            // 如果更新父角色，需要验证是否会造成循环继承
            if (role.getParentRoleId() != null && !role.getParentRoleId().equals(existing.getParentRoleId())) {
                if (wouldCauseCircularInheritance(role.getParentRoleId(), role.getRoleId())) {
                    throw new RoleException("设置父角色会导致循环继承");
                }
            }
            
            if (role.getRoleName() != null) existing.setRoleName(role.getRoleName());
            if (role.getRoleType() != null) existing.setRoleType(role.getRoleType());
            if (role.getDescription() != null) existing.setDescription(role.getDescription());
            if (role.getTenantId() != null) existing.setTenantId(role.getTenantId());
            if (role.getParentRoleId() != null) existing.setParentRoleId(role.getParentRoleId());
            if (role.getStatus() != null) existing.setStatus(role.getStatus());
            existing.setUpdatedBy(operator);
            existing.setUpdatedTime(new Date());
            roleDao.update(existing);
            evictPermissionCache();
            return roleDao.findById(existing.getRoleId());
        } catch (RoleException e) {
            throw e;
        } catch (Exception e) {
            throw new RoleException("更新角色失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteRole(String roleId, String operator) throws RoleException {
        try {
            roleDao.delete(roleId);
            evictPermissionCache();
        } catch (Exception e) {
            throw new RoleException("删除角色失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Role getRole(String roleId) throws RoleException {
        try {
            return roleDao.findById(roleId);
        } catch (Exception e) {
            throw new RoleException("获取角色失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Role> listRoles(String keyword, String tenantId) throws RoleException {
        try {
            Map<String, Object> condition = new java.util.HashMap<>();
            condition.put("keyword", keyword);
            condition.put("tenantId", tenantId);
            return roleDao.query(condition);
        } catch (Exception e) {
            throw new RoleException("查询角色列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void assignPermission(String roleId, String permissionId, String operator) throws RoleException {
        try {
            Role role = roleDao.findById(roleId);
            Permission perm = permissionDao.findById(permissionId);
            if (role == null || perm == null) {
                throw new RoleException("角色或权限不存在");
            }
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(permissionId);
            rolePermissionDao.insert(rp);
            evictPermissionCache();
        } catch (RoleException e) {
            throw e;
        } catch (Exception e) {
            throw new RoleException("分配权限失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void revokePermission(String roleId, String permissionId, String operator) throws RoleException {
        try {
            rolePermissionDao.delete(roleId, permissionId);
            evictPermissionCache();
        } catch (Exception e) {
            throw new RoleException("回收权限失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Permission> getRolePermissions(String roleId) throws RoleException {
        try {
            List<RolePermission> links = rolePermissionDao.listByRoleId(roleId);
            List<String> pids = links.stream().map(RolePermission::getPermissionId).collect(Collectors.toList());
            List<Permission> all = permissionDao.listAll();
            return all.stream().filter(p -> pids.contains(p.getPermissionId())).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RoleException("查询角色权限失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Set<Permission> getMergedPermissions(String roleId) throws RoleException {
        try {
            Set<Permission> result = new HashSet<>();
            collectPermissions(roleId, result, new HashSet<>());
            return result;
        } catch (Exception e) {
            throw new RoleException("合并权限失败: " + e.getMessage(), e);
        }
    }

    private void collectPermissions(String roleId, Set<Permission> acc, Set<String> visited) throws Exception {
        if (roleId == null || visited.contains(roleId)) {
            return;
        }
        visited.add(roleId);
        Role role = roleDao.findById(roleId);
        if (role == null) return;

        acc.addAll(getRolePermissions(roleId));
        if (role.getParentRoleId() != null) {
            collectPermissions(role.getParentRoleId(), acc, visited);
        }
    }

    private void evictPermissionCache() {
        if (permissionCacheService != null) {
            permissionCacheService.evictAll();
        }
    }
}


