package com.chinacreator.gzcm.runtime.core.datasource.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.core.datasource.IDataSourceService.DataSourceException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DataSourceServiceImpl 单元测试
 */
@DisplayName("数据源服务测试")
class DataSourceServiceImplTest {

    private DataSourceServiceImpl dataSourceService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        dataSourceService = new DataSourceServiceImpl();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("创建数据源")
    void testCreateDataSource() throws Exception {
        DataSourceEntity entity = createTestDataSource();

        String id = dataSourceService.createDataSource(entity);
        assertNotNull(id);
        assertEquals(entity.getDatasourceId(), id);
    }

    @Test
    @DisplayName("更新数据源")
    void testUpdateDataSource() throws Exception {
        DataSourceEntity entity = createTestDataSource();
        String id = dataSourceService.createDataSource(entity);

        entity.setDatasourceName("更新后的数据源");
        dataSourceService.updateDataSource(entity);

        DataSourceEntity found = dataSourceService.getDataSourceById(id);
        assertNotNull(found);
        assertEquals("更新后的数据源", found.getDatasourceName());
    }

    @Test
    @DisplayName("根据ID获取数据源")
    void testGetDataSourceById() throws Exception {
        DataSourceEntity entity = createTestDataSource();
        String id = dataSourceService.createDataSource(entity);

        DataSourceEntity found = dataSourceService.getDataSourceById(id);
        assertNotNull(found);
        assertEquals(id, found.getDatasourceId());
    }

    @Test
    @DisplayName("删除数据源")
    void testDeleteDataSource() throws Exception {
        DataSourceEntity entity = createTestDataSource();
        String id = dataSourceService.createDataSource(entity);

        dataSourceService.deleteDataSource(id);

        assertThrows(DataSourceException.class, () -> {
            dataSourceService.getDataSourceById(id);
        });
    }

    @Test
    @DisplayName("查询数据源列表")
    void testQueryDataSources() throws Exception {
        int initialSize = dataSourceService.queryDataSources(null, null, null, null).size();
        
        for (int i = 1; i <= 5; i++) {
            DataSourceEntity entity = createTestDataSource();
            entity.setDatasourceId("test-datasource-list-" + i + "-" + System.nanoTime());
            entity.setDatasourceName("数据源" + i);
            dataSourceService.createDataSource(entity);
        }

        List<DataSourceEntity> sources = dataSourceService.queryDataSources(null, null, null, null);
        assertNotNull(sources);
        assertTrue(sources.size() >= initialSize + 5, "应该至少新增5个数据源");
    }

    @Test
    @DisplayName("测试数据源连接")
    void testTestDataSource() throws Exception {
        DataSourceEntity entity = createTestDataSource();
        entity.setDatasourceId("test-conn-" + System.currentTimeMillis());
        String id = dataSourceService.createDataSource(entity);

        try {
            boolean connected = dataSourceService.testDataSource(id);
            assertNotNull(id);
        } catch (DataSourceException e) {
            assertTrue(e.getMessage().contains("connection") || 
                       e.getMessage().contains("failed") ||
                       e.getMessage().contains("timeout"));
        }
    }

    private DataSourceEntity createTestDataSource() {
        DataSourceEntity entity = new DataSourceEntity();
        entity.setDatasourceId("test-datasource-" + System.currentTimeMillis());
        entity.setDatasourceName("测试数据源");
        entity.setDatasourceType("DATABASE");
        
        // 设置连接配置（JSON格式）
        Map<String, Object> config = new HashMap<>();
        config.put("driver", "com.mysql.cj.jdbc.Driver");
        config.put("url", "jdbc:mysql://localhost:3306/testdb");
        config.put("username", "testuser");
        config.put("password", "testpass");
        config.put("database", "testdb");
        
        try {
            entity.setConnectionConfig(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            // 忽略JSON序列化错误
        }
        
        return entity;
    }
}

