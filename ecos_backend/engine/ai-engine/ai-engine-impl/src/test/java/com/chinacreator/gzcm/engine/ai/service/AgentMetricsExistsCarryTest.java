package com.chinacreator.gzcm.engine.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.ai.controller.AiErrorAdvice;
import com.chinacreator.gzcm.engine.ai.dao.AgentMetricsDao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Agent 指标存在性探测的不可用承载 + ai 域根型腿的状态行保真
 * （差距 <b>D-B4-88</b> 第三枚收口件，#142/B4-3e-58）。
 *
 * <p><b>缺陷形态两半</b>：</p>
 * <ol>
 *   <li>服务侧 {@code AgentMetricsService.exists} 在 {@code catch (Exception)} 顶层
 *       {@code return true}（注释自证「宽松降级：查询失败不判 404，继续走主流程」）＝把「查不了」
 *       洗成「查到了」，调用方随后渲染一份全 0 的成功信封（§4.7.2 三态塌成一态）。
 *       本枚<b>不能</b>改判 {@code false}：那会把「查不了」折成「Agent 不存在 → 404」，
 *       是同一条铁律的反向违例，故只能显式承载。</li>
 *   <li>渲染侧 {@code AiErrorAdvice} 此前没有根型腿，兜底 {@code Exception} 走
 *       {@code ErrorCodeMapper.classify}，而分类器反射只认 {@code getStatus()}
 *       （根的携带通道是 {@code getHttpStatus()}）⇒ 503 载体按 GENERIC 面落
 *       {@code TOOL_BACKEND_ERROR}/502，即 D-B4-67 在第四宿主的副本。
 *       收口必须两侧同批改，否则服务侧的显式承载在出口又被折叠。</li>
 * </ol>
 */
class AgentMetricsExistsCarryTest {

    @Test
    @DisplayName("D-B4-88③：探测查询故障 → 抛携带 503 ＋ ECOS-SYS-503 的根异常，禁 return true")
    void probeFailureCarriesExplicitUnavailable() {
        AgentMetricsDao dao = mock(AgentMetricsDao.class);
        when(dao.queryExists("agent-1")).thenThrow(new IllegalStateException("metrics table missing"));
        AgentMetricsService service = new AgentMetricsService(dao);

        DataBridgeException thrown = assertThrows(DataBridgeException.class,
                () -> service.exists("agent-1"));
        assertEquals(503, thrown.getHttpStatus(), "不可用态必须携带 503");
        assertTrue(thrown.getMessage().contains(SystemErrorCode.ECOS_SYS_503),
                "机读码必须随载体上抛，实读=" + thrown.getMessage());
    }

    @Test
    @DisplayName("D-B4-88③分态：探测取到 0 行 → 如实 false（「不存在」与「查不了」不可互替）")
    void probeZeroRowsIsFalseNotUnavailable() {
        AgentMetricsDao dao = mock(AgentMetricsDao.class);
        when(dao.queryExists("agent-2")).thenReturn(0);
        assertEquals(false, new AgentMetricsService(dao).exists("agent-2"),
                "真取到 0 行才允许调用方判 404");
    }

    @Test
    @DisplayName("D-B4-88③不过头：探测取到 ≥1 行 → true（合法读面不被收口打死）")
    void probeHitRowsIsTrue() {
        AgentMetricsDao dao = mock(AgentMetricsDao.class);
        when(dao.queryExists("agent-3")).thenReturn(7);
        assertTrue(new AgentMetricsService(dao).exists("agent-3"));
    }

    @Test
    @DisplayName("D-B4-88③生效前提：AiErrorAdvice 根型腿跟随携带状态行，禁折成 ECOS-AI-502/502")
    void bridgeLegHonoursCarriedStatusInsteadOfToolBackendError() {
        DataBridgeException carried = new DataBridgeException(503, 503,
                SystemErrorCode.ECOS_SYS_503 + ": Agent 指标存在性判定通道不可用");

        ResponseEntity<ApiResponse<Void>> rendered = new AiErrorAdvice().onBridge(carried);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, rendered.getStatusCode(),
                "503 携带值原样上状态行；落 502＝把「不可用」说成「上游坏掉」（D-B4-67 同型）");
        ApiResponse<Void> body = rendered.getBody();
        assertEquals(SystemErrorCode.ECOS_SYS_503, body.getErrorCode(),
                "机读码走 MachineErrorCode 单源，禁被本域偏好码覆盖");
        assertEquals(503, body.getCode(), "信封 code 与状态行同号");
        assertNotEquals("ECOS-AI-502", body.getErrorCode(),
                "禁回落 TOOL_BACKEND_ERROR 兜底类");
    }

    @Test
    @DisplayName("D-B4-88③单源口径：越界携带值经 resolveHttpStatus 回退 500，本腿不自带区间判据")
    void bridgeLegUsesSingleSourceRangeJudgement() {
        ResponseEntity<ApiResponse<Void>> rendered =
                new AiErrorAdvice().onBridge(new DataBridgeException(0, 500, "未携带状态"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, rendered.getStatusCode(),
                "非法携带值由 common-api 单源决定回退，不在本腿复制第二份区间判据");
    }
}
