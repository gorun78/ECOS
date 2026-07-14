package com.chinacreator.gzcm.runtime.core.legacy.applymanager.api.factorys;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.legacy.applymanager.vo.Apply;
import com.chinacreator.gzcm.runtime.core.legacy.applymanager.api.IApplyManager;

/**
 * 应用管理器工厂（占位实现）
 * 用于兼容旧代码
 */
public class ApplyManagerFactory {
    
    private static IApplyManager instance;
    
    /**
     * 获取应用管理器实例
     * @return 应用管理器实例
     */
    public static IApplyManager getApplyManagerInstance() {
        if (instance == null) {
            synchronized (ApplyManagerFactory.class) {
                if (instance == null) {
                    instance = new IApplyManager() {
                        @Override
                        public List<Apply> getApplyList() {
                            // TODO: 实现实际的获取逻辑
                            return new java.util.ArrayList<>();
                        }
                    };
                }
            }
        }
        return instance;
    }
}
