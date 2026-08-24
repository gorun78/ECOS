package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * DataSourceService 启动兜底实现（PMO-E3）。
 * <p>
 * data-engine-api 的 DataSourceService 接口无实现类，导致 DataSourceRegistryService
 * 无法注入。此 stub 提供最小化实现，让 Gateway 能启动。
 * 真正的数据源管理功能是功能缺口，本次不实现。
 * </p>
 */
@Service
public class StubDataSourceService implements DataSourceService {

    private static final Logger log = LoggerFactory.getLogger(StubDataSourceService.class);

    @Override
    public DataSourceEntity register(DataSourceDTO dto) {
        log.warn("StubDataSourceService.register: stub no-op");
        return null;
    }

    @Override
    public boolean testConnection(String datasourceId) {
        log.warn("StubDataSourceService.testConnection: stub no-op");
        return false;
    }

    @Override
    public List<DataSourceEntity> listAll() {
        log.warn("StubDataSourceService.listAll: stub no-op");
        return List.of();
    }

    @Override
    public DataSourceEntity getById(String datasourceId) {
        log.warn("StubDataSourceService.getById: stub no-op");
        return null;
    }

    @Override
    public void remove(String datasourceId) {
        log.warn("StubDataSourceService.remove: stub no-op");
    }
}
