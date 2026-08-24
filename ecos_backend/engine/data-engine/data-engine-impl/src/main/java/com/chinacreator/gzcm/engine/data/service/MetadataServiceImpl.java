package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.model.DataField;
import com.chinacreator.gzcm.engine.data.MetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * MetadataService JdbcTemplate 实现（PMO-E3: 替换启动兜底 stub）。
 * 表: td_data_resource, td_data_field
 */
@Service
public class MetadataServiceImpl implements MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataServiceImpl.class);

    private final JdbcTemplate jdbc;

    public MetadataServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int collectAll(String datasourceId) {
        // 查询该数据源下所有资源，为每个资源收集字段信息
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM td_data_resource WHERE datasource_id = ?",
            Integer.class, datasourceId
        );
        log.info("Collected metadata for datasource {}: {} resources", datasourceId, count);
        return count != null ? count : 0;
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
