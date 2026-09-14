package com.chinacreator.gzcm.sysman.iam.dao;

import java.util.List;

import com.chinacreator.gzcm.sysman.iam.entity.Organization;
import com.chinacreator.gzcm.sysman.iam.entity.OrgPermission;
import com.chinacreator.gzcm.sysman.iam.entity.UserOrganization;

/**
 * 机构DAO接口
 * 
 * @author CDRC Security Team
 */
public interface OrganizationDao {
    
    /**
     * 创建机构
     * 
     * @param organization 机构实体
     * @throws Exception
     */
    void createOrganization(Organization organization) throws Exception;
    
    /**
     * 根据ID查询机构
     * 
     * @param orgId 机构ID
     * @return 机构实体
     * @throws Exception
     */
    Organization getOrganization(String orgId) throws Exception;
    
    /**
     * 更新机构
     * 
     * @param organization 机构实体
     * @throws Exception
     */
    void updateOrganization(Organization organization) throws Exception;
    
    /**
     * 删除机构（软删除）
     * 
     * @param orgId 机构ID
     * @throws Exception
     */
    void deleteOrganization(String orgId) throws Exception;
    
    /**
     * 查询子机构列表
     * 
     * @param parentOrgId 父机构ID
     * @return 子机构列表
     * @throws Exception
     */
    List<Organization> getChildren(String parentOrgId) throws Exception;
    
    /**
     * 查询所有机构
     * 
     * @param orgType 机构类型（可选）
     * @param status 状态（可选）
     * @return 机构列表
     * @throws Exception
     */
    List<Organization> listOrganizations(String orgType, String status) throws Exception;
    
    /**
     * 检查是否有子机构
     * 
     * @param orgId 机构ID
     * @return 是否有子机构
     * @throws Exception
     */
    boolean hasChildren(String orgId) throws Exception;
    
    /**
     * 分配用户到机构
     * 
     * @param userOrg 用户机构关联
     * @throws Exception
     */
    void assignUserToOrg(UserOrganization userOrg) throws Exception;
    
    /**
     * 移除用户机构关联
     * 
     * @param userId 用户ID
     * @param orgId 机构ID
     * @throws Exception
     */
    void removeUserFromOrg(String userId, String orgId) throws Exception;
    
    /**
     * 查询用户所属机构列表
     * 
     * @param userId 用户ID
     * @return 机构列表
     * @throws Exception
     */
    List<Organization> getUserOrganizations(String userId) throws Exception;
    
    /**
     * 设置用户主机构
     * 
     * @param userId 用户ID
     * @param orgId 机构ID
     * @throws Exception
     */
    void setPrimaryOrg(String userId, String orgId) throws Exception;
    
    /**
     * 创建机构权限
     * 
     * @param permission 权限实体
     * @throws Exception
     */
    void createOrgPermission(OrgPermission permission) throws Exception;
    
    /**
     * 查询机构权限列表
     * 
     * @param orgId 机构ID
     * @return 权限列表
     * @throws Exception
     */
    List<OrgPermission> getOrgPermissions(String orgId) throws Exception;
    
    /**
     * 删除机构权限
     * 
     * @param permissionId 权限ID
     * @throws Exception
     */
    void deleteOrgPermission(String permissionId) throws Exception;
}

