package com.chinacreator.gzcm.datanet.service.impl;

import com.chinacreator.gzcm.datanet.connector.Connector;
import com.chinacreator.gzcm.datanet.connector.ConnectorFactory;
import com.chinacreator.gzcm.datanet.dto.DataSourceDTO;
import com.chinacreator.gzcm.datanet.repository.DataSourceRepository;
import com.chinacreator.gzcm.datanet.service.DataSourceService;
import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 数据源管理服务实现 — 基于 MyBatis + MySQL 持久化。
 */
@Service
public class DataSourceServiceImpl implements DataSourceService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceServiceImpl.class);

    private final DataSourceRepository repository;
    private final ConnectorFactory connectorFactory;

    public DataSourceServiceImpl(DataSourceRepository repository,
                                  ConnectorFactory connectorFactory) {
        this.repository = repository;
        this.connectorFactory = connectorFactory;
    }

    @Override
    @Transactional
    public DataSourceEntity register(DataSourceDTO dto) {
        DataSourceEntity entity = new DataSourceEntity();
        entity.setDatasourceId(UUID.randomUUID().toString().replace("-", ""));
        entity.setDatasourceName(dto.getDatasourceName());
        entity.setDatasourceType(dto.getDatasourceType());
        entity.setOrgId(dto.getOrgId());
        entity.setNodeId(dto.getOrgId());
        entity.setDescription(dto.getDescription());
        entity.setConnectionConfig(dto.getConnectionConfig());
        entity.setStatus("ACTIVE");
        entity.setCreateBy("system");
        entity.setCreateTime(Timestamp.valueOf(LocalDateTime.now()));
        entity.setUpdateBy("system");
        entity.setUpdateTime(Timestamp.valueOf(LocalDateTime.now()));
        entity.setTags(dto.getTags());

        repository.insert(entity);
        log.info("Registered datasource: {} (type={})", entity.getDatasourceName(), entity.getDatasourceType());
        return entity;
    }

    @Override
    public boolean testConnection(String datasourceId) {
        DataSourceEntity entity = repository.findById(datasourceId);
        if (entity == null) return false;

        try {
            Connector connector = connectorFactory.getConnector(entity.getDatasourceType());
            boolean ok = connector.testConnection(entity.getConnectionConfig());

            entity.setLastTestTime(Timestamp.valueOf(LocalDateTime.now()));
            entity.setLastTestResult(ok ? "SUCCESS" : "FAILED");
            entity.setLastTestMessage(ok ? "连接成功" : "连接失败");
            repository.update(entity);
            return ok;
        } catch (Exception e) {
            entity.setLastTestTime(Timestamp.valueOf(LocalDateTime.now()));
            entity.setLastTestResult("ERROR");
            entity.setLastTestMessage(e.getMessage());
            repository.update(entity);
            return false;
        }
    }

    @Override
    public List<DataSourceEntity> listAll() {
        return repository.findAll();
    }

    @Override
    public DataSourceEntity getById(String datasourceId) {
        return repository.findById(datasourceId);
    }

    @Override
    @Transactional
    public void remove(String datasourceId) {
        repository.deleteById(datasourceId);
        log.info("Removed datasource: {}", datasourceId);
    }
}
