package com.chinacreator.gzcm.runtime.core.legacy.applymanager.api;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.legacy.applymanager.vo.Apply;

/**
 * 应用管理器接口
 */
public interface IApplyManager {
    
    /**
     * 获取应用列表
     * @return 应用列表
     */
    List<Apply> getApplyList();
}
