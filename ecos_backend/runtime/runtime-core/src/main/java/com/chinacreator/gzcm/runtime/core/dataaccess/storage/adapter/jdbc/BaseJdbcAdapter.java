package com.chinacreator.gzcm.runtime.core.dataaccess.storage.adapter.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.runtime.core.dataaccess.model.FilterCondition;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.Pagination;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.QueryRequest;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.QueryResult;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.SortCondition;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.IStorageAdapter;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.model.StorageConfig;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.model.WriteRequest;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.model.WriteRequest.WriteMode;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataSchema;
import com.chinacreator.gzcm.runtime.core.datadescription.model.impl.DataSchemaImpl;
import com.chinacreator.gzcm.runtime.core.datadescription.model.impl.DataSchemaImpl.SchemaField;
import com.chinacreator.gzcm.runtime.core.i18n.I18nUtils;
import com.chinacreator.gzcm.runtime.core.i18n.LocaleResolver;

/**
 * JDBC存储适配器基类
 * 提供基于JDBC的统一数据访问实现
 * 
 * @author CDRC Runtime Team
 */
public abstract class BaseJdbcAdapter implements IStorageAdapter {

    private static final Logger logger = LoggerFactory.getLogger(BaseJdbcAdapter.class);

    protected Connection connection;
    protected StorageConfig config;
    protected boolean connected = false;
    protected boolean inTransaction = false;
    
    /**
     * 获取JDBC驱动类名（子类实现）
     */
    protected abstract String getDriverClassName();
    
    /**
     * 构建JDBC连接URL（子类实现）
     */
    protected abstract String buildConnectionUrl(StorageConfig config);
    
    /**
     * 获取分页SQL语法（子类实现）
     * 
     * @param sql 原始SQL
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 分页SQL
     */
    protected abstract String buildPaginationSql(String sql, int offset, int limit);
    
