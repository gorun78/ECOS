package com.chinacreator.gzcm.engine.data.controller;

import java.lang.reflect.Method;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.service.DataSourceRegistryService;
import com.chinacreator.gzcm.engine.data.service.DataSourceServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * 数据源兼容别名端点的<b>HTTP 状态行保真行为门</b>（#142/B4-3e-44，收口差距 <b>D-B4-74／D-B4-75</b>）。
 *
 * <p><b>为什么静态门不够</b>：本批同时新增的
 * {@code runtime/common-api} 棘轮门 {@code SuccessStatusFailureEnvelopeRatchetTest} 只数「形态」——
 * 把 {@code failure(ApiResponse.error(404, …))} 里的 helper 换成常量 200 时，形态面看不见。
 * 本门逐腿读 {@code getStatusCode()} 的<b>数值</b>，与静态门配对（承 #142/B4-3e-32／3e-37 的
 * 「两门同时在链」关账口径）。</p>
 *
 * <p><b>§4.7.2／§3.2.7 的分态判据</b>：记录不存在（404）、入参缺失（400）、底座不可用（503）三类
 * 失败此前一律落 HTTP 200、只活在信封 {@code code} 里——按状态行分派的中间层（网关、告警、
 * Playwright 用例）把三类都读成「成功但结果为空」。本门要求四者与信封 {@code code} <b>同号</b>。</p>
 */
class DataSourcePreviewStatusFidelityTest {

    private final DataSourceRegistryService registry = mock(DataSourceRegistryService.class);
    private final DataSourceServiceImpl impl = mock(DataSourceServiceImpl.class);
    private final PMO45DataSourceController controller = new PMO45DataSourceController(registry, impl);

    private static <T> ApiResponse<T> body(ResponseEntity<ApiResponse<T>> resp) {
        assertNotNull(resp.getBody(), "ResponseEntity 的 body 不得为 null（状态行有了、信封丢了）");
        return resp.getBody();
    }

