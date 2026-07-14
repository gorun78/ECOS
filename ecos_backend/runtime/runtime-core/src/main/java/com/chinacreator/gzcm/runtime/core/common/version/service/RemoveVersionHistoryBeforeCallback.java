package com.chinacreator.gzcm.runtime.core.common.version.service;

/**
 * 移除版本历史前的回调接口
 */
public interface RemoveVersionHistoryBeforeCallback {
    /**
     * 在移除版本历史前调用
     * @param versionId 版本ID
     */
    void beforeRemove(String versionId);
}