    /**
     * 转义标识符（表名、列名等）
     */
    protected String escapeIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }
    
    @Override
    public void connect(StorageConfig config) throws Exception {
        if (config == null) {
            String errorMsg = I18nUtils.getErrorMessage("invalid.config", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new IllegalArgumentException(errorMsg);
        }

        this.config = config;

        try {
            // 加载驱动
            Class.forName(getDriverClassName());
            
            // 构建连接URL
            String url = buildConnectionUrl(config);
            
            // 建立连接
            connection = DriverManager.getConnection(
                    url,
                    config.getUsername(),
                    config.getPassword()
            );
            
            // 设置连接属性
            if (config.getProperties() != null) {
                for (Map.Entry<String, Object> entry : config.getProperties().entrySet()) {
                    connection.setClientInfo(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            
            connected = true;
            logger.info(I18nUtils.getMessage("storage.connect.success", 
                    LocaleResolver.getDefaultLocaleCode()));
            
        } catch (ClassNotFoundException e) {
            connected = false;
            String errorMsg = I18nUtils.getErrorMessage("connection.failed", 
                    LocaleResolver.getDefaultLocaleCode(), getDriverClassName());
            logger.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        } catch (SQLException e) {
            connected = false;
            String errorMsg = I18nUtils.getErrorMessage("connection.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        }
    }

    @Override
    public void disconnect() throws Exception {
        if (connection != null && !connection.isClosed()) {
            try {
                if (inTransaction) {
                    rollback();
                }
            connection.close();
                logger.info(I18nUtils.getMessage("storage.disconnect.success", 
                        LocaleResolver.getDefaultLocaleCode()));
            } catch (SQLException e) {
                logger.error(I18nUtils.getErrorMessage("connection.failed", 
                        LocaleResolver.getDefaultLocaleCode()), e);
                throw new Exception(e);
            } finally {
                connected = false;
                connection = null;
            }
        }
    }

    @Override
    public boolean testConnection() throws Exception {
        if (connection == null || connection.isClosed()) {
            return false;
        }
        
        try (Statement stmt = connection.createStatement()) {
            stmt.executeQuery("SELECT 1");
            return true;
        } catch (SQLException e) {
            logger.error(I18nUtils.getErrorMessage("connection.failed", 
                    LocaleResolver.getDefaultLocaleCode()), e);
            return false;
        }
    }

    @Override
    public QueryResult<Map<String, Object>> query(QueryRequest request) throws Exception {
        if (!connected || connection == null) {
            String errorMsg = I18nUtils.getErrorMessage("connection.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new IllegalStateException(errorMsg);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            String resource = request.getDataProductId();
            if (resource == null || resource.isEmpty()) {
                String errorMsg = I18nUtils.getErrorMessage("data.not.found", 
                        LocaleResolver.getDefaultLocaleCode());
                throw new IllegalArgumentException(errorMsg);
            }

        // 构建SQL
            StringBuilder sql = new StringBuilder("SELECT ");
            
            // 选择列
            if (request.getColumns() != null && !request.getColumns().isEmpty()) {
                for (int i = 0; i < request.getColumns().size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append(escapeIdentifier(request.getColumns().get(i)));
                }
            } else {
                sql.append("*");
            }
            
            sql.append(" FROM ").append(escapeIdentifier(resource));
            
            // WHERE条件
        List<Object> params = new ArrayList<>();
            String whereClause = buildWhereClause(request.getFilter(), params);
            if (whereClause != null && !whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }
            
            // ORDER BY
            if (request.getSort() != null && !request.getSort().isEmpty()) {
                sql.append(" ORDER BY ");
                for (int i = 0; i < request.getSort().size(); i++) {
                    if (i > 0) sql.append(", ");
                    SortCondition sort = request.getSort().get(i);
                    sql.append(escapeIdentifier(sort.getField()))
                       .append(" ")
                       .append(sort.getDirection() == SortCondition.SortDirection.ASC ? "ASC" : "DESC");
                }
            }
            
            // 分页
            Pagination pagination = request.getPagination();
            int offset = 0;
            int limit = Integer.MAX_VALUE;
            if (pagination != null) {
                offset = pagination.getOffset();
                limit = pagination.getPageSize() != null ? pagination.getPageSize() : Integer.MAX_VALUE;
            }
            
            String finalSql = buildPaginationSql(sql.toString(), offset, limit);

        // 执行查询
        List<Map<String, Object>> data = new ArrayList<>();
            try (PreparedStatement pstmt = connection.prepareStatement(finalSql)) {
                // 设置参数
                for (int i = 0; i < params.size(); i++) {
                    pstmt.setObject(i + 1, params.get(i));
                }
                
                try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    data.add(row);
                }
            }
        }

        // 查询总数
        Long total = null;
        if (request.isIncludeTotal()) {
                total = count(resource, convertFilterToMap(request.getFilter()));
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            QueryResult<Map<String, Object>> result = new QueryResult<>(data, total);
            result.setDuration(duration);
            if (pagination != null) {
                pagination.setTotal(total);
                result.setPagination(pagination);
            }
            
            logger.debug(I18nUtils.getMessage("storage.query.success", 
                    LocaleResolver.getDefaultLocaleCode(), data.size()));

        return result;

        } catch (SQLException e) {
            String errorMsg = I18nUtils.getErrorMessage("query.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        }
    }

    @Override
    public List<Map<String, Object>> query(String resource, List<String> columns, 
            Map<String, Object> filter, Map<String, String> sort, 
            Integer offset, Integer limit) throws Exception {

        QueryRequest request = new QueryRequest();
        request.setDataProductId(resource);
        request.setColumns(columns);
        request.setFilter(convertMapToFilter(filter));
        
        if (sort != null && !sort.isEmpty()) {
            List<SortCondition> sortConditions = new ArrayList<>();
            for (Map.Entry<String, String> entry : sort.entrySet()) {
                SortCondition.SortDirection direction = "DESC".equalsIgnoreCase(entry.getValue()) 
                        ? SortCondition.SortDirection.DESC 
                        : SortCondition.SortDirection.ASC;
                sortConditions.add(new SortCondition(entry.getKey(), direction));
            }
            request.setSort(sortConditions);
        }
        
        if (offset != null || limit != null) {
            Pagination pagination = new Pagination();
            if (offset != null && limit != null) {
                pagination.setPageNum((offset / limit) + 1);
                pagination.setPageSize(limit);
            }
            request.setPagination(pagination);
        }
        
        QueryResult<Map<String, Object>> result = query(request);
        return result.getData();
    }

    @Override
    public int write(WriteRequest request) throws Exception {
        if (!connected || connection == null) {
            String errorMsg = I18nUtils.getErrorMessage("connection.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new IllegalStateException(errorMsg);
        }
        
        if (request == null || request.getResource() == null) {
            String errorMsg = I18nUtils.getErrorMessage("invalid.config", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new IllegalArgumentException(errorMsg);
        }
        
        String resource = request.getResource();
        List<Map<String, Object>> data = request.getData();
        WriteMode mode = request.getWriteMode() != null ? request.getWriteMode() : WriteMode.INSERT;
        
        if (data == null || data.isEmpty()) {
            return 0;
        }
        
        try {
            boolean useTransaction = request.getUseTransaction() != null ? request.getUseTransaction() : true;
            if (useTransaction && !inTransaction) {
                beginTransaction();
            }
            
            int affectedRows = 0;
            
            switch (mode) {
                case INSERT:
                    affectedRows = executeInsert(resource, data, request.getBatchSize());
                    break;
                case UPDATE:
                    affectedRows = executeUpdate(resource, data, request.getUpdateCondition());
                    break;
                case UPSERT:
                    affectedRows = executeUpsert(resource, data, request.getUpdateCondition());
                    break;
                case REPLACE:
                    affectedRows = executeReplace(resource, data);
                    break;
                default:
                    affectedRows = executeInsert(resource, data, request.getBatchSize());
            }
            
            if (useTransaction && inTransaction) {
                commit();
            }
            
            logger.info(I18nUtils.getMessage("storage.write.success", 
                    LocaleResolver.getDefaultLocaleCode(), affectedRows));
            
            return affectedRows;
            
        } catch (SQLException e) {
            if (inTransaction) {
                rollback();
            }
            String errorMsg = I18nUtils.getErrorMessage("write.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        }
    }

    @Override
    public long count(String resource, Map<String, Object> filter) throws Exception {
        if (!connected || connection == null) {
            String errorMsg = I18nUtils.getErrorMessage("connection.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new IllegalStateException(errorMsg);
        }
        
        try {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(escapeIdentifier(resource));
        List<Object> params = new ArrayList<>();

            String whereClause = buildWhereClause(convertMapToFilter(filter), params);
            if (whereClause != null && !whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }
            
            try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    pstmt.setObject(i + 1, params.get(i));
                }
                
                try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        return 0;

        } catch (SQLException e) {
            String errorMsg = I18nUtils.getErrorMessage("query.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        }
    }

    @Override
    public void insert(String resource, List<Map<String, Object>> data) throws Exception {
        WriteRequest request = new WriteRequest();
        request.setResource(resource);
        request.setData(data);
        request.setWriteMode(WriteMode.INSERT);
        write(request);
    }

    @Override
    public int update(String resource, Map<String, Object> filter, 
            Map<String, Object> updateFields) throws Exception {
        if (!connected || connection == null) {
            String errorMsg = I18nUtils.getErrorMessage("connection.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new IllegalStateException(errorMsg);
        }
        
        if (updateFields == null || updateFields.isEmpty()) {
            return 0;
        }
        
        try {
            StringBuilder sql = new StringBuilder("UPDATE ").append(escapeIdentifier(resource)).append(" SET ");
            List<Object> params = new ArrayList<>();
            
            boolean first = true;
            for (Map.Entry<String, Object> entry : updateFields.entrySet()) {
                if (!first) sql.append(", ");
                sql.append(escapeIdentifier(entry.getKey())).append(" = ?");
                params.add(entry.getValue());
                first = false;
            }
            
            String whereClause = buildWhereClause(convertMapToFilter(filter), params);
            if (whereClause != null && !whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }
            
            try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    pstmt.setObject(i + 1, params.get(i));
                }
                
                return pstmt.executeUpdate();
            }
            
        } catch (SQLException e) {
            String errorMsg = I18nUtils.getErrorMessage("update.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        }
    }

    @Override
    public int delete(String resource, Map<String, Object> filter, boolean softDelete) throws Exception {
        if (!connected || connection == null) {
            String errorMsg = I18nUtils.getErrorMessage("connection.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new IllegalStateException(errorMsg);
        }
        
        try {
        if (softDelete) {
                // 软删除：更新deleted字段
                Map<String, Object> updateFields = new HashMap<>();
                updateFields.put("deleted", true);
                return update(resource, filter, updateFields);
        } else {
                // 硬删除
                StringBuilder sql = new StringBuilder("DELETE FROM ").append(escapeIdentifier(resource));
                List<Object> params = new ArrayList<>();
                
                String whereClause = buildWhereClause(convertMapToFilter(filter), params);
                if (whereClause != null && !whereClause.isEmpty()) {
                    sql.append(" WHERE ").append(whereClause);
                }
                
                try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        pstmt.setObject(i + 1, params.get(i));
                    }
                    
                    return pstmt.executeUpdate();
                }
            }
            
        } catch (SQLException e) {
            String errorMsg = I18nUtils.getErrorMessage("delete.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        }
    }

    @Override
    public DataSchema getSchema(String resource) throws Exception {
        if (!connected || connection == null) {
            String errorMsg = I18nUtils.getErrorMessage("connection.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new IllegalStateException(errorMsg);
        }
        
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String[] parts = resource.split("\\.");
            String schema = parts.length > 1 ? parts[0] : null;
            String table = parts.length > 1 ? parts[1] : parts[0];
            
            DataSchemaImpl dataSchema = new DataSchemaImpl();
            dataSchema.setName(resource);
            List<SchemaField> fields = new ArrayList<>();
            
            try (ResultSet rs = metaData.getColumns(null, schema, table, null)) {
                while (rs.next()) {
                    SchemaField field = new SchemaField();
                    field.setName(rs.getString("COLUMN_NAME"));
                    field.setType(rs.getString("TYPE_NAME"));
                    field.setSize(rs.getInt("COLUMN_SIZE"));
                    field.setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    field.setDefaultValue(rs.getString("COLUMN_DEF"));
                fields.add(field);
                }
            }
            
            dataSchema.setFields(fields);
            return dataSchema;
            
        } catch (SQLException e) {
            String errorMsg = I18nUtils.getErrorMessage("query.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        }
    }

    @Override
    public Map<String, Object> getMetadata() throws Exception {
        if (!connected || connection == null) {
            String errorMsg = I18nUtils.getErrorMessage("connection.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new IllegalStateException(errorMsg);
        }
        
        try {
            DatabaseMetaData metaData = connection.getMetaData();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("databaseProductName", metaData.getDatabaseProductName());
        metadata.put("databaseProductVersion", metaData.getDatabaseProductVersion());
        metadata.put("driverName", metaData.getDriverName());
        metadata.put("driverVersion", metaData.getDriverVersion());
            metadata.put("url", metaData.getURL());
            metadata.put("username", metaData.getUserName());
        return metadata;
        } catch (SQLException e) {
            String errorMsg = I18nUtils.getErrorMessage("query.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        }
    }

    @Override
    public void beginTransaction() throws Exception {
        if (!connected || connection == null) {
            String errorMsg = I18nUtils.getErrorMessage("connection.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            throw new IllegalStateException(errorMsg);
        }
        
        try {
            connection.setAutoCommit(false);
            inTransaction = true;
            logger.debug(I18nUtils.getMessage("storage.transaction.begin", 
                    LocaleResolver.getDefaultLocaleCode()));
        } catch (SQLException e) {
            String errorMsg = I18nUtils.getErrorMessage("transaction.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        }
    }

    @Override
    public void commit() throws Exception {
        if (!connected || connection == null) {
            return;
        }
        
        try {
            connection.commit();
            connection.setAutoCommit(true);
            inTransaction = false;
            logger.debug(I18nUtils.getMessage("storage.transaction.commit", 
                    LocaleResolver.getDefaultLocaleCode()));
        } catch (SQLException e) {
            String errorMsg = I18nUtils.getErrorMessage("transaction.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        }
    }

    @Override
    public void rollback() throws Exception {
        if (!connected || connection == null) {
            return;
        }
        
        try {
            connection.rollback();
            connection.setAutoCommit(true);
            inTransaction = false;
            logger.debug(I18nUtils.getMessage("storage.transaction.rollback", 
                    LocaleResolver.getDefaultLocaleCode()));
        } catch (SQLException e) {
            String errorMsg = I18nUtils.getErrorMessage("transaction.failed", 
                    LocaleResolver.getDefaultLocaleCode());
            logger.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        }
    }

    @Override
    public boolean supportsTransaction() {
        return true;
    }
    
    @Override
    public boolean isConnected() {
        try {
            return connected && connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 构建WHERE子句
     */
    private String buildWhereClause(FilterCondition filter, List<Object> params) {
        if (filter == null) {
            return null;
        }
        
        StringBuilder sql = new StringBuilder();
        FilterCondition.ConditionType type = filter.getType();
        
        switch (type) {
            case EQUALS:
                sql.append(escapeIdentifier(filter.getField())).append(" = ?");
                params.add(filter.getValue());
                break;
            case NOT_EQUALS:
                sql.append(escapeIdentifier(filter.getField())).append(" != ?");
                params.add(filter.getValue());
                break;
            case GREATER_THAN:
                sql.append(escapeIdentifier(filter.getField())).append(" > ?");
                params.add(filter.getValue());
                break;
            case GREATER_THAN_OR_EQUAL:
                sql.append(escapeIdentifier(filter.getField())).append(" >= ?");
                params.add(filter.getValue());
                break;
            case LESS_THAN:
                sql.append(escapeIdentifier(filter.getField())).append(" < ?");
                params.add(filter.getValue());
                break;
            case LESS_THAN_OR_EQUAL:
                sql.append(escapeIdentifier(filter.getField())).append(" <= ?");
                params.add(filter.getValue());
                break;
            case IN:
                sql.append(escapeIdentifier(filter.getField())).append(" IN (");
                List<Object> values = filter.getValues();
                for (int i = 0; i < values.size(); i++) {
                if (i > 0) sql.append(", ");
                    sql.append("?");
                    params.add(values.get(i));
                }
                sql.append(")");
                break;
            case NOT_IN:
                sql.append(escapeIdentifier(filter.getField())).append(" NOT IN (");
                values = filter.getValues();
                for (int i = 0; i < values.size(); i++) {
                if (i > 0) sql.append(", ");
                    sql.append("?");
                    params.add(values.get(i));
                }
                sql.append(")");
                break;
            case LIKE:
                sql.append(escapeIdentifier(filter.getField())).append(" LIKE ?");
                params.add(filter.getValue());
                break;
            case NOT_LIKE:
                sql.append(escapeIdentifier(filter.getField())).append(" NOT LIKE ?");
                params.add(filter.getValue());
                break;
            case IS_NULL:
                sql.append(escapeIdentifier(filter.getField())).append(" IS NULL");
                break;
            case IS_NOT_NULL:
                sql.append(escapeIdentifier(filter.getField())).append(" IS NOT NULL");
                break;
            case BETWEEN:
                sql.append(escapeIdentifier(filter.getField())).append(" BETWEEN ? AND ?");
                params.add(filter.getStartValue());
                params.add(filter.getEndValue());
                break;
            case AND:
                if (filter.getConditions() != null && !filter.getConditions().isEmpty()) {
                    for (int i = 0; i < filter.getConditions().size(); i++) {
                        if (i > 0) sql.append(" AND ");
                        sql.append("(").append(buildWhereClause(filter.getConditions().get(i), params)).append(")");
                    }
                }
                break;
            case OR:
                if (filter.getConditions() != null && !filter.getConditions().isEmpty()) {
                    for (int i = 0; i < filter.getConditions().size(); i++) {
                        if (i > 0) sql.append(" OR ");
                        sql.append("(").append(buildWhereClause(filter.getConditions().get(i), params)).append(")");
                    }
                }
                break;
            case NOT:
                if (filter.getConditions() != null && !filter.getConditions().isEmpty()) {
                    sql.append("NOT (").append(buildWhereClause(filter.getConditions().get(0), params)).append(")");
                }
                break;
            default:
                return null;
        }

        return sql.toString();
    }
    
    /**
     * 执行INSERT操作
     */
    private int executeInsert(String resource, List<Map<String, Object>> data, Integer batchSize) throws SQLException {
        if (data == null || data.isEmpty()) {
            return 0;
        }
        
        Map<String, Object> firstRow = data.get(0);
        List<String> columns = new ArrayList<>(firstRow.keySet());

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(escapeIdentifier(resource)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(escapeIdentifier(columns.get(i)));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(")");

        int batch = batchSize != null && batchSize > 0 ? batchSize : 1000;
        int totalAffected = 0;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < data.size(); i++) {
                Map<String, Object> row = data.get(i);
                for (int j = 0; j < columns.size(); j++) {
                    pstmt.setObject(j + 1, row.get(columns.get(j)));
                }
                pstmt.addBatch();
                
                if ((i + 1) % batch == 0) {
                    int[] results = pstmt.executeBatch();
                    for (int r : results) {
                        totalAffected += r;
                    }
                    pstmt.clearBatch();
                }
            }
            
            int[] results = pstmt.executeBatch();
            for (int r : results) {
                totalAffected += r;
            }
        }
        
        return totalAffected;
    }
    
    /**
     * 执行UPDATE操作
     */
    private int executeUpdate(String resource, List<Map<String, Object>> data, 
            Map<String, Object> updateCondition) throws Exception {
        // 简化实现：逐行更新
        int totalAffected = 0;
        for (Map<String, Object> row : data) {
            totalAffected += update(resource, updateCondition, row);
        }
        return totalAffected;
    }
    
    /**
     * 执行UPSERT操作
     */
    private int executeUpsert(String resource, List<Map<String, Object>> data, 
            Map<String, Object> updateCondition) throws Exception {
        // 简化实现：先尝试更新，如果影响行数为0则插入
        int totalAffected = 0;
        for (Map<String, Object> row : data) {
            int updated = update(resource, updateCondition, row);
        if (updated == 0) {
                insert(resource, Collections.singletonList(row));
                totalAffected++;
            } else {
                totalAffected += updated;
            }
        }
        return totalAffected;
    }
    
    /**
     * 执行REPLACE操作
     */
    private int executeReplace(String resource, List<Map<String, Object>> data) throws Exception {
        // 简化实现：先删除再插入
        delete(resource, Collections.emptyMap(), false);
        return executeInsert(resource, data, null);
    }
    
    /**
     * 将Map转换为FilterCondition（简化版）
     */
    private FilterCondition convertMapToFilter(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        
        if (filter.size() == 1) {
            Map.Entry<String, Object> entry = filter.entrySet().iterator().next();
            FilterCondition condition = new FilterCondition();
            condition.setType(FilterCondition.ConditionType.EQUALS);
            condition.setField(entry.getKey());
            condition.setValue(entry.getValue());
            return condition;
        } else {
            FilterCondition andCondition = new FilterCondition();
            andCondition.setType(FilterCondition.ConditionType.AND);
            List<FilterCondition> conditions = new ArrayList<>();
            for (Map.Entry<String, Object> entry : filter.entrySet()) {
                FilterCondition condition = new FilterCondition();
                condition.setType(FilterCondition.ConditionType.EQUALS);
                condition.setField(entry.getKey());
                condition.setValue(entry.getValue());
                conditions.add(condition);
            }
            andCondition.setConditions(conditions);
            return andCondition;
        }
    }
    
    /**
     * 将FilterCondition转换为Map（简化版，用于count方法）
     */
    private Map<String, Object> convertFilterToMap(FilterCondition filter) {
        Map<String, Object> map = new HashMap<>();
        if (filter != null && filter.getType() == FilterCondition.ConditionType.EQUALS) {
            map.put(filter.getField(), filter.getValue());
        }
        return map;
    }
}
