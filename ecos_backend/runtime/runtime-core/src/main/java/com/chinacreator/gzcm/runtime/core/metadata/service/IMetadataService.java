package com.chinacreator.gzcm.runtime.core.metadata.service;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.metadata.bean.Metadata;

/**
 * 元数据服务接口（占位实现）
 */
public interface IMetadataService {
    
    /**
     * 根据ID获取元数据
     * @param id 元数据ID
     * @return 元数据对象
     * @throws Exception 获取失败时抛出异常
     */
    Metadata getMetadataById(String id) throws Exception;
    
    /**
     * 获取所有元数据列表
     * @return 元数据列表
     * @throws Exception 获取失败时抛出异常
     */
    List<Metadata> getAllMetadata() throws Exception;
}
