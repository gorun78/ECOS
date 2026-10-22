package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.sysman.service.SecurityPolicyQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 安全策略表达式原文读面的<b>HTTP 状态行保真行为门</b>（#142/B4-3e-35，收口差距
 * <b>D-B4-59</b> 的 sysman 裸回腿）。
 *
 * <p><b>本腿为什么关键</b>：策略原文（Rego/ABAC 表达式）是安全裁决的<b>敏感读面</b>，
 * 其 403「角色不足」与此前 404/500 全落 HTTP 200。{@code ApiResponse} 只描述 body、
 * 没有任何 HTTP 状态能力，而 {@code SecurityDenialHttpStatusRatchetTest} 的判据 A 只看
 * 「裸回形态是否还在」——把属主腿折成 {@code ResponseEntity.ok(...)} 时文本门全绿，
 * 只有本门读状态行的<b>数值</b>才会变红。⇒ 关账 D-B4-59 必须以「文本门＋行为门」同时在链为前提。</p>
 */
class SecurityPolicyExprStatusFidelityTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<Object> eventBusProvider = mock(ObjectProvider.class);
    private final SecurityPolicyQueryService policyService = mock(SecurityPolicyQueryService.class);
    private final SecurityPolicyController controller =
            new SecurityPolicyController(policyService, eventBusProvider);

    private ApiResponse<Map<String, Object>> envelope(
            ResponseEntity<ApiResponse<Map<String, Object>>> resp) {
        assertNotNull(resp.getBody(), "ResponseEntity 的 body 不得为 null（状态行有了、信封丢了）");
        return resp.getBody();
    }

    @Test
    @DisplayName("非 admin/auditor 角色：状态行 403 且与信封同号，不再恒 200")
    void insufficientRoleCarries403OnTheStatusLine() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.getFullExpr("p-1", "READONLY");

        assertEquals(HttpStatus.FORBIDDEN.value(), resp.getStatusCode().value(),
                "敏感读面的拒绝只活在信封 code 里＝只看状态行的中间层把 403 读成成功");
        assertEquals(ApiResponse.CODE_FORBIDDEN, envelope(resp).getCode(),
                "状态行与信封必须同号，否则两层各读到一个结论");
    }

    @Test
    @DisplayName("大小写不敏感的角色准入仍成立：ADMIN 与 admin 都拿到 200（只补状态行，不改准入语义）")
    void allowedRoleCarries200() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "p-1");
        row.put("policyExpr", "allow");
        when(policyService.findFullExprById("p-1")).thenReturn(row);

        for (String role : new String[] {"ADMIN", "admin", "Security_Auditor"}) {
            ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                    controller.getFullExpr("p-1", role);
            assertEquals(HttpStatus.OK.value(), resp.getStatusCode().value(), "role=" + role);
            assertEquals(ApiResponse.CODE_SUCCESS, envelope(resp).getCode(), "role=" + role);
        }
    }

    @Test
    @DisplayName("策略不存在：状态行 404，与拒绝（403）在状态行上可区分")
    void missingPolicyCarries404() {
        when(policyService.findFullExprById(anyString())).thenReturn(null);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.getFullExpr("nope", "ADMIN");

        assertEquals(HttpStatus.NOT_FOUND.value(), resp.getStatusCode().value());
        assertEquals(ApiResponse.CODE_NOT_FOUND, envelope(resp).getCode());
    }

    /**
     * 查询失败腿：状态行 500，信封 code 仍是 {@code CODE_INTERNAL_ERROR = -1}。
     * 该同号差属 D-B4-65 余项、不在本批收口面内，此处把两侧读数都写成断言而非注释。
     */
    @Test
    @DisplayName("查询异常腿：状态行 500（信封仍 -1，同号差显式钉住，不靠沉默掩盖）")
    void queryFailureCarries500WhileEnvelopeKeepsTheRegisteredMinusOne() {
        when(policyService.findFullExprById(anyString())).thenThrow(new RuntimeException("dao down"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.getFullExpr("p-1", "ADMIN");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), resp.getStatusCode().value());
        assertEquals(ApiResponse.CODE_INTERNAL_ERROR, envelope(resp).getCode());
    }

    @Test
    @DisplayName("反向对照：状态行由信封 code 单源推出（改码即改行）")
    void statusLineIsDerivedFromTheEnvelopeCode() {
        for (int code : new int[] {403, 404, 503}) {
            assertEquals(code, DataBridgeException.resolveHttpStatus(code),
                    "code=" + code + " 未落到同号状态行＝单源推导被改动，本门与属主助手口径不再一致");
        }
    }
}
