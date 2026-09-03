package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.service.DataSourceRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * DataSourceControllerTest — 数据源 CRUD 三接口。
 *
 * <p>Wave-5.1 T-06：mock DataSourceRegistryService，
 * 验证添加/查/删均返回 200。
 */
@ExtendWith(MockitoExtension.class)
class DataSourceControllerTest {

    @Mock
    private DataSourceRegistryService service;

    private DataSourceController controller;

    @BeforeEach
    void setUp() {
        this.controller = new DataSourceController(service);
    }

    @Test
    @DisplayName("POST / — 添加数据源 200")
    void registerSuccess() {
        DataSourceDTO dto = new DataSourceDTO();
        DataSourceEntity entity = new DataSourceEntity();
        entity.setDatasourceId("ds-1");
        entity.setDatasourceName("pg-main");
        when(service.register(dto)).thenReturn(entity);

        ApiResponse<DataSourceEntity> resp = controller.register(dto);
        assertTrue(resp.isSuccess(), "register 应 code=0");
        assertNotNull(resp.getData());
        assertEquals("ds-1", resp.getData().getDatasourceId());
    }

    @Test
    @DisplayName("GET / — 查列表 200")
    void listSuccess() {
        DataSourceEntity e1 = new DataSourceEntity();
        e1.setDatasourceId("ds-1");
        when(service.listAll()).thenReturn(List.of(e1));

        ApiResponse<List<DataSourceEntity>> resp = controller.list();
        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().size());
    }

    @Test
    @DisplayName("DELETE /{id} — 删除 200")
    void removeSuccess() {
        ApiResponse<Void> resp = controller.remove("ds-1");
        assertTrue(resp.isSuccess());
    }

    @Test
    @DisplayName("GET /{id} — id 不存在应 404")
    void getByIdNotFound() {
        when(service.getById("nope")).thenReturn(null);
        ApiResponse<DataSourceEntity> resp = controller.getById("nope");
        assertEquals(ApiResponse.CODE_NOT_FOUND, resp.getCode());
    }
}
