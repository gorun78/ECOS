package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.service.DataSourceRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Wave-5.4 T-19 seed: DataSourceController 连接测试与更新 404 路径. */
@ExtendWith(MockitoExtension.class)
class DataSourceControllerWave54Test {

    @Mock
    private DataSourceRegistryService service;

    private DataSourceController controller;

    @BeforeEach
    void setUp() {
        controller = new DataSourceController(service);
    }

    @Test
    void testConnection_success() {
        when(service.testConnection("ds-1")).thenReturn(true);

        ApiResponse<Map<String, Object>> resp = controller.testConnection("ds-1");

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertEquals(Boolean.TRUE, resp.getData().get("success"));
        assertEquals("ds-1", resp.getData().get("datasourceId"));
    }

    @Test
    void update_missing_shouldReturn404() {
        DataSourceDTO dto = new DataSourceDTO();
        when(service.update("gone", dto)).thenReturn(null);

        ApiResponse<DataSourceEntity> resp = controller.update("gone", dto);

        assertEquals(404, resp.getCode());
    }
}
