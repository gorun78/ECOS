package com.chinacreator.gzcm.runtime.core.database.impl;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;

/**
 * 系统数据库访问实现
 * 提供系统管理模块的数据库访问功能
 * 使用 Spring DataSource 访问数据库
 * 
 * @author CDRC Runtime Team
 */
@Component
public class SystemDatabaseAccessImpl implements ISystemDatabaseAccess {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemDatabaseAccessImpl.class);
    
    // Spring DataSource，通过依赖注入获取
    private final DataSource dataSource;
    
    // 内存存储：tableName -> primaryKey -> entity（保留用于兼容性）
    private final ConcurrentMap<String, ConcurrentMap<String, Object>> dataStore = new ConcurrentHashMap<>();
    
    // 事务状态
    private boolean inTransaction = false;
    
    // 事务连接（用于事务管理）
    private Connection transactionConnection = null;
    
    // 事务中的数据变更（用于回滚）
    private final List<TransactionOperation> transactionOperations = new ArrayList<>();
    
    // SQL 配置文件缓存：sqlConfigPath -> Map<sqlName, sql>
    private final ConcurrentMap<String, Map<String, String>> sqlConfigCache = new ConcurrentHashMap<>();
    
    /**
     * 构造函数，通过Spring注入DataSource
     */
    @Autowired
    public SystemDatabaseAccessImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * 无参构造函数（用于兼容性）
     */
    public SystemDatabaseAccessImpl() {
        this.dataSource = null;
        logger.warn("SystemDatabaseAccessImpl initialized without DataSource. Database operations may fail.");
    }

    @Override
    public <T> void insert(String tableName, T entity) throws DatabaseAccessException {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new DatabaseAccessException("Table name cannot be null or empty");
        }
        if (entity == null) {
            throw new DatabaseAccessException("Entity cannot be null");
        }
        
        // 简化实现：使用反射获取主键值（实际应使用ORM框架）
        String primaryKey = extractPrimaryKey(entity);
        if (primaryKey == null) {
            primaryKey = generatePrimaryKey();
        }
        
        ConcurrentMap<String, Object> tableData = dataStore.computeIfAbsent(tableName, 
            k -> new ConcurrentHashMap<>());
        
        if (tableData.containsKey(primaryKey)) {
            throw new DatabaseAccessException("Entity with primary key " + primaryKey + " already exists");
        }
        
        tableData.put(primaryKey, entity);
        
        // 记录事务操作
        if (inTransaction) {
            transactionOperations.add(new TransactionOperation("INSERT", tableName, primaryKey, null, entity));
        }
    }

    @Override
    public <T> void update(String tableName, T entity) throws DatabaseAccessException {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new DatabaseAccessException("Table name cannot be null or empty");
        }
        if (entity == null) {
            throw new DatabaseAccessException("Entity cannot be null");
        }
        
        String primaryKey = extractPrimaryKey(entity);
        if (primaryKey == null) {
            throw new DatabaseAccessException("Cannot determine primary key for entity");
        }
        
        ConcurrentMap<String, Object> tableData = dataStore.get(tableName);
        if (tableData == null || !tableData.containsKey(primaryKey)) {
            throw new DatabaseAccessException("Entity with primary key " + primaryKey + " not found");
        }
        
        // 记录旧值用于回滚
        Object oldValue = null;
        if (inTransaction) {
            oldValue = tableData.get(primaryKey);
        }
        
        tableData.put(primaryKey, entity);
        
        // 记录事务操作
        if (inTransaction) {
            transactionOperations.add(new TransactionOperation("UPDATE", tableName, primaryKey, oldValue, entity));
        }
    }

    @Override
    public void delete(String tableName, String primaryKey) throws DatabaseAccessException {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new DatabaseAccessException("Table name cannot be null or empty");
        }
        if (primaryKey == null || primaryKey.trim().isEmpty()) {
            throw new DatabaseAccessException("Primary key cannot be null or empty");
        }
        
        ConcurrentMap<String, Object> tableData = dataStore.get(tableName);
        if (tableData == null || !tableData.containsKey(primaryKey)) {
            throw new DatabaseAccessException("Entity with primary key " + primaryKey + " not found");
        }
        
        // 记录旧值用于回滚
        Object oldValue = null;
        if (inTransaction) {
            oldValue = tableData.get(primaryKey);
        }
        
        tableData.remove(primaryKey);
        
        // 记录事务操作
        if (inTransaction) {
            transactionOperations.add(new TransactionOperation("DELETE", tableName, primaryKey, oldValue, null));
        }
    }

    @Override
    public <T> T findById(String tableName, Class<T> clazz, String primaryKey) throws DatabaseAccessException {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new DatabaseAccessException("Table name cannot be null or empty");
        }
        if (primaryKey == null || primaryKey.trim().isEmpty()) {
            throw new DatabaseAccessException("Primary key cannot be null or empty");
        }
        
        ConcurrentMap<String, Object> tableData = dataStore.get(tableName);
        if (tableData == null) {
            return null;
        }
        
        Object entity = tableData.get(primaryKey);
        if (entity == null) {
            return null;
        }
        
        try {
            return clazz.cast(entity);
        } catch (ClassCastException e) {
            throw new DatabaseAccessException("Entity type mismatch", e);
        }
    }

    @Override
    public <T> T findOne(String tableName, Class<T> clazz, Map<String, Object> condition) throws DatabaseAccessException {
        List<T> results = query(tableName, clazz, condition);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public <T> List<T> query(String tableName, Class<T> clazz, Map<String, Object> condition) throws DatabaseAccessException {
        return query(tableName, clazz, condition, 0, Integer.MAX_VALUE);
    }

    @Override
    public <T> List<T> query(String tableName, Class<T> clazz, Map<String, Object> condition, 
            int offset, int limit) throws DatabaseAccessException {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new DatabaseAccessException("Table name cannot be null or empty");
        }
        
        ConcurrentMap<String, Object> tableData = dataStore.get(tableName);
        if (tableData == null) {
            return new ArrayList<>();
        }
        
        List<T> results = new ArrayList<>();
        for (Object entity : tableData.values()) {
            if (matchesCondition(entity, condition)) {
                try {
                    results.add(clazz.cast(entity));
                } catch (ClassCastException e) {
                    // 跳过类型不匹配的实体
                    continue;
                }
            }
        }
        
        // 分页
        int start = Math.max(0, offset);
        int end = Math.min(start + limit, results.size());
        if (start < results.size()) {
            return results.subList(start, end);
        }
        
        return new ArrayList<>();
    }

    @Override
    public int count(String tableName, Map<String, Object> condition) throws DatabaseAccessException {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new DatabaseAccessException("Table name cannot be null or empty");
        }
        
        ConcurrentMap<String, Object> tableData = dataStore.get(tableName);
        if (tableData == null) {
            return 0;
        }
        
        if (condition == null || condition.isEmpty()) {
            return tableData.size();
        }

        int count = 0;
        for (Object entity : tableData.values()) {
            if (matchesCondition(entity, condition)) {
                count++;
            }
        }
        
        return count;
    }

    @Override
    public List<Map<String, Object>> executeQuery(String sql, Object... params) throws DatabaseAccessException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new DatabaseAccessException("SQL cannot be null or empty");
        }
        
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            // 如果在事务中，使用事务连接；否则从连接池获取
            if (inTransaction && transactionConnection != null) {
                conn = transactionConnection;
            } else {
                conn = getConnection();
            }
            
            if (conn == null) {
                throw new DatabaseAccessException("Failed to get database connection");
            }
            
            pstmt = conn.prepareStatement(sql);
            
            // 设置参数
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    pstmt.setObject(i + 1, params[i]);
                }
            }
            
            rs = pstmt.executeQuery();
            
            // 转换结果集为Map列表
            List<Map<String, Object>> results = new ArrayList<>();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                results.add(row);
            }
            
            logger.debug("Executed query: {}, returned {} rows", sql, results.size());
            return results;
            
        } catch (SQLException e) {
            logger.error("Failed to execute query: " + sql, e);
            throw new DatabaseAccessException("Failed to execute query: " + e.getMessage(), e);
        } finally {
            // 如果不在事务中，关闭连接；否则保持连接打开
            if (!inTransaction) {
                closeResources(conn, pstmt, rs);
            } else {
                closeResources(null, pstmt, rs);
            }
        }
    }

    @Override
    public int executeUpdate(String sql, Object... params) throws DatabaseAccessException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new DatabaseAccessException("SQL cannot be null or empty");
        }
        
        Connection conn = null;
        PreparedStatement pstmt = null;
        
        try {
            // 如果在事务中，使用事务连接；否则从连接池获取
            if (inTransaction && transactionConnection != null) {
                conn = transactionConnection;
            } else {
                conn = getConnection();
            }
            
            if (conn == null) {
                throw new DatabaseAccessException("Failed to get database connection");
            }
            
            pstmt = conn.prepareStatement(sql);
            
            // 设置参数
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    pstmt.setObject(i + 1, params[i]);
                }
            }
            
            int affectedRows = pstmt.executeUpdate();
            
            logger.debug("Executed update: {}, affected {} rows", sql, affectedRows);
            return affectedRows;
            
        } catch (SQLException e) {
            logger.error("Failed to execute update: " + sql, e);
            throw new DatabaseAccessException("Failed to execute update: " + e.getMessage(), e);
        } finally {
            // 如果不在事务中，关闭连接；否则保持连接打开
            if (!inTransaction) {
                closeResources(conn, pstmt, null);
            } else {
                closeResources(null, pstmt, null);
            }
        }
    }

    @Override
    public List<Map<String, Object>> executeQueryFromConfig(String sqlConfigPath, String sqlName, Object params) throws DatabaseAccessException {
        try {
            // 从配置文件读取SQL
            String sql = readSqlFromConfig(sqlConfigPath, sqlName);
            if (sql == null) {
                throw new DatabaseAccessException("SQL not found in config: " + sqlConfigPath + "#" + sqlName);
            }
            
            // 处理动态 SQL 和参数替换
            sql = processDynamicSql(sql, params);
            sql = replaceParams(sql, params);
            
            // 解析参数并执行查询
            Object[] sqlParams = extractParamsForSql(sql, params);
            return executeQuery(sql, sqlParams);
        } catch (Exception e) {
            logger.error("Failed to execute query from config: " + sqlConfigPath + "#" + sqlName, e);
            throw new DatabaseAccessException("Failed to execute query from config: " + e.getMessage(), e);
        }
    }

    @Override
    public int executeUpdateFromConfig(String sqlConfigPath, String sqlName, Map<String, Object> params) throws DatabaseAccessException {
        try {
            // 从配置文件读取SQL
            String sql = readSqlFromConfig(sqlConfigPath, sqlName);
            if (sql == null) {
                throw new DatabaseAccessException("SQL not found in config: " + sqlConfigPath + "#" + sqlName);
            }
            
            // 处理动态 SQL 和参数替换
            sql = processDynamicSql(sql, params);
            sql = replaceParams(sql, params);
            
            // 解析参数并执行更新
            Object[] sqlParams = extractParamsForSql(sql, params);
            return executeUpdate(sql, sqlParams);
        } catch (Exception e) {
            logger.error("Failed to execute update from config: " + sqlConfigPath + "#" + sqlName, e);
            throw new DatabaseAccessException("Failed to execute update from config: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> void executeInsertFromConfig(String sqlConfigPath, String sqlName, T entity) throws DatabaseAccessException {
        try {
            // 从配置文件读取SQL
            String sql = readSqlFromConfig(sqlConfigPath, sqlName);
            if (sql == null) {
                throw new DatabaseAccessException("SQL not found in config: " + sqlConfigPath + "#" + sqlName);
            }
            
            // 处理动态 SQL
            sql = processDynamicSql(sql, entity);
            sql = replaceParams(sql, entity);
            
            // 从实体对象提取参数
            Object[] sqlParams = extractParamsForSql(sql, entity);
            executeUpdate(sql, sqlParams);
        } catch (Exception e) {
            logger.error("Failed to execute insert from config: " + sqlConfigPath + "#" + sqlName, e);
            throw new DatabaseAccessException("Failed to execute insert from config: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> void executeUpdateFromConfigWithEntity(String sqlConfigPath, String sqlName, T entity) throws DatabaseAccessException {
        try {
            // 从配置文件读取SQL
            String sql = readSqlFromConfig(sqlConfigPath, sqlName);
            if (sql == null) {
                throw new DatabaseAccessException("SQL not found in config: " + sqlConfigPath + "#" + sqlName);
            }
            
            // 处理动态 SQL
            sql = processDynamicSql(sql, entity);
            sql = replaceParams(sql, entity);
            
            // 从实体对象提取参数
            Object[] sqlParams = extractParamsForSql(sql, entity);
            executeUpdate(sql, sqlParams);
        } catch (Exception e) {
            logger.error("Failed to execute update from config: " + sqlConfigPath + "#" + sqlName, e);
            throw new DatabaseAccessException("Failed to execute update from config: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T queryObjectFromConfig(String sqlConfigPath, String sqlName, Class<T> clazz, Object params) throws DatabaseAccessException {
        try {
            List<Map<String, Object>> results = executeQueryFromConfig(sqlConfigPath, sqlName, params);
            if (results == null || results.isEmpty()) {
                return null;
            }
            
            // 将第一个结果转换为对象
            Map<String, Object> firstRow = results.get(0);
            return mapToObject(firstRow, clazz);
        } catch (Exception e) {
            logger.error("Failed to query object from config: " + sqlConfigPath + "#" + sqlName, e);
            throw new DatabaseAccessException("Failed to query object from config: " + e.getMessage(), e);
        }
    }
    
    /**
     * 处理动态 SQL（Properties 格式的条件语句）
     * 支持 #if($condition) ... #end
     */
    private String processDynamicSql(String sql, Object params) {
        if (sql == null || params == null) {
            return sql;
        }
        
        // 处理 #if($condition) ... #end
        Pattern ifPattern = Pattern.compile("#if\\(\\$([\\w.]+)\\)(.*?)#end", Pattern.DOTALL);
        Matcher ifMatcher = ifPattern.matcher(sql);
        StringBuffer result = new StringBuffer();
        
        while (ifMatcher.find()) {
            String condition = ifMatcher.group(1);
            String content = ifMatcher.group(2);
            
            // 检查条件是否满足
            boolean conditionMet = evaluateCondition(params, condition);
            if (conditionMet) {
                // 条件满足，保留内容
                ifMatcher.appendReplacement(result, Matcher.quoteReplacement(content));
            } else {
                // 条件不满足，移除内容
                ifMatcher.appendReplacement(result, "");
            }
        }
        ifMatcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 评估条件
     */
    private boolean evaluateCondition(Object params, String condition) {
        if (params == null) {
            return false;
        }
        
        // 提取属性路径（支持嵌套，如 task.name）
        String[] parts = condition.split("\\.");
        Object value = params;
        
        for (String part : parts) {
            if (value == null) {
                return false;
            }
            
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                value = map.get(part);
            } else {
                // 尝试通过反射获取字段值
                try {
                    String getterName = "get" + capitalize(part);
                    java.lang.reflect.Method getter = value.getClass().getMethod(getterName);
                    value = getter.invoke(value);
                } catch (Exception e) {
                    return false;
                }
            }
        }
        
        // 判断值是否非空且非空字符串
        if (value == null) {
            return false;
        }
        if (value instanceof String) {
            return !((String) value).isEmpty();
        }
        return true;
    }
    
    /**
     * 替换 SQL 中的参数占位符
     * 将 #property# 和 #[property] 替换为 ?
     */
    private String replaceParams(String sql, Object params) {
        if (sql == null) {
            return sql;
        }
        
        // 替换 #property# 为 ?
        sql = sql.replaceAll("#\\w+#", "?");
        
        // 替换 #[property] 为 ?
        sql = sql.replaceAll("#\\[\\w+\\]", "?");
        
        return sql;
    }
    
    /**
     * 为 SQL 提取参数数组（按占位符顺序）
     */
    private Object[] extractParamsForSql(String sql, Object params) {
        if (params == null) {
            return new Object[0];
        }
        
        // 提取所有参数名（按出现顺序）
        List<String> paramNames = new ArrayList<>();
        
        // 匹配 #property# 格式
        Pattern pattern1 = Pattern.compile("#(\\w+)#");
        Matcher matcher1 = pattern1.matcher(sql);
        while (matcher1.find()) {
            String paramName = matcher1.group(1);
            if (!"value".equals(paramName) && !paramNames.contains(paramName)) {
                paramNames.add(paramName);
            }
        }
        
        // 匹配 #[property] 格式
        Pattern pattern2 = Pattern.compile("#\\[(\\w+)\\]");
        Matcher matcher2 = pattern2.matcher(sql);
        while (matcher2.find()) {
            String paramName = matcher2.group(1);
            if (!paramNames.contains(paramName)) {
                paramNames.add(paramName);
            }
        }
        
        // 如果找到参数名，按名称提取；否则按位置提取
        if (!paramNames.isEmpty()) {
            List<Object> values = new ArrayList<>();
            for (String paramName : paramNames) {
                Object value = extractParamValue(params, paramName);
                values.add(value);
            }
            return values.toArray();
        } else {
            // 对于 ? 占位符，如果 params 是数组或列表，直接使用
            if (params instanceof Object[]) {
                return (Object[]) params;
            } else if (params instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) params;
                return list.toArray();
            } else if (params instanceof Map) {
                // Map 类型但 SQL 无占位符 → 返回空数组
                return new Object[0];
            } else {
                // 单个参数
                return new Object[] { params };
            }
        }
    }

    @Override
    public <T> List<T> queryListFromConfig(String sqlConfigPath, String sqlName, Class<T> clazz, Object params) throws DatabaseAccessException {
        try {
            List<Map<String, Object>> results = executeQueryFromConfig(sqlConfigPath, sqlName, params);
            if (results == null || results.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 将所有结果转换为对象列表
            List<T> objects = new ArrayList<>();
            for (Map<String, Object> row : results) {
                T obj = mapToObject(row, clazz);
                if (obj != null) {
                    objects.add(obj);
                }
            }
            return objects;
        } catch (Exception e) {
            logger.error("Failed to query list from config: " + sqlConfigPath + "#" + sqlName, e);
            throw new DatabaseAccessException("Failed to query list from config: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从配置文件读取SQL
     * 支持 iBATIS 格式（sqlMap）和 properties 格式
     */
    private String readSqlFromConfig(String sqlConfigPath, String sqlName) throws Exception {
        // 检查缓存
        Map<String, String> sqlMap = sqlConfigCache.get(sqlConfigPath);
        if (sqlMap != null && sqlMap.containsKey(sqlName)) {
            return sqlMap.get(sqlName);
        }
        
        // 从 classpath 加载配置文件
        InputStream inputStream = null;
        try {
            inputStream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(sqlConfigPath);
            
            if (inputStream == null) {
                throw new DatabaseAccessException("SQL config file not found: " + sqlConfigPath);
            }
            
            // 判断文件格式
            if (sqlConfigPath.endsWith(".xml")) {
                // 解析 iBATIS 格式
                sqlMap = parseIbatisSqlMap(inputStream, sqlConfigPath);
            } else {
                // 解析 Properties 格式
                sqlMap = parsePropertiesSqlMap(inputStream, sqlConfigPath);
            }
            
            // 缓存解析结果
            sqlConfigCache.put(sqlConfigPath, sqlMap);
            
            String sql = sqlMap.get(sqlName);
            if (sql == null) {
                throw new DatabaseAccessException("SQL not found in config: " + sqlConfigPath + "#" + sqlName);
            }
            
            return sql;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    logger.warn("Failed to close input stream", e);
                }
            }
        }
    }
    
    /**
     * 解析 iBATIS 格式的 SQL Map
     */
    private Map<String, String> parseIbatisSqlMap(InputStream inputStream, String sqlConfigPath) throws Exception {
        Map<String, String> sqlMap = new HashMap<>();
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);
            
            Element root = doc.getDocumentElement();
            String namespace = root.getAttribute("namespace");
            
            // 解析所有 SQL 语句
            NodeList nodeList = root.getChildNodes();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String tagName = element.getTagName();
                    String id = element.getAttribute("id");
                    
                    if (id != null && !id.isEmpty()) {
                        // 构建完整的 SQL 名称（namespace.id 或直接 id）
                        String sqlName = namespace != null && !namespace.isEmpty() 
                            ? namespace + "." + id 
                            : id;
                        
                        // 提取 SQL 内容
                        String sql = extractSqlFromElement(element);
                        sqlMap.put(sqlName, sql);
                        sqlMap.put(id, sql); // 也支持不带 namespace 的查询
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse iBATIS SQL map: " + sqlConfigPath, e);
            throw new DatabaseAccessException("Failed to parse iBATIS SQL map: " + e.getMessage(), e);
        }
        
        return sqlMap;
    }
    
    /**
     * 从 iBATIS 元素中提取 SQL
     */
    private String extractSqlFromElement(Element element) {
        StringBuilder sql = new StringBuilder();
        NodeList children = element.getChildNodes();
        
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                sql.append(node.getNodeValue());
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) node;
                String tagName = childElement.getTagName();
                
                // 处理动态 SQL 标签（简化实现）
                if ("dynamic".equals(tagName)) {
                    String prepend = childElement.getAttribute("prepend");
                    if (prepend != null && !prepend.isEmpty()) {
                        sql.append(" ").append(prepend).append(" ");
                    }
                    sql.append(extractSqlFromElement(childElement));
                } else if ("isNotEmpty".equals(tagName) || "isNotNull".equals(tagName)) {
                    String prepend = childElement.getAttribute("prepend");
                    if (prepend != null && !prepend.isEmpty()) {
                        sql.append(" ").append(prepend).append(" ");
                    }
                    sql.append(extractSqlFromElement(childElement));
                } else {
                    sql.append(extractSqlFromElement(childElement));
                }
            }
        }
        
        return sql.toString().trim();
    }
    
    /**
     * 解析 Properties 格式的 SQL Map
     */
    private Map<String, String> parsePropertiesSqlMap(InputStream inputStream, String sqlConfigPath) throws Exception {
        Map<String, String> sqlMap = new HashMap<>();
        
        try {
            // 读取 XML properties 格式
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);
            
            Element root = doc.getDocumentElement();
            NodeList propertyList = root.getElementsByTagName("property");
            
            for (int i = 0; i < propertyList.getLength(); i++) {
                Element property = (Element) propertyList.item(i);
                String name = property.getAttribute("name");
                
                if (name != null && !name.isEmpty()) {
                    // 提取 CDATA 内容
                    NodeList cdataNodes = property.getChildNodes();
                    for (int j = 0; j < cdataNodes.getLength(); j++) {
                        Node node = cdataNodes.item(j);
                        if (node.getNodeType() == Node.CDATA_SECTION_NODE) {
                            String sql = node.getNodeValue();
                            sqlMap.put(name, sql.trim());
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse Properties SQL map: " + sqlConfigPath, e);
            throw new DatabaseAccessException("Failed to parse Properties SQL map: " + e.getMessage(), e);
        }
        
        return sqlMap;
    }
    
    /**
     * 从参数对象提取SQL参数数组
     */
    private Object[] extractParams(Object params) {
        if (params == null) {
            return new Object[0];
        }
        
        if (params instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramMap = (Map<String, Object>) params;
            return paramMap.values().toArray();
        }
        
        if (params instanceof Object[]) {
            return (Object[]) params;
        }
        
        // 单个参数
        return new Object[] { params };
    }
    
    /**
     * 从实体对象提取SQL参数
     * 使用反射从实体对象中提取字段值
     */
    private <T> Object[] extractParamsFromEntity(T entity, String sql) {
        if (entity == null) {
            return new Object[0];
        }
        
        // 提取 SQL 中的参数占位符
        List<String> paramNames = extractParamNames(sql);
        List<Object> paramValues = new ArrayList<>();
        
        for (String paramName : paramNames) {
            Object value = extractParamValue(entity, paramName);
            paramValues.add(value);
        }
        
        return paramValues.toArray();
    }
    
    /**
     * 从 SQL 中提取参数名
     * 支持格式：#property#、#[property]、#value#、?
     */
    private List<String> extractParamNames(String sql) {
        List<String> paramNames = new ArrayList<>();
        
        // 匹配 #property# 格式（iBATIS）
        Pattern pattern1 = Pattern.compile("#(\\w+)#");
        Matcher matcher1 = pattern1.matcher(sql);
        while (matcher1.find()) {
            String paramName = matcher1.group(1);
            if (!"value".equals(paramName)) {
                paramNames.add(paramName);
            }
        }
        
        // 匹配 #[property] 格式（Properties）
        Pattern pattern2 = Pattern.compile("#\\[(\\w+)\\]");
        Matcher matcher2 = pattern2.matcher(sql);
        while (matcher2.find()) {
            paramNames.add(matcher2.group(1));
        }
        
        // 如果找到参数名，返回；否则按 ? 占位符数量返回
        if (paramNames.isEmpty()) {
            // 统计 ? 的数量
            int count = 0;
            for (int i = 0; i < sql.length(); i++) {
                if (sql.charAt(i) == '?') {
                    count++;
                }
            }
            // 对于 ? 占位符，参数顺序与实体字段顺序一致
            // 这里简化处理，返回空列表，由调用方处理
        }
        
        return paramNames;
    }
    
    /**
     * 从实体对象中提取参数值
     */
    private Object extractParamValue(Object entity, String paramName) {
        try {
            // 尝试通过 getter 方法获取
            String getterName = "get" + capitalize(paramName);
            java.lang.reflect.Method getter = entity.getClass().getMethod(getterName);
            return getter.invoke(entity);
        } catch (NoSuchMethodException e) {
            // 尝试直接访问字段
            try {
                java.lang.reflect.Field field = entity.getClass().getDeclaredField(paramName);
                field.setAccessible(true);
                return field.get(entity);
            } catch (Exception ex) {
                // 尝试下划线转驼峰
                String camelName = underscoreToCamel(paramName);
                try {
                    String getterName = "get" + capitalize(camelName);
                    java.lang.reflect.Method getter = entity.getClass().getMethod(getterName);
                    return getter.invoke(entity);
                } catch (Exception ex2) {
                    logger.warn("Failed to extract parameter value: " + paramName, ex2);
                    return null;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract parameter value: " + paramName, e);
            return null;
        }
    }
    
    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * 下划线转驼峰
     */
    private String underscoreToCamel(String str) {
        if (str == null || !str.contains("_")) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        boolean nextUpperCase = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '_') {
                nextUpperCase = true;
            } else {
                if (nextUpperCase) {
                    result.append(Character.toUpperCase(c));
                    nextUpperCase = false;
                } else {
                    result.append(c);
                }
            }
        }
        return result.toString();
    }
    
    /**
     * 将Map转换为对象
     * 使用反射将Map的键值对映射到对象的字段
     * 支持字段名映射（下划线转驼峰）
     */
    private <T> T mapToObject(Map<String, Object> map, Class<T> clazz) {
        try {
            @SuppressWarnings("deprecation")
            T obj = clazz.newInstance();
            java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
            
            for (java.lang.reflect.Field field : fields) {
                String fieldName = field.getName();
                Object value = null;
                
                // 1. 直接匹配字段名
                if (map.containsKey(fieldName)) {
                    value = map.get(fieldName);
                } else {
                    // 2. 尝试下划线转驼峰匹配（如 task_id -> taskId）
                    String underscoreName = camelToUnderscore(fieldName);
                    if (map.containsKey(underscoreName)) {
                        value = map.get(underscoreName);
                    } else {
                        // 3. 尝试大写匹配（如 TASK_ID）
                        String upperName = underscoreName.toUpperCase();
                        if (map.containsKey(upperName)) {
                            value = map.get(upperName);
                        }
                    }
                }
                
                if (value != null) {
                    field.setAccessible(true);
                    // 类型转换
                    Object convertedValue = convertValue(value, field.getType());
                    if (convertedValue != null) {
                        field.set(obj, convertedValue);
                    }
                }
            }
            
            return obj;
        } catch (Exception e) {
            logger.error("Failed to convert Map to object: " + clazz.getName(), e);
            return null;
        }
    }
    
    /**
     * 驼峰转下划线
     */
    private String camelToUnderscore(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
    
    /**
     * 类型转换
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        
        // 如果类型匹配，直接返回
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        
        // 类型转换
        try {
            if (targetType == String.class) {
                return value.toString();
            } else if (targetType == Integer.class || targetType == int.class) {
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                return Integer.parseInt(value.toString());
            } else if (targetType == Long.class || targetType == long.class) {
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
                return Long.parseLong(value.toString());
            } else if (targetType == Double.class || targetType == double.class) {
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return Double.parseDouble(value.toString());
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                if (value instanceof Boolean) {
                    return value;
                }
                return Boolean.parseBoolean(value.toString());
            } else if (targetType == java.util.Date.class) {
                if (value instanceof java.util.Date) {
                    return value;
                } else if (value instanceof java.sql.Timestamp) {
                    return new java.util.Date(((java.sql.Timestamp) value).getTime());
                } else if (value instanceof java.sql.Date) {
                    return new java.util.Date(((java.sql.Date) value).getTime());
                } else if (value instanceof Long) {
                    return new java.util.Date((Long) value);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to convert value: " + value + " to " + targetType.getName(), e);
        }
        
        return value;
    }

    @Override
    public void beginTransaction() throws DatabaseAccessException {
        if (inTransaction) {
            throw new DatabaseAccessException("Transaction already in progress");
        }
        
        try {
            transactionConnection = getConnection();
            if (transactionConnection == null) {
                throw new DatabaseAccessException("Failed to get database connection for transaction");
            }
            transactionConnection.setAutoCommit(false);
            inTransaction = true;
            transactionOperations.clear();
            logger.debug("Transaction started");
        } catch (SQLException e) {
            logger.error("Failed to begin transaction", e);
            if (transactionConnection != null) {
                try {
                    transactionConnection.close();
                } catch (SQLException ex) {
                    // ignore
                }
                transactionConnection = null;
            }
            throw new DatabaseAccessException("Failed to begin transaction: " + e.getMessage(), e);
        }
    }

    @Override
    public void commit() throws DatabaseAccessException {
        if (!inTransaction) {
            throw new DatabaseAccessException("No transaction in progress");
        }
        
        try {
            if (transactionConnection != null) {
                transactionConnection.commit();
                transactionConnection.setAutoCommit(true);
                transactionConnection.close();
            }
            inTransaction = false;
            transactionConnection = null;
            transactionOperations.clear();
            logger.debug("Transaction committed");
        } catch (SQLException e) {
            logger.error("Failed to commit transaction", e);
            try {
                if (transactionConnection != null) {
                    transactionConnection.rollback();
                }
            } catch (SQLException ex) {
                logger.error("Failed to rollback transaction", ex);
            }
            throw new DatabaseAccessException("Failed to commit transaction: " + e.getMessage(), e);
        }
    }

    @Override
    public void rollback() throws DatabaseAccessException {
        if (!inTransaction) {
            throw new DatabaseAccessException("No transaction in progress");
        }
        
        try {
            if (transactionConnection != null) {
                transactionConnection.rollback();
                transactionConnection.setAutoCommit(true);
                transactionConnection.close();
            }
            
            // 回滚内存操作（如果存在）
            for (int i = transactionOperations.size() - 1; i >= 0; i--) {
                TransactionOperation op = transactionOperations.get(i);
                rollbackOperation(op);
            }
            
            inTransaction = false;
            transactionConnection = null;
            transactionOperations.clear();
            logger.debug("Transaction rolled back");
        } catch (SQLException e) {
            logger.error("Failed to rollback transaction", e);
            inTransaction = false;
            transactionConnection = null;
            transactionOperations.clear();
            throw new DatabaseAccessException("Failed to rollback transaction: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取数据库连接
     * 使用 Spring DataSource 获取连接
     */
    private Connection getConnection() throws DatabaseAccessException {
        try {
            if (dataSource == null) {
                throw new DatabaseAccessException("DataSource is not configured. Please ensure DataSource is injected via Spring.");
            }
            
            // 如果已经在事务中，返回事务连接
            if (inTransaction && transactionConnection != null) {
                return transactionConnection;
            }
            
            // 从Spring DataSource获取连接
            Connection conn = dataSource.getConnection();
            
            if (conn == null) {
                throw new DatabaseAccessException("Failed to get connection from DataSource");
            }
            return conn;
        } catch (SQLException e) {
            logger.error("Failed to get database connection from DataSource", e);
            throw new DatabaseAccessException("Failed to get database connection: " + e.getMessage(), e);
        }
    }
    
    /**
     * 关闭数据库资源
     */
    private void closeResources(Connection conn, PreparedStatement pstmt, ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                logger.warn("Failed to close ResultSet", e);
            }
        }
        if (pstmt != null) {
            try {
                pstmt.close();
            } catch (SQLException e) {
                logger.warn("Failed to close PreparedStatement", e);
            }
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.warn("Failed to close Connection", e);
            }
        }
    }
    
    /**
     * 提取主键值（简化实现）
     */
    private String extractPrimaryKey(Object entity) {
        try {
            // 尝试调用getId()方法
            java.lang.reflect.Method getIdMethod = entity.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(entity);
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 生成主键
     */
    private String generatePrimaryKey() {
        return java.util.UUID.randomUUID().toString();
    }
    
    /**
     * 检查实体是否匹配条件
     */
    private boolean matchesCondition(Object entity, Map<String, Object> condition) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        
        for (Map.Entry<String, Object> entry : condition.entrySet()) {
            try {
                String fieldName = entry.getKey();
                Object expectedValue = entry.getValue();
                
                // 尝试获取字段值
                String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                java.lang.reflect.Method getter = entity.getClass().getMethod(getterName);
                Object actualValue = getter.invoke(entity);
                
                if (expectedValue == null) {
                    if (actualValue != null) {
                        return false;
                    }
                } else if (!expectedValue.equals(actualValue)) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 回滚操作
     */
    private void rollbackOperation(TransactionOperation op) {
        ConcurrentMap<String, Object> tableData = dataStore.get(op.getTableName());
        if (tableData == null) {
            return;
        }
        
        switch (op.getOperation()) {
            case "INSERT":
                tableData.remove(op.getPrimaryKey());
                break;
            case "UPDATE":
            case "DELETE":
                if (op.getOldValue() != null) {
                    tableData.put(op.getPrimaryKey(), op.getOldValue());
                }
                break;
        }
    }
    
    /**
     * 事务操作记录
     */
    private static class TransactionOperation {
        private final String operation;
        private final String tableName;
        private final String primaryKey;
        private final Object oldValue;
        private final Object newValue;
        
        public TransactionOperation(String operation, String tableName, String primaryKey, 
                Object oldValue, Object newValue) {
            this.operation = operation;
            this.tableName = tableName;
            this.primaryKey = primaryKey;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
        
        public String getOperation() { return operation; }
        public String getTableName() { return tableName; }
        public String getPrimaryKey() { return primaryKey; }
        public Object getOldValue() { return oldValue; }
        public Object getNewValue() { return newValue; }
    }
}

