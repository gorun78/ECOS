package com.chinacreator.gzcm.runtime.core.metadata.service.impl;

import java.util.ArrayList;
import java.util.List;
import com.chinacreator.gzcm.runtime.core.metadata.bean.Metadata;
import com.chinacreator.gzcm.runtime.core.metadata.service.IMetadataService;

/**
 * 元数据服务实现（占位实现）
 */
public class MetadataService implements IMetadataService {
    
    @Override
    public Metadata getMetadataById(String id) throws Exception {
        // TODO: 实现实际的查询逻辑
        return null;
    }
    
    @Override
    public List<Metadata> getAllMetadata() throws Exception {
        // TODO: 实现实际的查询逻辑
        return new ArrayList<>();
    }
}
