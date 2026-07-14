package com.chinacreator.gzcm.runtime.core.dataaccess.storage.adapter.jdbc;

import java.util.Map;

import com.chinacreator.gzcm.runtime.core.dataaccess.storage.model.StorageConfig;

/**
 * PostgreSQL存储适配器
 * 
 * @author CDRC Runtime Team
 */
public class PostgresqlAdapter extends BaseJdbcAdapter {
    
    @Override
    protected String getDriverClassName() {
        return "org.postgresql.Driver";
    }
    
    @Override
    protected String buildConnectionUrl(StorageConfig config) {
        if (config.getConnectionString() != null && !config.getConnectionString().isEmpty()) {
            return config.getConnectionString();
        }
        
        StringBuilder url = new StringBuilder("jdbc:postgresql://");
        url.append(config.getHost() != null ? config.getHost() : "localhost");
        
        if (config.getPort() != null) {
            url.append(":").append(config.getPort());
        } else {
            url.append(":5432");
        }
        
        if (config.getDatabase() != null) {
            url.append("/").append(config.getDatabase());
        }
        
        // 添加自定义属性
        if (config.getProperties() != null && !config.getProperties().isEmpty()) {
            url.append("?");
            boolean first = true;
            for (Map.Entry<String, Object> entry : config.getProperties().entrySet()) {
                if (!first) url.append("&");
                url.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }
        
        return url.toString();
    }
    
    @Override
    protected String buildPaginationSql(String sql, int offset, int limit) {
        return sql + " LIMIT " + limit + " OFFSET " + offset;
    }
    
    @Override
    protected String escapeIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }
}
