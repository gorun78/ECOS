package com.chinacreator.gzcm.runtime.core.dataaccess.storage.adapter.olap;

import java.util.Map;

import com.chinacreator.gzcm.runtime.core.dataaccess.storage.adapter.jdbc.BaseJdbcAdapter;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.model.StorageConfig;

/**
 * ClickHouse存储适配器
 * 
 * @author CDRC Runtime Team
 */
public class ClickHouseAdapter extends BaseJdbcAdapter {
    
    @Override
    protected String getDriverClassName() {
        // 支持两种驱动：新版本使用com.clickhouse.jdbc，旧版本使用ru.yandex.clickhouse
        // 优先使用新版本驱动
        return "com.clickhouse.jdbc.ClickHouseDriver";
    }
    
    @Override
    protected String buildConnectionUrl(StorageConfig config) {
        if (config.getConnectionString() != null && !config.getConnectionString().isEmpty()) {
            return config.getConnectionString();
        }
        
        StringBuilder url = new StringBuilder("jdbc:clickhouse://");
        url.append(config.getHost() != null ? config.getHost() : "localhost");
        
        if (config.getPort() != null) {
            url.append(":").append(config.getPort());
        } else {
            url.append(":8123"); // ClickHouse默认HTTP端口
        }
        
        if (config.getDatabase() != null) {
            url.append("/").append(config.getDatabase());
        }
        
        // 添加常用参数
        url.append("?");
        
        // 添加自定义属性
        if (config.getProperties() != null && !config.getProperties().isEmpty()) {
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
        // ClickHouse使用LIMIT offset, limit语法
        return sql + " LIMIT " + offset + ", " + limit;
    }
    
    @Override
    protected String escapeIdentifier(String identifier) {
        return "`" + identifier + "`";
    }
}

