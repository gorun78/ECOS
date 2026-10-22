package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.ai.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.engine.ai.agent.mesh.repository.AgentRegistryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 内置 Agent 删除保护的<b>HTTP 状态行保真行为门</b>（#142/B4-3e-35，收口差距
 * <b>D-B4-59</b> 的 ai 域裸回腿）。
 *
 * <p><b>本域的特殊性</b>：ai 域的状态行单源是 D.5 码表 {@link AiErrorClass#httpStatus()}
 * （信封 code 与状态行同源）。{@code deleteAgent} 此前与本域另一枚端点
 * （{@code testAgent}）写成 {@code ResponseEntity.status(HttpStatus.BAD_GATEWAY)} ＋
 * {@code AiResponseSupport.classified(...)} <b>两式各写一遍</b>——改码不改行就会漂移。
 * 本批把 {@code deleteAgent} 改为「状态行从信封 code 经
 * {@link DataBridgeException#resolveHttpStatus(int)} 推出」，并在这里把三态读数钉住。</p>
 *
 * <p>控制器用字段注入（{@code @Autowired(required = false)}），故经反射装桩——
 * 只测状态行，不测装配。</p>
 */
class AipDeleteAgentStatusFidelityTest {

    private final AgentRegistryRepository repo = mock(AgentRegistryRepository.class);

    private AIPAgentController controllerWithRepo() {
        AIPAgentController controller = new AIPAgentController();
        try {
            Field field = AIPAgentController.class.getDeclaredField("agentRepo");
            field.setAccessible(true);
            field.set(controller, repo);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("agentRepo 字段形态变更：本门的装桩通道失效（不得当成"
                    + "「状态行已保真」的读数）", e);
        }
        return controller;
    }

    private ApiResponse<Map<String, Object>> envelope(
            ResponseEntity<ApiResponse<Map<String, Object>>> resp) {
        assertNotNull(resp.getBody(), "ResponseEntity 的 body 不得为 null（状态行有了、信封丢了）");
        return resp.getBody();
    }

    @Test
    @DisplayName("依赖未装配腿：状态行 502＝D.5 TOOL_BACKEND_ERROR，与信封同号")
    void dependencyMissingCarries502() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                new AIPAgentController().deleteAgent("a-1");

        assertEquals(AiErrorClass.TOOL_BACKEND_ERROR.httpStatus(), resp.getStatusCode().value(),
                "依赖未就绪的不可用态落到 200＝调用方把「服务端没装配」读成「删成功了」");
        assertEquals(resp.getStatusCode().value(), envelope(resp).getCode(),
                "状态行与信封不同源＝改码不改行的漂移面（D-B4-52 同族）");
    }

    @Test
    @DisplayName("内置 Agent 删除保护：状态行 403 且与信封同号，不再恒 200（D-B4-59 本尊）")
    void builtinAgentCarries403OnTheStatusLine() {
        AgentRegistryEntity builtin = new AgentRegistryEntity();
        builtin.setRole("builtin");
        when(repo.findById("builtin-agent")).thenReturn(builtin);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controllerWithRepo().deleteAgent("builtin-agent");

        assertEquals(HttpStatus.FORBIDDEN.value(), resp.getStatusCode().value(),
                "拒绝语义只活在信封 code 里、状态行仍 200 ⇒ D-B4-59 在本端点复现");
        assertEquals(ApiResponse.CODE_FORBIDDEN, envelope(resp).getCode(),
                "状态行与信封必须同号，否则两层各读到一个结论");
    }

    @Test
    @DisplayName("Agent 不存在：状态行 404，与拒绝（403）在状态行上可区分")
    void unknownAgentCarries404() {
        when(repo.findById(anyString())).thenReturn(null);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controllerWithRepo().deleteAgent("nope");

        assertEquals(HttpStatus.NOT_FOUND.value(), resp.getStatusCode().value());
        assertEquals(ApiResponse.CODE_NOT_FOUND, envelope(resp).getCode());
    }

    @Test
    @DisplayName("删除成功腿：状态行 200 ＋ 信封 code=0（禁把成功折成失败）")
    void deletedAgentCarries200() {
        AgentRegistryEntity custom = new AgentRegistryEntity();
        custom.setRole("custom");
        when(repo.findById("a-1")).thenReturn(custom);
        when(repo.delete("a-1")).thenReturn(1);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controllerWithRepo().deleteAgent("a-1");

        assertEquals(HttpStatus.OK.value(), resp.getStatusCode().value());
        assertEquals(ApiResponse.CODE_SUCCESS, envelope(resp).getCode());
    }

    @Test
    @DisplayName("删除影响 0 行：仍是 404，不落到「成功 0 行」的假绿")
    void zeroRowDeleteIsStillNotFound() {
        AgentRegistryEntity custom = new AgentRegistryEntity();
        custom.setRole("custom");
        when(repo.findById("a-2")).thenReturn(custom);
        when(repo.delete("a-2")).thenReturn(0);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controllerWithRepo().deleteAgent("a-2");

        assertEquals(HttpStatus.NOT_FOUND.value(), resp.getStatusCode().value(),
                "rows=0 折成 200＝前端把删除失败渲染成删除成功");
    }

    @Test
    @DisplayName("反向对照：状态行由信封 code 单源推出（改码即改行）")
    void statusLineIsDerivedFromTheEnvelopeCode() {
        for (int code : new int[] {403, 404, 502, 503}) {
            assertEquals(code, DataBridgeException.resolveHttpStatus(code),
                    "code=" + code + " 未落到同号状态行＝单源推导被改动，本门与属主助手口径不再一致");
        }
    }
}
