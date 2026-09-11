package com.chinacreator.gzcm.sysman.iam.service;

import java.util.List;

import com.chinacreator.gzcm.sysman.iam.entity.Organization;
import com.chinacreator.gzcm.sysman.iam.entity.OrgPermission;

/**
 * 机构服务接口
 * 
 * @author CDRC Security Team
 */
public interface IOrganizationService {
    
    /**
     * 创建机构
     * 
     * @param orgName 机构名称
     * @param orgCode 机构编码
     * @param parentOrgId 父机构ID
     * @param orgType 机构类型
     * @param description 描述
     * @param createdBy 创建者ID
     * @return 机构实体
     * @throws OrganizationException
     */
    Organization createOrganization(String orgName, String orgCode, 
            String parentOrgId, String orgType, String description, String createdBy) 
            throws OrganizationException;
    
    /**
     * 获取机构
     * 
     * @param orgId 机构ID
     * @return 机构实体
     * @throws OrganizationException
     */
    Organization getOrganization(String orgId) throws OrganizationException;
    
    /**
     * 更新机构
     * 
     * @param orgId 机构ID
     * @param orgName 机构名称
     * @param orgCode 机构编码
     * @param orgType 机构类型
     * @param description 描述
     * @param updatedBy 更新者ID
     * @return 更新后的机构实体
     * @throws OrganizationException
     */
    Organization updateOrganization(String orgId, String orgName, String orgCode, 
            String orgType, String description, String updatedBy) throws OrganizationException;
    
    /**
     * 删除机构
     * 
     * @param orgId 机构ID
     * @throws OrganizationException
     */
    void deleteOrganization(String orgId) throws OrganizationException;
    
    /**
     * 查询机构树
     * 
     * @param rootOrgId 根机构ID（可选，为空则查询所有）
     * @return 机构树
     * @throws OrganizationException
     */
    Organization getOrganizationTree(String rootOrgId) throws OrganizationException;
    
    /**
     * 查询子机构列表
     * 
     * @param parentOrgId 父机构ID
     * @return 子机构列表
     * @throws OrganizationException
     */
    List<Organization> getChildren(String parentOrgId) throws OrganizationException;
    
    /**
     * 查询机构列表
     * 
     * @param orgType 机构类型（可选）
     * @param status 状态（可选）
     * @return 机构列表
     * @throws OrganizationException
     */
    List<Organization> listOrganizations(String orgType, String status) throws OrganizationException;
    
    /**
     * 分配用户到机构
     * 
     * @param userId 用户ID
     * @param orgId 机构ID
     * @param isPrimary 是否主机构
     * @param createdBy 创建者ID
     * @throws OrganizationException
     */
    void assignUserToOrg(String userId, String orgId, boolean isPrimary, String createdBy) 
            throws OrganizationException;
    
    /**
     * 移除用户机构关联
     * 
     * @param userId 用户ID
     * @param orgId 机构ID
     * @throws OrganizationException
     */
    void removeUserFromOrg(String userId, String orgId) throws OrganizationException;
    
    /**
     * 查询用户所属机构列表
     * 
     * @param userId 用户ID
     * @return 机构列表
     * @throws OrganizationException
     */
    List<Organization> getUserOrganizations(String userId) throws OrganizationException;
    
    /**
     * 设置用户主机构
     * 
     * @param userId 用户ID
     * @param orgId 机构ID
     * @throws OrganizationException
     */
    void setPrimaryOrg(String userId, String orgId) throws OrganizationException;
    
    /**
     * 创建机构权限
     * 
     * @param orgId 机构ID
     * @param resourceId 资源ID
     * @param action 操作
     * @param inheritFromParent 是否继承父机构权限
     * @param createdBy 创建者ID
     * @return 权限实体
     * @throws OrganizationException
     */
    OrgPermission createOrgPermission(String orgId, String resourceId, 
            String action, boolean inheritFromParent, String createdBy) throws OrganizationException;
    
    /**
     * 查询机构权限（包括继承的权限）
     * 
     * @param orgId 机构ID
     * @return 权限列表
     * @throws OrganizationException
     */
    List<OrgPermission> getOrgPermissions(String orgId) throws OrganizationException;
    
    /**
     * 删除机构权限
     * 
     * @param permissionId 权限ID
     * @throws OrganizationException
     */
    void deleteOrgPermission(String permissionId) throws OrganizationException;
    
    /**
     * 机构异常
     */
    class OrganizationException extends Exception {
        private static final long serialVersionUID = 1L;
        private String errorCode;
        
        public OrganizationException(String message) {
            super(message);
        }
        
        public OrganizationException(String message, Throwable cause) {
            super(message, cause);
        }
        
        public OrganizationException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public OrganizationException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public String getErrorCode() {
            return errorCode;
        }
    }
}

