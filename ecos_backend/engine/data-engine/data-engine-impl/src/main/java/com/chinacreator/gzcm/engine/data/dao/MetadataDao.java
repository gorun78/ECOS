package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.common.data.model.DataField;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MetadataServiceImpl 的 JDBC 数据访问层。
 * <p>td_data_field 字段清单查询走本 DAO。</p>
 */
@Repository
public class MetadataDao extends JdbcTemplateDaoSupport {

    public List<DataField> getFields(String resourceId) {
        return jdbcTemplate.query(
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
