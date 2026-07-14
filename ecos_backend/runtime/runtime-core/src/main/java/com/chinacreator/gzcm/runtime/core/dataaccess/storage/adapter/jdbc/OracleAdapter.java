package com.chinacreator.gzcm.runtime.core.dataaccess.storage.adapter.jdbc;

import java.util.Map;

import com.chinacreator.gzcm.runtime.core.dataaccess.storage.model.StorageConfig;

/**
 * Oracle存储适配器
 * 
 * @author CDRC Runtime Team
 */
public class OracleAdapter extends BaseJdbcAdapter {
    
    @Override
    protected String getDriverClassName() {
        return "oracle.jdbc.OracleDriver";
    }
    
    @Override
    protected String buildConnectionUrl(StorageConfig config) {
        if (config.getConnectionString() != null && !config.getConnectionString().isEmpty()) {
            return config.getConnectionString();
        }
        
        StringBuilder url = new StringBuilder("jdbc:oracle:thin:@");
        url.append(config.getHost() != null ? config.getHost() : "localhost");
        
        if (config.getPort() != null) {
            url.append(":").append(config.getPort());
        } else {
            url.append(":1521");
        }
        
        if (config.getDatabase() != null) {
            url.append(":").append(config.getDatabase());
        } else {
            url.append(":ORCL");
        }
        
        return url.toString();
    }
    
    @Override
    protected String buildPaginationSql(String sql, int offset, int limit) {
        // Oracle使用ROWNUM进行分页
        StringBuilder paginatedSql = new StringBuilder();
        paginatedSql.append("SELECT * FROM (");
        paginatedSql.append("SELECT a.*, ROWNUM rn FROM (");
        paginatedSql.append(sql);
        paginatedSql.append(") a WHERE ROWNUM <= ").append(offset + limit);
        paginatedSql.append(") WHERE rn > ").append(offset);
        return paginatedSql.toString();
    }
    
    @Override
    protected String escapeIdentifier(String identifier) {
        return "\"" + identifier.toUpperCase() + "\"";
    }
}
