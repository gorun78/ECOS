package com.chinacreator.gzcm.runtime.core.datadescription.discovery.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chinacreator.gzcm.runtime.core.datadescription.discovery.IDataDescriptionDiscovery;
import com.chinacreator.gzcm.runtime.core.datadescription.enums.DataType;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataDescription;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataMetadata;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataSchema;
import com.chinacreator.gzcm.runtime.core.datadescription.model.impl.DataSchemaImpl;
import com.chinacreator.gzcm.runtime.core.datadescription.service.IDataDescriptionService;
import com.chinacreator.gzcm.runtime.core.datadescription.service.impl.DataDescriptionServiceImpl;

/**
 * 数据描述发现服务实现
 * 支持数据库表、文件和API的自动发现
 * 
 * @author CDRC Runtime Team
 */
public class DataDescriptionDiscoveryImpl implements IDataDescriptionDiscovery {
    
    private final IDataDescriptionService dataDescriptionService;
    
    public DataDescriptionDiscoveryImpl() {
        this.dataDescriptionService = new DataDescriptionServiceImpl();
    }
    
    public DataDescriptionDiscoveryImpl(IDataDescriptionService dataDescriptionService) {
        this.dataDescriptionService = dataDescriptionService;
    }
    
    @Override
    public List<DataDescription> discoverDatabaseTable(DatabaseConnectionInfo connectionInfo, String tableName) throws Exception {
        if (connectionInfo == null) {
            throw new IllegalArgumentException("Database connection info cannot be null");
        }
        
        List<DataDescription> descriptions = new ArrayList<>();
        Connection conn = null;
        
        try {
            // 加载驱动
            if (connectionInfo.getDriverClass() != null) {
                Class.forName(connectionInfo.getDriverClass());
            }
            
            // 建立连接
            String jdbcUrl = connectionInfo.buildJdbcUrl();
            conn = DriverManager.getConnection(
                jdbcUrl,
                connectionInfo.getUsername(),
                connectionInfo.getPassword()
            );
            
            DatabaseMetaData metaData = conn.getMetaData();
            
            // 获取表列表
            List<String> tables = new ArrayList<>();
            if (tableName != null && !tableName.trim().isEmpty()) {
                tables.add(tableName);
            } else {
                // 获取所有表
                ResultSet rs = metaData.getTables(
                    connectionInfo.getDatabase(),
                    null,
                    null,
                    new String[]{"TABLE", "VIEW"}
                );
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
                rs.close();
            }
            
            // 为每个表创建数据描述
            for (String table : tables) {
                DataDescription description = createTableDescription(
                    conn, metaData, connectionInfo.getDatabase(), table
                );
                descriptions.add(description);
                
                // 可选：保存到数据描述服务
                // dataDescriptionService.createDataDescription(description);
            }
            
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception e) {
                    // 忽略关闭异常
                }
            }
        }
        
        return descriptions;
    }
    
    @Override
    public DataDescription discoverFile(String filePath) throws Exception {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        
        // 创建数据描述
        DataDescriptionImpl description = new DataDescriptionImpl();
        description.setId(UUID.randomUUID().toString());
        description.setDataType(determineFileDataType(file));
        
        // 创建元数据
        DataMetadata metadata = createFileMetadata(file);
        description.setMetadata(metadata);
        
        // 可选：创建Schema（对于结构化文件）
        if (isStructuredFile(file)) {
            DataSchema schema = createFileSchema(file);
            description.setSchema(schema);
        }
        
        return description;
    }
    
    @Override
    public List<DataDescription> discoverApi(String apiUrl) throws Exception {
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("API URL cannot be null or empty");
        }
        
        List<DataDescription> descriptions = new ArrayList<>();
        
        try {
            // 下载API文档
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new Exception("Failed to fetch API document: HTTP " + conn.getResponseCode());
            }
            
            // 读取内容
            InputStream is = conn.getInputStream();
            byte[] buffer = new byte[8192];
            StringBuilder content = new StringBuilder();
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                content.append(new String(buffer, 0, bytesRead, "UTF-8"));
            }
            is.close();
            conn.disconnect();
            
            // 解析OpenAPI/Swagger JSON
            String jsonContent = content.toString();
            descriptions = parseOpenApiDocument(jsonContent, apiUrl);
            
        } catch (Exception e) {
            throw new Exception("Failed to discover API: " + e.getMessage(), e);
        }
        
        return descriptions;
    }
    
    /**
     * 创建表的数据描述
     */
    private DataDescription createTableDescription(Connection conn, DatabaseMetaData metaData, 
            String database, String tableName) throws Exception {
        DataDescriptionImpl description = new DataDescriptionImpl();
        description.setId(UUID.randomUUID().toString());
        description.setDataType(DataType.STRUCTURED);
        
        // 创建Schema
        DataSchemaImpl schema = new DataSchemaImpl();
        schema.setName(tableName);
        schema.setSchemaType(DataSchema.SchemaType.CUSTOM);
        
        // 获取列信息
        List<DataSchemaImpl.SchemaField> fields = new ArrayList<>();
        ResultSet columns = metaData.getColumns(database, null, tableName, null);
        while (columns.next()) {
            DataSchemaImpl.SchemaField field = new DataSchemaImpl.SchemaField();
            field.setName(columns.getString("COLUMN_NAME"));
            field.setType(columns.getString("TYPE_NAME"));
            field.setSize(columns.getInt("COLUMN_SIZE"));
            field.setNullable(columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
            field.setDefaultValue(columns.getString("COLUMN_DEF"));
            fields.add(field);
        }
        columns.close();
        schema.setFields(fields);
        
        // 构建Schema内容（JSON格式）
        StringBuilder schemaContent = new StringBuilder();
        schemaContent.append("{\"type\":\"object\",\"properties\":{");
        boolean first = true;
        for (DataSchemaImpl.SchemaField field : fields) {
            if (!first) schemaContent.append(",");
            schemaContent.append("\"").append(field.getName()).append("\":{");
            schemaContent.append("\"type\":\"").append(mapJdbcTypeToJsonType(field.getType())).append("\"");
            if (field.getSize() != null) {
                schemaContent.append(",\"maxLength\":").append(field.getSize());
            }
            if (field.getNullable() != null && !field.getNullable()) {
                schemaContent.append(",\"required\":true");
            }
            schemaContent.append("}");
            first = false;
        }
        schemaContent.append("}}");
        schema.setSchemaContent(schemaContent.toString());
        
        description.setSchema(schema);
        
        // 创建元数据
        DataMetadata metadata = new DataMetadataImpl();
        metadata.setName(tableName);
        metadata.setType("TABLE");
        metadata.setFormat("DATABASE");
        metadata.addExtension("database", database);
        metadata.addExtension("table", tableName);
        
        description.setMetadata(metadata);
        
        return description;
    }
    
    /**
     * 创建文件的元数据
     */
    private DataMetadata createFileMetadata(File file) {
        DataMetadataImpl metadata = new DataMetadataImpl();
        metadata.setName(file.getName());
        metadata.setType(determineFileType(file));
        metadata.setFormat(getFileExtension(file));
        metadata.setSize(file.length());
        metadata.addExtension("path", file.getAbsolutePath());
        metadata.addExtension("lastModified", String.valueOf(file.lastModified()));
        return metadata;
    }
    
    /**
     * 创建文件的Schema
     */
    private DataSchema createFileSchema(File file) throws Exception {
        // 简化实现：对于CSV、JSON等结构化文件，可以尝试解析
        // 这里返回null，表示不自动创建Schema
        return null;
    }
    
    /**
     * 判断文件数据类型
     */
    private DataType determineFileDataType(File file) {
        String extension = getFileExtension(file).toLowerCase();
        if (extension.equals("csv") || extension.equals("json") || extension.equals("xml")) {
            return DataType.STRUCTURED;
        } else if (extension.equals("pdf") || extension.equals("doc") || extension.equals("docx") || 
                   extension.equals("xls") || extension.equals("xlsx")) {
            return DataType.FILE;
        } else if (extension.equals("jpg") || extension.equals("png") || extension.equals("gif") || 
                   extension.equals("svg")) {
            return DataType.FILE;
        } else {
            return DataType.FILE;
        }
    }
    
    /**
     * 判断是否为结构化文件
     */
    private boolean isStructuredFile(File file) {
        String extension = getFileExtension(file).toLowerCase();
        return extension.equals("csv") || extension.equals("json") || extension.equals("xml");
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1) : "";
    }
    
    /**
     * 判断文件类型
     */
    private String determineFileType(File file) {
        String extension = getFileExtension(file).toLowerCase();
        if (extension.equals("csv") || extension.equals("json") || extension.equals("xml")) {
            return "STRUCTURED";
        } else if (extension.equals("pdf") || extension.equals("doc") || extension.equals("docx")) {
            return "DOCUMENT";
        } else if (extension.equals("xls") || extension.equals("xlsx")) {
            return "SPREADSHEET";
        } else if (extension.equals("jpg") || extension.equals("png") || extension.equals("gif")) {
            return "IMAGE";
        } else {
            return "FILE";
        }
    }
    
    /**
     * 解析OpenAPI文档
     */
    private List<DataDescription> parseOpenApiDocument(String jsonContent, String apiUrl) throws Exception {
        List<DataDescription> descriptions = new ArrayList<>();
        
        // 简化实现：使用简单的JSON解析提取API路径
        // 实际应使用OpenAPI解析库（如swagger-parser）
        try {
            // 查找paths节点
            int pathsIndex = jsonContent.indexOf("\"paths\"");
            if (pathsIndex < 0) {
                // 可能是Swagger 2.0格式
                pathsIndex = jsonContent.indexOf("\"swagger\"");
            }
            
            if (pathsIndex >= 0) {
                // 简化实现：创建一个通用的API数据描述
                DataDescriptionImpl description = new DataDescriptionImpl();
                description.setId(UUID.randomUUID().toString());
                description.setDataType(DataType.SERVICE);
                
                DataMetadataImpl metadata = new DataMetadataImpl();
                metadata.setName("API: " + apiUrl);
                metadata.setType("REST_API");
                metadata.setFormat("OPENAPI");
                metadata.addExtension("url", apiUrl);
                description.setMetadata(metadata);
                
                descriptions.add(description);
            }
        } catch (Exception e) {
            throw new Exception("Failed to parse OpenAPI document: " + e.getMessage(), e);
        }
        
        return descriptions;
    }
    
    /**
     * 将JDBC类型映射到JSON Schema类型
     */
    private String mapJdbcTypeToJsonType(String jdbcType) {
        if (jdbcType == null) {
            return "string";
        }
        String type = jdbcType.toUpperCase();
        if (type.contains("INT") || type.contains("NUMBER") || type.contains("NUMERIC")) {
            if (type.contains("BIGINT") || type.contains("LONG")) {
                return "integer";
            }
            if (type.contains("DECIMAL") || type.contains("DOUBLE") || type.contains("FLOAT") || 
                type.contains("REAL")) {
                return "number";
            }
            return "integer";
        } else if (type.contains("CHAR") || type.contains("TEXT") || type.contains("CLOB")) {
            return "string";
        } else if (type.contains("DATE") || type.contains("TIME") || type.contains("TIMESTAMP")) {
            return "string";
        } else if (type.contains("BOOLEAN") || type.contains("BIT")) {
            return "boolean";
        } else if (type.contains("BLOB") || type.contains("BINARY")) {
            return "string";
        } else {
            return "string";
        }
    }
    
    /**
     * DataDescription实现类（用于创建实例）
     */
    private static class DataDescriptionImpl implements DataDescription {
        private String id;
        private DataType dataType;
        private DataSchema schema;
        private DataMetadata metadata;
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        @Override
        public DataType getDataType() {
            return dataType;
        }
        
        public void setDataType(DataType dataType) {
            this.dataType = dataType;
        }
        
        @Override
        public DataSchema getSchema() {
            return schema;
        }
        
        @Override
        public void setSchema(DataSchema schema) {
            this.schema = schema;
        }
        
        @Override
        public DataMetadata getMetadata() {
            return metadata;
        }
        
        @Override
        public void setMetadata(DataMetadata metadata) {
            this.metadata = metadata;
        }
        
        @Override
        public boolean validate() throws ValidationException {
            if (dataType == null) {
                throw new ValidationException("DataType is required");
            }
            if (metadata == null) {
                throw new ValidationException("DataMetadata is required");
            }
            if (metadata.getName() == null || metadata.getName().trim().isEmpty()) {
                throw new ValidationException("DataMetadata name is required");
            }
            return true;
        }
        
        @Override
        public boolean validateData(Object data) throws ValidationException {
            if (schema != null) {
                try {
                    return schema.validate(data);
                } catch (DataSchema.ValidationException e) {
                    throw new ValidationException("Schema validation failed: " + e.getMessage(), e);
                }
            }
            return true;
        }
    }
    
    /**
     * DataMetadata实现类
     */
    private static class DataMetadataImpl implements DataMetadata {
        private String name;
        private String description;
        private String type;
        private String format;
        private Long size;
        private String encoding;
        private Map<String, Object> extensions = new HashMap<>();
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public void setName(String name) {
            this.name = name;
        }
        
        @Override
        public String getDescription() {
            return description;
        }
        
        @Override
        public void setDescription(String description) {
            this.description = description;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        @Override
        public void setType(String type) {
            this.type = type;
        }
        
        @Override
        public String getFormat() {
            return format;
        }
        
        @Override
        public void setFormat(String format) {
            this.format = format;
        }
        
        @Override
        public Long getSize() {
            return size;
        }
        
        @Override
        public void setSize(Long size) {
            this.size = size;
        }
        
        @Override
        public String getEncoding() {
            return encoding;
        }
        
        @Override
        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }
        
        @Override
        public Map<String, Object> getExtensions() {
            return new HashMap<>(extensions);
        }
        
        @Override
        public void setExtensions(Map<String, Object> extensions) {
            this.extensions = extensions != null ? new HashMap<>(extensions) : new HashMap<>();
        }
        
        @Override
        public void addExtension(String key, Object value) {
            extensions.put(key, value);
        }
        
        @Override
        public Object getExtension(String key) {
            return extensions.get(key);
        }
        
        @Override
        public Object removeExtension(String key) {
            return extensions.remove(key);
        }
    }
}

