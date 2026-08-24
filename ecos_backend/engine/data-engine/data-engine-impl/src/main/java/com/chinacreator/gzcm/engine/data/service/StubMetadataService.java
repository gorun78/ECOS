package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.MetadataService;
import com.chinacreator.gzcm.common.data.model.DataField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MetadataService 启动兜底实现（PMO-E3）。
 * data-engine-api 的 MetadataService 接口无实现类，此 stub 让 Gateway 能启动。
 */
@Service
public class StubMetadataService implements MetadataService {

    private static final Logger log = LoggerFactory.getLogger(StubMetadataService.class);

    @Override
    public int collectAll(String datasourceId) {
        log.warn("StubMetadataService.collectAll: stub no-op"); return 0;
    }
    @Override
    public List<DataField> getFields(String resourceId) {
        log.warn("StubMetadataService.getFields: stub no-op"); return List.of();
    }
}
