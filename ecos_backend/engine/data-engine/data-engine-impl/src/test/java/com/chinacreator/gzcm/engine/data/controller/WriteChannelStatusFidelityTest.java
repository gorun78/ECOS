package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.dto.WriteChannelRequest;
import com.chinacreator.gzcm.engine.data.dto.WriteChannelResult;
import com.chinacreator.gzcm.engine.data.writechannel.DwWriteChannelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 业务域唯一写通道端点的<b>HTTP 状态行保真行为门</b>（#142/B4-3e-35，收口差距
 * <b>D-B4-59</b> 的写通道裸回腿）。
 *
 * <p><b>为什么文本门不够</b>：{@code SecurityDenialHttpStatusRatchetTest} 的判据 A 只看
 * 「是否还有 {@code return ApiResponse.forbidden(...)} 这种裸回形态」，把属主腿折成
 * {@code ResponseEntity.ok(...)} 时它<b>全绿</b>（该门自己的 javadoc 已把这一失明面登记在册）。
 * 本门是它的行为侧配对：逐腿断言状态行的<b>数值</b>，因此「摘掉 {@code ResponseEntity.status(...)}
 * 换成 ok()」这类变异会在这里变红，而不是只在这里变红。</p>
 *
 * <p><b>§4.7.2 的分态判据</b>：MC02/MC04 守卫拒绝（403）与入参缺失（400）必须在状态行上可分派；
 * 二者此前都落 HTTP 200，只看状态行的中间层（网关、告警、Playwright 用例）把守卫拒绝读成成功。</p>
 */
class WriteChannelStatusFidelityTest {

    private final DwWriteChannelService service = mock(DwWriteChannelService.class);
    private final WriteChannelController controller = new WriteChannelController(service);
    private final WriteChannelRequest request = new WriteChannelRequest();

    private ApiResponse<WriteChannelResult> envelope(ResponseEntity<ApiResponse<WriteChannelResult>> resp) {
        assertNotNull(resp.getBody(), "ResponseEntity 的 body 不得为 null（状态行有了、信封丢了）");
        return resp.getBody();
    }

    @Test
    @DisplayName("成功腿：状态行 200 ＋ 信封 code=0（禁把成功折成失败）")
    void successLegCarries200() {
        when(service.write(any(WriteChannelRequest.class))).thenReturn(new WriteChannelResult());

        ResponseEntity<ApiResponse<WriteChannelResult>> resp = controller.write(request);

        assertEquals(HttpStatus.OK.value(), resp.getStatusCode().value());
        assertEquals(ApiResponse.CODE_SUCCESS, envelope(resp).getCode());
    }

    @Test
    @DisplayName("守卫拒绝腿（MC02/MC04）：状态行 403 且与信封同号——不再恒 200（D-B4-59 本尊）")
    void guardDenialLegCarries403OnTheStatusLine() {
        when(service.write(any(WriteChannelRequest.class)))
                .thenThrow(new IllegalStateException("ECOS-DATA-063 MC02 裸 JSON 列"));

        ResponseEntity<ApiResponse<WriteChannelResult>> resp = controller.write(request);

        assertEquals(HttpStatus.FORBIDDEN.value(), resp.getStatusCode().value(),
                "写通道守卫拒绝的状态行仍是 200＝拒绝语义只活在信封里，传输层无从分派");
        assertEquals(ApiResponse.CODE_FORBIDDEN, envelope(resp).getCode(),
                "状态行与信封必须同号，否则两层各读到一个结论");
    }

    @Test
    @DisplayName("入参拒绝腿：状态行 400，与守卫拒绝（403）在状态行上可区分")
    void badRequestLegIsNotTheSameAsDenial() {
        when(service.write(any(WriteChannelRequest.class)))
                .thenThrow(new IllegalArgumentException("ECOS-DATA-061 layer 非法"));

        ResponseEntity<ApiResponse<WriteChannelResult>> resp = controller.write(request);

        assertEquals(HttpStatus.BAD_REQUEST.value(), resp.getStatusCode().value());
        assertEquals(ApiResponse.CODE_BAD_REQUEST, envelope(resp).getCode());
    }

    /**
     * 内部错误腿：状态行 500，而信封 code 仍是 {@code CODE_INTERNAL_ERROR = -1}。
     *
     * <p>这个<b>同号差</b>是 D-B4-65 的余项，不在本批收口面内；这里把它写成断言而不是注释，
     * 是为了让「两侧读数不一致」成为一个可被推翻的登记，而不是靠沉默掩盖。
     * 500 由 {@link DataBridgeException#resolveHttpStatus(int)} 对越界 code 的兜底推出，
     * 本类没有第二个状态字面量。</p>
     */
    @Test
    @DisplayName("内部错误腿：状态行 500（信封仍 -1，该同号差属 D-B4-65 余项，此处显式钉住两侧读数）")
    void internalErrorLegCarries500WhileEnvelopeKeepsTheRegisteredMinusOne() {
        when(service.write(any(WriteChannelRequest.class))).thenThrow(new RuntimeException("dao down"));

        ResponseEntity<ApiResponse<WriteChannelResult>> resp = controller.write(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), resp.getStatusCode().value());
        assertEquals(ApiResponse.CODE_INTERNAL_ERROR, envelope(resp).getCode());
    }

    @Test
    @DisplayName("反向对照：本类的状态行确实由信封 code 单源推出（改码即改行）")
    void statusLineIsDerivedFromTheEnvelopeCode() {
        for (int code : new int[] {400, 403, 503}) {
            ResponseEntity<ApiResponse<WriteChannelResult>> probe =
                    ResponseEntity.status(DataBridgeException.resolveHttpStatus(code)).build();
            assertEquals(code, probe.getStatusCode().value(),
                    "code=" + code + " 未落到同号状态行＝单源推导被改动，本门与属主助手的口径不再一致");
        }
    }
}
