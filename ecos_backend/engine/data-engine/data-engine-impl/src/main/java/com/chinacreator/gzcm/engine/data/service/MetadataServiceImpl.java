package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.model.DataField;
import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.engine.data.MetadataService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.repository.DataSourceRepository;
import com.chinacreator.gzcm.runtime.access.connector.Connector;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MetadataService JdbcTemplate 实现。
 * collectAll 走 runtime-access Connector 真实发现表清单，经 ResourceSyncService 落库
 * （与 MetadataCollectTaskExecutor 异步链路同源，避免双轨实现）。
 * 表: td_data_resource, td_data_field
 */
@Service
public class MetadataServiceImpl implements MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataServiceImpl.class);

    private final JdbcTemplate jdbc;
    private final DataSourceRepository dataSourceRepository;
    private final ConnectorFactory connectorFactory;
    private final ResourceSyncService resourceSyncService;

    public MetadataServiceImpl(JdbcTemplate jdbc,
                               DataSourceRepository dataSourceRepository,
                               ConnectorFactory connectorFactory,
                               ResourceSyncService resourceSyncService) {
        this.jdbc = jdbc;
        this.dataSourceRepository = dataSourceRepository;
        this.connectorFactory = connectorFactory;
        this.resourceSyncService = resourceSyncService;
    }

    @Override
    public int collectAll(String datasourceId) {
        DataSourceEntity ds = dataSourceRepository.findById(datasourceId);
        if (ds == null) {
            throw new IllegalArgumentException("数据源不存在: " + datasourceId);
        }

        // Connector 发现表/视图清单（连接失败时异常上抛，由调用方决定任务失败语义）
        Connector connector = connectorFactory.getConnector(ds.getDatasourceType());
        List<DataResource> resources = connector.listResources(
                ds.getConnectionConfig(), ds.getOrgId(), ds.getDatasourceName());

        // 逐表落库（幂等 upsert）；行数不在此统计（rowCnt=null 保持 -1，由异步任务按 countMethod 统计）
        int ok = 0;
        for (DataResource r : resources) {
            try {
                resourceSyncService.syncResource(datasourceId, r, null);
                ok++;
            } catch (Exception e) {
                log.warn("资源 {} 落库失败: {}", r.getResourceName(), e.getMessage());
            }
        }
        log.info("Collected metadata for datasource {}: {}/{} resources",
                datasourceId, ok, resources.size());
        return ok;
    }

    @Override
    public List<DataField> getFields(String resourceId) {
        return jdbc.query(
            "SELECT * FROM td_data_field WHERE resource_id = ? ORDER BY field_order",
            (rs, i) -> {
                DataField f = new DataField();
                f.setFieldId(rs.getString("field_id"));
                f.setResourceId(rs.getString("resource_id"));
                f.setFieldName(rs.getString("field_name"));
                f.setFieldAlias(rs.getString("field_alias"));
                f.setDataType(rs.getString("field_type"));
                f.setDataLength(rs.getInt("field_length"));
                f.setDataPrecision(rs.getInt("data_precision"));
                Boolean nullable = rs.getBoolean("nullable");
                f.setNullable(nullable);
                Boolean pk = rs.getBoolean("is_primary_key");
                f.setPrimaryKey(pk);
                f.setDefaultValue(rs.getString("default_value"));
                f.setDescription(rs.getString("description"));
                f.setSortOrder(rs.getInt("field_order"));
                return f;
            },
            resourceId
        );
    }
}
