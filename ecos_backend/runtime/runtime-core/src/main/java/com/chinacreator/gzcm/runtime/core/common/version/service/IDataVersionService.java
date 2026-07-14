package com.chinacreator.gzcm.runtime.core.common.version.service;

import com.chinacreator.gzcm.runtime.core.common.version.bean.Version;

/**
 * 数据版本服务接口
 */
public interface IDataVersionService {
    
    /**
     * 检查是否启用版本
     * @param resourceCode 资源代码
     * @return 是否启用
     */
    boolean checkEnabledVersion(String resourceCode);
    
    /**
     * 根据ID获取版本
     * @param versionId 版本ID
     * @return 版本对象
     */
    Version getVersionById(String versionId);
    
    /**
     * 添加版本
     * @param groupId 组ID
     * @param groupName 组名称
     * @param creator 创建者
     * @return 版本对象
     */
    Version addVersion(String groupId, String groupName, String creator);
    
    /**
     * 移除版本历史
     * @param versionId 版本ID
     * @param callback 回调
     */
    void removeVersionHistory(String versionId, RemoveVersionHistoryBeforeCallback callback);
    
    /**
     * 移除版本历史前的回调接口
     */
    interface RemoveVersionHistoryBeforeCallback {
        /**
         * 在移除版本历史前执行
         * @param versionId 版本ID
         * @throws Exception
         */
        void beforeRemove(String versionId) throws Exception;
        
        /**
         * 执行回调（兼容方法）
         * @param version 版本对象
         * @throws Exception
         */
        default void execute(Version version) throws Exception {
            beforeRemove(version.getId());
        }
    }
}
