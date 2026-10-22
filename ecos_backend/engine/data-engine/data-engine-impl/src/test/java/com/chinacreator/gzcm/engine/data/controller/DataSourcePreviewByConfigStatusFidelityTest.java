package com.chinacreator.gzcm.engine.data.controller;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.service.DataSourceRegistryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 承流端点<b>原始配置预览</b>的 HTTP 状态行保真行为门（#142/B4-3e-46，收口差距 <b>D-B4-76</b> 的端点腿）。
 *
 * <p>与同批的服务侧门 {@code DataSourcePreviewByConfigCarriedThrowTest} 配对：那里 {@code DataSourceServiceImpl}
 * 是真身、端点不进门；这里 {@code DataSourceRegistryService} 是 mock、只验「端点把携带态渲染成
 * <b>真状态行</b>并与信封 {@code code} 同号」。任一门单独都不足以宣称通道收口
 *（承 #142/B4-3e-44 的两门同时在链口径）。</p>
 *
 * <p><b>本批为什么不能沿用改前的裸回</b>：改前该通道根本没有承流端点（前端打的是幻影路径），
 * 而 {@code DataSourceController} 其余 6 枚方法仍是裸回（属 D-B4-59 族另账）。新方法若也裸回
 * {@code ApiResponse}，失败腿会再次落进「HTTP 200 ＋ 信封失败码」这个刚被收口的折叠形态。
 * 故本门逐腿读 {@code getStatusCode()} 的<b>数值</b>，并钉住 400 腿有意不带机读码
 *（借用他码会重犯 #142/B4-3e-44 登记的「503 段被非降级腿借用」同型错误）。</p>
 */
class DataSourcePreviewByConfigStatusFidelityTest {

    private final DataSourceRegistryService registry = mock(DataSourceRegistryService.class);
    private final DataSourceController controller = new DataSourceController(registry);

    private static DataSourceDTO raw(String type) {
        DataSourceDTO dto = new DataSourceDTO();
        dto.setDatasourceType(type);
        dto.setConnectionConfig("{\"jdbcUrl\":\"jdbc:mysql://127.0.0.1:3306/db\"}");
        return dto;
    }

    private static <T> ApiResponse<T> body(ResponseEntity<ApiResponse<T>> resp) {
        assertNotNull(resp.getBody(), "ResponseEntity 的 body 不得为 null（状态行有了、信封丢了）");
        return resp.getBody();
    }

    @Test
    @DisplayName("成功腿：状态行 200 ＋ 信封 code=0，键面原样透出")
    void successLegCarries200() {
        when(registry.previewSchemaRaw(any())).thenReturn(Map.of(
                "type", "MYSQL", "tableNames", List.of("db.orders")));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.previewSchema(raw("MYSQL"));

        assertEquals(HttpStatus.OK.value(), resp.getStatusCode().value());
        assertEquals(ApiResponse.CODE_SUCCESS, body(resp).getCode());
        assertEquals(List.of("db.orders"), body(resp).getData().get("tableNames"),
                "承流腿必须把 tableNames 透出——改前前端读的就是这个键，读空即缺陷本体");
    }

    @Test
    @DisplayName("入参不合法腿：状态行 400 与信封同号，机读码有意留空")
    void badRequestLegCarries400WithoutBorrowingAnotherCode() {
        when(registry.previewSchemaRaw(any()))
                .thenThrow(new IllegalArgumentException("连接配置不能为空"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.previewSchema(raw("MYSQL"));

        assertEquals(HttpStatus.BAD_REQUEST.value(), resp.getStatusCode().value(),
                "入参校验失败必须是 400 状态行，不留 200");
        assertEquals(HttpStatus.BAD_REQUEST.value(), body(resp).getCode(), "§3.2.7 同号性");
        assertEquals("连接配置不能为空", body(resp).getMessage());
        assertNull(body(resp).getErrorCode(),
                "本域 400 段无对应号义，禁借 ECOS-DATA-051／032 等他码");
    }

    @Test
    @DisplayName("通道不可用腿：状态行 503＋机读码 ECOS-DATA-051，与信封同号（禁折成 403 或 200）")
    void unavailableLegCarries503WithMachineCode() {
        when(registry.previewSchemaRaw(any())).thenThrow(new DataBridgeException(503, 503,
                DataDomainErrorCode.ECOS_DATA_051 + " — 数据源结构预览通道不可用: Communications link failure"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.previewSchema(raw("MYSQL"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), resp.getStatusCode().value());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), body(resp).getCode());
        assertEquals(DataDomainErrorCode.ECOS_DATA_051, body(resp).getErrorCode());
    }

    @Test
    @DisplayName("携带 404 腿：状态行照携带值落 404，不被兜底吞成 500")
    void carriedNotFoundLegStays404() {
        when(registry.previewSchemaRaw(any())).thenThrow(new DataBridgeException(404, 404,
                DataDomainErrorCode.ECOS_DATA_032 + " — 数据源不存在"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.previewSchema(raw("MYSQL"));

        assertEquals(HttpStatus.NOT_FOUND.value(), resp.getStatusCode().value());
        assertEquals(DataDomainErrorCode.ECOS_DATA_032, body(resp).getErrorCode());
    }

    @Test
    @DisplayName("意外失败腿：500＋ECOS-DATA-031（数据层故障段），不留 200 也不冒充 404")
    void unexpectedFailureLegCarries500() {
        when(registry.previewSchemaRaw(any())).thenThrow(new NullPointerException("boom"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.previewSchema(raw("MYSQL"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), resp.getStatusCode().value());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), body(resp).getCode());
        assertEquals(DataDomainErrorCode.ECOS_DATA_031, body(resp).getErrorCode());
    }

    @Test
    @DisplayName("签名只增不改：previewSchema 收 DataSourceDTO 并返回承载状态的类型")
    void endpointSignatureIsAdditiveAndCarrying() throws NoSuchMethodException {
        Method method = DataSourceController.class
                .getMethod("previewSchema", DataSourceDTO.class);
        assertEquals(ResponseEntity.class, method.getReturnType(),
                "新承流腿必须返回带状态行的 ResponseEntity（裸回 ApiResponse 即重犯折叠形态）");
    }
}
