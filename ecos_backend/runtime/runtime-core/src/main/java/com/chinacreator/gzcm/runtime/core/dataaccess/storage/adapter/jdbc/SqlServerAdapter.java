package com.chinacreator.gzcm.runtime.core.dataaccess.storage.adapter.jdbc;

import java.util.Map;

import com.chinacreator.gzcm.runtime.core.dataaccess.storage.model.StorageConfig;

/**
 * SQL Server存储适配器
 * 
 * @author CDRC Runtime Team
 */
public class SqlServerAdapter extends BaseJdbcAdapter {
    
    @Override
    protected String getDriverClassName() {
        return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    }
    
    @Override
    protected String buildConnectionUrl(StorageConfig config) {
        if (config.getConnectionString() != null && !config.getConnectionString().isEmpty()) {
            return config.getConnectionString();
        }
        
        StringBuilder url = new StringBuilder("jdbc:sqlserver://");
        url.append(config.getHost() != null ? config.getHost() : "localhost");
        
        if (config.getPort() != null) {
            url.append(":").append(config.getPort());
        } else {
            url.append(":1433");
        }
        
        // SQL Server使用分号分隔参数
        url.append(";");
        
        if (config.getDatabase() != null) {
            url.append("databaseName=").append(config.getDatabase()).append(";");
        }
        
        // 添加常用参数
        url.append("encrypt=false;trustServerCertificate=true;");
        
        // 添加自定义属性
        if (config.getProperties() != null) {
            for (Map.Entry<String, Object> entry : config.getProperties().entrySet()) {
                if (!url.toString().contains(entry.getKey() + "=")) {
                    url.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
                }
            }
        }
        
        return url.toString();
    }
    
    @Override
    protected String buildPaginationSql(String sql, int offset, int limit) {
        // SQL Server 2012+ 使用 OFFSET ... ROWS FETCH NEXT ... ROWS ONLY
        // 如果SQL中已经包含ORDER BY，直接添加OFFSET和FETCH
        String upperSql = sql.toUpperCase().trim();
        if (upperSql.contains("ORDER BY")) {
            return sql + " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
        } else {
            // 如果没有ORDER BY，需要添加一个（SQL Server要求）
            // 使用子查询方式，添加一个虚拟的ORDER BY
            StringBuilder paginatedSql = new StringBuilder();
            paginatedSql.append("SELECT * FROM (");
            paginatedSql.append("SELECT *, ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS rn FROM (");
            paginatedSql.append(sql);
            paginatedSql.append(") AS t");
            paginatedSql.append(") AS t2 WHERE rn > ").append(offset).append(" AND rn <= ").append(offset + limit);
            return paginatedSql.toString();
        }
    }
    
    @Override
    protected String escapeIdentifier(String identifier) {
        return "[" + identifier + "]";
    }
}

