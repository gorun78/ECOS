package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.sysman.iam.cache.PermissionCacheService;
import com.chinacreator.gzcm.sysman.iam.dao.PermissionDao;
import com.chinacreator.gzcm.sysman.iam.dao.RolePermissionDao;
import com.chinacreator.gzcm.sysman.iam.dao.UserRoleDao;
import com.chinacreator.gzcm.sysman.iam.entity.Permission;
import com.chinacreator.gzcm.sysman.iam.entity.RolePermission;
import com.chinacreator.gzcm.sysman.iam.entity.UserRole;
import com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService;
import com.chinacreator.gzcm.sysman.iam.service.IRoleService;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;
import com.chinacreator.gzcm.sysman.iam.service.IUserService.UserException;

/**
 * 简化版权限检查：基于用户角色 -> 角色权限 -> 合并继承，匹配资源+动作。
 * 可后续接入缓存与条件表达式解析。
 */
@Service
public class PermissionCheckServiceImpl implements IPermissionCheckService {

    private final IUserService userService;
    private final IRoleService roleService;
    private final PermissionDao permissionDao;
    private final RolePermissionDao rolePermissionDao;
    private final UserRoleDao userRoleDao;
    private final PermissionCacheService permissionCacheService;

    @Autowired
    public PermissionCheckServiceImpl(IUserService userService,
                                      IRoleService roleService,
                                      PermissionDao permissionDao,
                                      RolePermissionDao rolePermissionDao,
                                      UserRoleDao userRoleDao,
                                      PermissionCacheService permissionCacheService) {
        this.userService = userService;
        this.roleService = roleService;
        this.permissionDao = permissionDao;
        this.rolePermissionDao = rolePermissionDao;
        this.userRoleDao = userRoleDao;
        this.permissionCacheService = permissionCacheService;
    }

    @Override
    public boolean checkPermission(String userId, String resource, String action) throws PermissionCheckException {
        try {
            // 获取用户的所有角色权限（包括继承的权限）
            Map<String, PermissionDecision> permissionMap = getUserPermissionDecisions(userId);
            
            // 查找匹配的权限
            List<Permission> all = permissionDao.listAll();
            List<PermissionDecision> matchingDecisions = all.stream()
                    .filter(p -> resourceEquals(p.getResource(), resource) && actionEquals(p.getAction(), action))
                    .map(p -> permissionMap.get(p.getPermissionId()))
                    .filter(d -> d != null)
                    .collect(Collectors.toList());
            
            if (matchingDecisions.isEmpty()) {
                return false; // 没有匹配的权限
            }
            
            // 按优先级排序，高优先级优先
            matchingDecisions.sort((d1, d2) -> {
                int p1 = d1.priority != null ? d1.priority : 0;
                int p2 = d2.priority != null ? d2.priority : 0;
                return Integer.compare(p2, p1); // 降序
            });
            
            // 检查是否有DENY（显式拒绝）
            for (PermissionDecision decision : matchingDecisions) {
                if ("DENY".equalsIgnoreCase(decision.effect)) {
                    return false; // 显式拒绝
                }
            }
            
            // 检查是否有ALLOW（允许）
            for (PermissionDecision decision : matchingDecisions) {
                if ("ALLOW".equalsIgnoreCase(decision.effect) || decision.effect == null) {
                    return true; // 允许（默认也是允许）
                }
            }
            
            return false;
        } catch (Exception e) {
            throw new PermissionCheckException("权限检查失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取用户的所有权限决策（包括继承的权限，支持覆盖）
     */
    private Map<String, PermissionDecision> getUserPermissionDecisions(String userId) throws Exception {
        // 先从缓存获取
        Map<String, Object> cached = permissionCacheService.getUserPermissionDecisions(userId);
        if (cached != null && !cached.isEmpty()) {
            // 转换为PermissionDecision对象
            Map<String, PermissionDecision> result = new java.util.HashMap<>();
            for (Map.Entry<String, Object> entry : cached.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> decisionMap = (Map<String, Object>) entry.getValue();
                    String effect = (String) decisionMap.get("effect");
                    Integer priority = decisionMap.get("priority") != null ? 
                        ((Number) decisionMap.get("priority")).intValue() : null;
                    result.put(entry.getKey(), new PermissionDecision(effect, priority));
                }
            }
            return result;
        }
        
        // 获取用户的角色列表
        List<String> roleIds = fetchUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return new java.util.HashMap<>();
        }
        
        // 合并所有角色的权限（包括继承的权限）
        Map<String, PermissionDecision> permissionMap = new java.util.HashMap<>();
        for (String roleId : roleIds) {
            mergeRolePermissions(roleId, permissionMap, new java.util.HashSet<>());
        }
        
        // 转换为Map格式并写入缓存
        Map<String, Object> cacheMap = new java.util.HashMap<>();
        for (Map.Entry<String, PermissionDecision> entry : permissionMap.entrySet()) {
            Map<String, Object> decisionMap = new java.util.HashMap<>();
            decisionMap.put("effect", entry.getValue().effect);
            decisionMap.put("priority", entry.getValue().priority);
            cacheMap.put(entry.getKey(), decisionMap);
        }
        permissionCacheService.putUserPermissionDecisions(userId, cacheMap, -1);
        
        return permissionMap;
    }
    
    /**
     * 递归合并角色权限（包括父角色的权限）
     * 子角色的权限会覆盖父角色的相同权限（如果优先级更高）
     */
    private void mergeRolePermissions(String roleId, Map<String, PermissionDecision> permissionMap, 
            Set<String> visited) throws Exception {
        if (roleId == null || visited.contains(roleId)) {
            return; // 防止循环
        }
        visited.add(roleId);
        
        // 先合并父角色的权限
        try {
            com.chinacreator.gzcm.sysman.iam.entity.Role role = roleService.getRole(roleId);
            if (role != null && role.getParentRoleId() != null) {
                mergeRolePermissions(role.getParentRoleId(), permissionMap, visited);
            }
        } catch (Exception e) {
            // 忽略错误，继续处理
        }
        
        // 再合并当前角色的权限（会覆盖父角色的相同权限）
        List<RolePermission> rolePermissions = rolePermissionDao.listByRoleId(roleId);
        for (RolePermission rp : rolePermissions) {
            String permId = rp.getPermissionId();
            PermissionDecision existing = permissionMap.get(permId);
            
            // 如果当前权限的优先级更高，或者不存在，则覆盖
            if (existing == null || 
                (rp.getPriority() != null && existing.priority != null && rp.getPriority() > existing.priority)) {
                permissionMap.put(permId, new PermissionDecision(
                    rp.getEffect() != null ? rp.getEffect() : "ALLOW",
                    rp.getPriority() != null ? rp.getPriority() : 0
                ));
            }
        }
    }
    
    /**
     * 权限决策内部类
     */
    private static class PermissionDecision {
        String effect; // ALLOW/DENY
        Integer priority; // 优先级
        
        PermissionDecision(String effect, Integer priority) {
            this.effect = effect;
            this.priority = priority;
        }
    }

    private List<String> fetchUserRoleIds(String userId) throws Exception {
        List<UserRole> links = userRoleDao.listByUserId(userId);
        return links.stream().map(UserRole::getRoleId).collect(Collectors.toList());
    }

    private boolean resourceEquals(String permResource, String target) {
        if (permResource == null) return false;
        // 简单匹配；可拓展为前缀/AntPath/正则匹配
        return permResource.equals(target);
    }

    private boolean actionEquals(String permAction, String target) {
        if (permAction == null) return false;
        return permAction.equalsIgnoreCase(target);
    }
}