    @Test
    @DisplayName("预览成功腿：状态行 200 ＋ 信封 code=0（禁把成功也折进失败推导）")
    void successLegCarries200() {
        when(impl.previewSchema("ds-1")).thenReturn(Map.<String, Object>of("type", "MYSQL"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.previewSchema(Map.of("datasourceId", "ds-1"));

        assertEquals(HttpStatus.OK.value(), resp.getStatusCode().value());
        assertEquals(ApiResponse.CODE_SUCCESS, body(resp).getCode());
    }

    @Test
    @DisplayName("记录不存在腿：状态行 404 且与信封同号，机读码 ECOS-DATA-032（改前恒 200）")
    void notFoundLegCarries404OnTheStatusLine() {
        when(impl.previewSchema("missing")).thenThrow(new DataBridgeException(404, 404,
                DataDomainErrorCode.ECOS_DATA_032 + " — 数据源不存在: missing"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.previewSchema(Map.of("datasourceId", "missing"));

        assertEquals(HttpStatus.NOT_FOUND.value(), resp.getStatusCode().value(),
                "404 仍落 200＝「查无此源」与「查到但没结构」在传输层同形");
        assertEquals(ApiResponse.CODE_NOT_FOUND, body(resp).getCode(), "状态行与信封必须同号");
        assertEquals(DataDomainErrorCode.ECOS_DATA_032, body(resp).getErrorCode(),
                "机读码取本域码表 message 前缀，不留数字码孤面");
    }

    @Test
    @DisplayName("底座不可用腿：状态行 503 且不被折成 404／200（§4.7.2 不可用 ≠ 无权限）")
    void unavailableLegCarries503NotAPlainEmptyResult() {
        when(impl.previewSchema(anyString())).thenThrow(new DataBridgeException(503, 503,
                DataDomainErrorCode.ECOS_DATA_051 + " — 数据源结构预览通道不可用: connector down"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.previewSchema(Map.of("datasourceId", "ds-1"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), resp.getStatusCode().value());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), body(resp).getCode());
        assertEquals(DataDomainErrorCode.ECOS_DATA_051, body(resp).getErrorCode());
    }

    @Test
    @DisplayName("入参缺失腿：状态行 400（改前写 200 ＋ code 400，是本缺陷族主体）")
    void blankIdLegCarries400() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.previewSchema(Map.of("datasourceId", "  "));

        assertEquals(HttpStatus.BAD_REQUEST.value(), resp.getStatusCode().value());
        assertEquals(ApiResponse.CODE_BAD_REQUEST, body(resp).getCode());
    }

    @Test
    @DisplayName("非携带态意外失败：500 ＋ ECOS-DATA-031，不得回退成 404 或 200")
    void unexpectedFailureIs500NotAFakeNotFound() {
        when(impl.previewSchema(anyString())).thenThrow(new IllegalStateException("NPE-ish"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.previewSchema(Map.of("datasourceId", "ds-1"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), resp.getStatusCode().value());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), body(resp).getCode());
        assertEquals(DataDomainErrorCode.ECOS_DATA_031, body(resp).getErrorCode());
    }

    @Test
    @DisplayName("非法携带值（0/200/302/600/700）经单源回退 500——端点内不写第二个状态判据")
    void illegalCarriedStatusFallsBackTo500() {
        for (int illegal : new int[] {0, 200, 302, 600, 700}) {
            // 打桩必须用 doThrow 形态：when(impl.previewSchema(…)) 会<b>真的调用</b>一次 mock，
            // 第二轮起上一次装好的 throw 会从这里逃出到测试方法（本门首次实跑即以此暴露）。
            reset(impl);
            doThrow(new DataBridgeException(illegal, illegal,
                    DataDomainErrorCode.ECOS_DATA_031 + " — 非法携带值 " + illegal))
                    .when(impl).previewSchema("ds-illegal");

            ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                    controller.previewSchema(Map.of("datasourceId", "ds-illegal"));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), resp.getStatusCode().value(),
                    "携带值 " + illegal + " 越界必须回退 500，不得把成功码当状态行");
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), body(resp).getCode(),
                    "状态值越界时信封 code 必须与回退后的状态行同号");
        }
    }

    @Test
    @DisplayName("查询单条与更新的存在性腿：404 上状态行且带 ECOS-DATA-032（同一折叠面的另两枚腿）")
    void getByIdAndUpdateCarry404OnTheStatusLine() {
        when(registry.getById("gone")).thenReturn(null);
        when(registry.update(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(null);

        ResponseEntity<ApiResponse<DataSourceEntity>> got = controller.getById("gone");
        assertEquals(HttpStatus.NOT_FOUND.value(), got.getStatusCode().value());
        assertEquals(DataDomainErrorCode.ECOS_DATA_032, body(got).getErrorCode());

        ResponseEntity<ApiResponse<DataSourceEntity>> put =
                controller.update("gone", new com.chinacreator.gzcm.common.data.dto.DataSourceDTO());
        assertEquals(HttpStatus.NOT_FOUND.value(), put.getStatusCode().value());
        assertEquals(DataDomainErrorCode.ECOS_DATA_032, body(put).getErrorCode());
    }

    @Test
    @DisplayName("弃用别名响应头不丢：X-Deprecation 仍在（保真改动不得顺手改掉 F02-01 契约）")
    void deprecationHeaderSurvivesTheStatusLineFix() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.previewSchema(Map.of("datasourceId", ""));

        assertEquals(java.util.List.of("true"),
                resp.getHeaders().get("X-Deprecation"), "状态行改了、弃用标记头丢了＝另一类契约破坏");
    }

    @Test
    @DisplayName("端点签名只增不改：previewSchema 仍收 datasourceId 请求体并返回承载状态的类型")
    void endpointSignatureIsUnchanged() throws NoSuchMethodException {
        Method m = PMO45DataSourceController.class
                .getMethod("previewSchema", Map.class);
        assertEquals(ResponseEntity.class, m.getReturnType(),
                "返回类型退回裸 ApiResponse＝状态行永远写不出来（D-B4-59 同型回退）");
        assertNotNull(m.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class),
                "路径注解面不得被摘除");
    }
}
