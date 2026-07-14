package com.chinacreator.gzcm.datanet.service.impl;

import com.chinacreator.gzcm.datanet.connector.Connector;
import com.chinacreator.gzcm.datanet.connector.ConnectorFactory;
import com.chinacreator.gzcm.datanet.model.DataField;
import com.chinacreator.gzcm.datanet.model.DataResource;
import com.chinacreator.gzcm.datanet.repository.DataFieldRepository;
import com.chinacreator.gzcm.datanet.repository.DataSourceRepository;
import com.chinacreator.gzcm.datanet.service.CatalogService;
import com.chinacreator.gzcm.datanet.service.MetadataService;
import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 元数据采集服务实现 — 基于 MyBatis + MySQL 持久化。
 */
@Service
public class MetadataServiceImpl implements MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataServiceImpl.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ConnectorFactory connectorFactory;
    private final CatalogService catalogService;
    private final DataSourceRepository dsRepository;
    private final DataFieldRepository fieldRepository;

    public MetadataServiceImpl(ConnectorFactory connectorFactory,
                                CatalogService catalogService,
                                DataSourceRepository dsRepository,
                                DataFieldRepository fieldRepository) {
        this.connectorFactory = connectorFactory;
        this.catalogService = catalogService;
        this.dsRepository = dsRepository;
        this.fieldRepository = fieldRepository;
    }

    @Override
    public int collectAll(String datasourceId) {
        DataSourceEntity ds = dsRepository.findById(datasourceId);
        if (ds == null) {
            log.warn("Datasource not found: {}", datasourceId);
            return 0;
        }

        Connector connector = connectorFactory.getConnector(ds.getDatasourceType());
        List<DataResource> resources = connector.listResources(
                ds.getConnectionConfig(), ds.getOrgId(), ds.getDatasourceName());

        int count = 0;
        for (DataResource resource : resources) {
            List<DataField> fields = collectFields(ds, resource.getSourcePath(), resource.getResourceId());
            fieldRepository.deleteByResourceId(resource.getResourceId());
            if (!fields.isEmpty()) {
                fieldRepository.batchInsert(fields);
            }
            resource.setFieldCount(fields.size());
            resource.setDatasourceId(datasourceId);

            catalogService.register(resource);
            count++;

            log.debug("Collected resource: {}.{} ({} fields)",
                    ds.getDatasourceName(), resource.getResourceName(), fields.size());
        }

        log.info("Metadata collection complete: {} resources from {}", count, ds.getDatasourceName());
        return count;
    }

    @Override
    public List<DataField> getFields(String resourceId) {
        return fieldRepository.findByResourceId(resourceId);
    }

    @SuppressWarnings("unchecked")
    private List<DataField> collectFields(DataSourceEntity ds, String sourcePath, String resourceId) {
        List<DataField> fields = new ArrayList<>();

        try {
            Map<String, String> config = mapper.readValue(ds.getConnectionConfig(), Map.class);

            try (Connection conn = DriverManager.getConnection(
                    config.get("jdbcUrl"),
                    config.get("username"),
                    config.get("password"))) {

                String[] parts = sourcePath.split("\\.");
                String schema = parts.length > 1 ? parts[0] : conn.getSchema();
                String tableName = parts.length > 1 ? parts[1] : parts[0];

                DatabaseMetaData metaData = conn.getMetaData();

                Set<String> pkColumns = new HashSet<>();
                try (ResultSet pks = metaData.getPrimaryKeys(conn.getCatalog(), schema, tableName)) {
                    while (pks.next()) pkColumns.add(pks.getString("COLUMN_NAME"));
                }

                try (ResultSet columns = metaData.getColumns(conn.getCatalog(), schema, tableName, "%")) {
                    int sortOrder = 0;
                    while (columns.next()) {
                        DataField field = new DataField();
                        field.setFieldId(UUID.randomUUID().toString().replace("-", ""));
                        field.setResourceId(resourceId);
                        field.setFieldName(columns.getString("COLUMN_NAME"));
                        field.setDataType(columns.getString("TYPE_NAME"));
                        field.setDataLength(columns.getInt("COLUMN_SIZE"));
                        field.setDataPrecision(columns.getInt("DECIMAL_DIGITS"));
                        field.setNullable("YES".equalsIgnoreCase(columns.getString("IS_NULLABLE")));
                        field.setPrimaryKey(pkColumns.contains(columns.getString("COLUMN_NAME")));
                        field.setDescription(columns.getString("REMARKS"));
                        field.setSortOrder(sortOrder++);
                        fields.add(field);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to collect fields for {}: {}", sourcePath, e.getMessage(), e);
        }

        return fields;
    }
}
