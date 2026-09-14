package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.sysman.iam.dao.OrganizationDao;
import com.chinacreator.gzcm.sysman.iam.entity.OrgPermission;
import com.chinacreator.gzcm.sysman.iam.entity.Organization;
import com.chinacreator.gzcm.sysman.iam.entity.UserOrganization;
import com.chinacreator.gzcm.sysman.iam.service.IOrganizationService;

/**
 * 机构服务实现类
 * 
 * @author CDRC Security Team
 */
@Service
public class OrganizationServiceImpl implements IOrganizationService {
    
    private OrganizationDao organizationDao;
    
    /**
     * 构造函数
     * 
     * @param organizationDao 机构DAO
     */
    @Autowired
    public OrganizationServiceImpl(OrganizationDao organizationDao) {
        this.organizationDao = organizationDao;
    }
    
    @Override
    public Organization createOrganization(String orgName, String orgCode, 
            String parentOrgId, String orgType, String description, String createdBy) 
            throws OrganizationException {
        try {
            // 验证参数
            if (orgName == null || orgName.isEmpty()) {
                throw new OrganizationException("INVALID_PARAMETER", "机构名称不能为空");
            }
            
            // 如果指定了父机构，验证父机构存在
            Organization parent = null;
            if (parentOrgId != null && !parentOrgId.isEmpty()) {
                parent = organizationDao.getOrganization(parentOrgId);
                if (parent == null) {
                    throw new OrganizationException("PARENT_NOT_FOUND", "父机构不存在: " + parentOrgId);
                }
            }
            
            // 创建机构实体
            Organization organization = new Organization();
            organization.setOrgId(UUID.randomUUID().toString());
            organization.setOrgName(orgName);
            organization.setOrgCode(orgCode);
            organization.setParentOrgId(parentOrgId);
            organization.setOrgType(orgType);
            organization.setDescription(description);
            organization.setStatus("ACTIVE");
            organization.setCreatedTime(LocalDateTime.now());
            organization.setCreatedBy(createdBy);
            organization.setUpdatedTime(LocalDateTime.now());
            organization.setUpdatedBy(createdBy);
            
            // 计算并设置path（用于快速查询）
            String path = calculatePath(organization.getOrgId(), parent);
            organization.setPath(path);
            
            // 保存
            organizationDao.createOrganization(organization);
            
            return organization;
            
        } catch (OrganizationException e) {
            throw e;
        } catch (Exception e) {
            throw new OrganizationException("CREATE_FAILED", "创建机构失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 计算机构路径
     */
    private String calculatePath(String orgId, Organization parent) {
        if (parent == null || parent.getPath() == null || parent.getPath().isEmpty()) {
            return "/" + orgId;
        }
        return parent.getPath() + "/" + orgId;
    }
    
    @Override
    public Organization getOrganization(String orgId) throws OrganizationException {
        try {
            Organization organization = organizationDao.getOrganization(orgId);
            if (organization == null) {
                throw new OrganizationException("NOT_FOUND", "机构不存在: " + orgId);
            }
            return organization;
        } catch (OrganizationException e) {
            throw e;
        } catch (Exception e) {
            throw new OrganizationException("QUERY_FAILED", "查询机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Organization updateOrganization(String orgId, String orgName, String orgCode, 
            String orgType, String description, String updatedBy) throws OrganizationException {
        try {
            Organization organization = organizationDao.getOrganization(orgId);
            if (organization == null) {
                throw new OrganizationException("NOT_FOUND", "机构不存在: " + orgId);
            }
            
            String oldParentId = organization.getParentOrgId();
            
            // 更新字段
            if (orgName != null) {
                organization.setOrgName(orgName);
            }
            if (orgCode != null) {
                organization.setOrgCode(orgCode);
            }
            if (orgType != null) {
                organization.setOrgType(orgType);
            }
            if (description != null) {
                organization.setDescription(description);
            }
            organization.setUpdatedTime(LocalDateTime.now());
            organization.setUpdatedBy(updatedBy);
            
            // 如果父机构发生变化，需要更新path并递归更新子机构的path
            // 注意：这里假设updateOrganization方法可以更新parentOrgId，如果需要单独处理，可以添加参数
            organizationDao.updateOrganization(organization);
            
            // 如果父机构变化，更新当前机构及其所有子机构的path
            if (oldParentId != null && !oldParentId.equals(organization.getParentOrgId())) {
                updatePathRecursively(organization);
            }
            
            return organization;
            
        } catch (OrganizationException e) {
            throw e;
        } catch (Exception e) {
            throw new OrganizationException("UPDATE_FAILED", "更新机构失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 递归更新机构及其子机构的path
     */
    private void updatePathRecursively(Organization organization) throws Exception {
        Organization parent = null;
        if (organization.getParentOrgId() != null) {
            parent = organizationDao.getOrganization(organization.getParentOrgId());
        }
        String newPath = calculatePath(organization.getOrgId(), parent);
        organization.setPath(newPath);
        organizationDao.updateOrganization(organization);
        
        // 递归更新子机构
        List<Organization> children = organizationDao.getChildren(organization.getOrgId());
        if (children != null) {
            for (Organization child : children) {
                updatePathRecursively(child);
            }
        }
    }
    
    @Override
    public void deleteOrganization(String orgId) throws OrganizationException {
        try {
            Organization organization = organizationDao.getOrganization(orgId);
            if (organization == null) {
                throw new OrganizationException("NOT_FOUND", "机构不存在: " + orgId);
            }
            
            // 检查是否有子机构
            if (organizationDao.hasChildren(orgId)) {
                throw new OrganizationException("HAS_CHILDREN", "机构存在子机构，无法删除");
            }
            
            // 软删除
            organizationDao.deleteOrganization(orgId);
            
        } catch (OrganizationException e) {
            throw e;
        } catch (Exception e) {
            throw new OrganizationException("DELETE_FAILED", "删除机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Organization getOrganizationTree(String rootOrgId) throws OrganizationException {
        try {
            Organization root;
            if (rootOrgId == null || rootOrgId.isEmpty()) {
                // 如果没有指定根机构，查找所有根机构（parentOrgId为null的机构）
                List<Organization> roots = organizationDao.listOrganizations(null, "ACTIVE");
                if (roots.isEmpty()) {
                    return null;
                }
                root = roots.get(0); // 取第一个作为根
            } else {
                root = organizationDao.getOrganization(rootOrgId);
                if (root == null) {
                    throw new OrganizationException("NOT_FOUND", "根机构不存在: " + rootOrgId);
                }
            }
            
            // 递归构建树
            buildTree(root);
            
            return root;
            
        } catch (OrganizationException e) {
            throw e;
        } catch (Exception e) {
            throw new OrganizationException("QUERY_FAILED", "查询机构树失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 递归构建机构树
     */
    private void buildTree(Organization organization) throws Exception {
        List<Organization> children = organizationDao.getChildren(organization.getOrgId());
        if (children != null && !children.isEmpty()) {
            organization.setChildren(children);
            for (Organization child : children) {
                buildTree(child);
            }
        }
    }
    
    @Override
    public List<Organization> getChildren(String parentOrgId) throws OrganizationException {
        try {
            return organizationDao.getChildren(parentOrgId);
        } catch (Exception e) {
            throw new OrganizationException("QUERY_FAILED", "查询子机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Organization> listOrganizations(String orgType, String status) 
            throws OrganizationException {
        try {
            return organizationDao.listOrganizations(orgType, status);
        } catch (Exception e) {
            throw new OrganizationException("QUERY_FAILED", "查询机构列表失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void assignUserToOrg(String userId, String orgId, boolean isPrimary, String createdBy) 
            throws OrganizationException {
        try {
            // 验证机构存在
            Organization organization = organizationDao.getOrganization(orgId);
            if (organization == null) {
                throw new OrganizationException("NOT_FOUND", "机构不存在: " + orgId);
            }
            
            // 如果是主机构，先取消其他主机构
            if (isPrimary) {
                organizationDao.setPrimaryOrg(userId, orgId);
            }
            
            // 创建关联
            UserOrganization userOrg = new UserOrganization();
            userOrg.setUserId(userId);
            userOrg.setOrgId(orgId);
            userOrg.setIsPrimary(isPrimary ? "1" : "0");
            userOrg.setCreatedTime(new Date());
            userOrg.setCreatedBy(createdBy);
            
            organizationDao.assignUserToOrg(userOrg);
            
        } catch (OrganizationException e) {
            throw e;
        } catch (Exception e) {
            throw new OrganizationException("ASSIGN_FAILED", "分配用户到机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void removeUserFromOrg(String userId, String orgId) throws OrganizationException {
        try {
            organizationDao.removeUserFromOrg(userId, orgId);
        } catch (Exception e) {
            throw new OrganizationException("REMOVE_FAILED", "移除用户机构关联失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Organization> getUserOrganizations(String userId) throws OrganizationException {
        try {
            return organizationDao.getUserOrganizations(userId);
        } catch (Exception e) {
            throw new OrganizationException("QUERY_FAILED", "查询用户机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void setPrimaryOrg(String userId, String orgId) throws OrganizationException {
        try {
            organizationDao.setPrimaryOrg(userId, orgId);
        } catch (Exception e) {
            throw new OrganizationException("SET_PRIMARY_FAILED", "设置主机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public OrgPermission createOrgPermission(String orgId, String resourceId, 
            String action, boolean inheritFromParent, String createdBy) throws OrganizationException {
        try {
            // 验证机构存在
            Organization organization = organizationDao.getOrganization(orgId);
            if (organization == null) {
                throw new OrganizationException("NOT_FOUND", "机构不存在: " + orgId);
            }
            
            // 创建权限
            OrgPermission permission = new OrgPermission();
            permission.setPermissionId(UUID.randomUUID().toString());
            permission.setOrgId(orgId);
            permission.setResourceId(resourceId);
            permission.setAction(action);
            permission.setInheritFromParent(inheritFromParent ? "1" : "0");
            permission.setCreatedTime(new Date());
            permission.setCreatedBy(createdBy);
            
            organizationDao.createOrgPermission(permission);
            
            return permission;
            
        } catch (OrganizationException e) {
            throw e;
        } catch (Exception e) {
            throw new OrganizationException("CREATE_PERMISSION_FAILED", 
                "创建机构权限失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<OrgPermission> getOrgPermissions(String orgId) throws OrganizationException {
        try {
            List<OrgPermission> permissions = new ArrayList<>();
            Map<String, OrgPermission> permissionMap = new HashMap<>(); // 用于去重，子机构权限覆盖父机构权限
            
            // 递归收集权限（从根到当前机构）
            collectOrgPermissions(orgId, permissionMap, new ArrayList<>());
            
            permissions.addAll(permissionMap.values());
            return permissions;
            
        } catch (Exception e) {
            throw new OrganizationException("QUERY_FAILED", 
                "查询机构权限失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 递归收集机构权限（包括继承的权限）
     * 子机构的权限会覆盖父机构的相同权限
     */
    private void collectOrgPermissions(String orgId, Map<String, OrgPermission> permissionMap, 
            List<String> visited) throws Exception {
        if (orgId == null || visited.contains(orgId)) {
            return; // 防止循环
        }
        visited.add(orgId);
        
        Organization organization = organizationDao.getOrganization(orgId);
        if (organization == null) {
            return;
        }
        
        // 先收集父机构的权限
        if (organization.getParentOrgId() != null) {
            collectOrgPermissions(organization.getParentOrgId(), permissionMap, visited);
        }
        
        // 再收集当前机构的权限（会覆盖父机构的相同权限）
        List<OrgPermission> orgPermissions = organizationDao.getOrgPermissions(orgId);
        for (OrgPermission perm : orgPermissions) {
            // 使用resourceId+action作为key，确保相同资源操作的权限会被覆盖
            String key = perm.getResourceId() + ":" + perm.getAction();
            permissionMap.put(key, perm);
        }
    }
    
    @Override
    public void deleteOrgPermission(String permissionId) throws OrganizationException {
        try {
            organizationDao.deleteOrgPermission(permissionId);
        } catch (Exception e) {
            throw new OrganizationException("DELETE_FAILED", 
                "删除机构权限失败: " + e.getMessage(), e);
        }
    }
}

