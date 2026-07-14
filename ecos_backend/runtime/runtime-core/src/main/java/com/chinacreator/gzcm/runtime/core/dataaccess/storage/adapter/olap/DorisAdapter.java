package com.chinacreator.gzcm.runtime.core.dataaccess.storage.adapter.olap;

import java.util.Map;

import com.chinacreator.gzcm.runtime.core.dataaccess.storage.adapter.jdbc.BaseJdbcAdapter;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.model.StorageConfig;

/**
 * Doris存储适配器
 * Doris兼容MySQL协议，使用MySQL驱动
 * 
 * @author CDRC Runtime Team
 */
public class DorisAdapter extends BaseJdbcAdapter {
    
    @Override
    protected String getDriverClassName() {
        return "com.mysql.cj.jdbc.Driver";
    }
    
    @Override
    protected String buildConnectionUrl(StorageConfig config) {
        if (config.getConnectionString() != null && !config.getConnectionString().isEmpty()) {
            return config.getConnectionString();
        }
        
        StringBuilder url = new StringBuilder("jdbc:mysql://");
        url.append(config.getHost() != null ? config.getHost() : "localhost");
        
        if (config.getPort() != null) {
            url.append(":").append(config.getPort());
        } else {
            url.append(":9030"); // Doris默认FE端口
        }
        
        if (config.getDatabase() != null) {
            url.append("/").append(config.getDatabase());
        }
        
        // 添加常用参数
        url.append("?useSSL=false&serverTimezone=UTC&characterEncoding=utf8");
        
        // 添加自定义属性
        if (config.getProperties() != null) {
            for (Map.Entry<String, Object> entry : config.getProperties().entrySet()) {
                if (!url.toString().contains(entry.getKey() + "=")) {
                    url.append("&").append(entry.getKey()).append("=").append(entry.getValue());
                }
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
        return "`" + identifier + "`";
    }
}
