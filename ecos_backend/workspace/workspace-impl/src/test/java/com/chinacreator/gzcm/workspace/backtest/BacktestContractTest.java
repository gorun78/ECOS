package com.chinacreator.gzcm.workspace.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;

import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;
import com.chinacreator.gzcm.workspace.security.AbacQueryFilter;
import com.chinacreator.gzcm.workspace.security.RlsFilter;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chinacreator.gzcm.workspace.exception.WorkspaceExceptionHandler;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

/**
 * 回测五指标契约承流面验收（#146aa 附录A 第 33 条
 * {@code GET /api/v1/workspace/backtests?period=&granularity=}，listForecastBacktests，
 * 详设 line 8267）。
 *
 * <p>形态 = 窄 MockMvc（standaloneSetup + {@code WorkspaceExceptionHandler} 单源 advice，
 * 与 {@code ForecastContractActionsContractTest} 同法）：三滤波器已由
 * {@code /api/v1/workspace/**} 既有族统一覆盖，本批不新增登记 ⇒ 只钉
 * 「契约路径逐字 + 粒度词表单源 400 逐字段 + RLS 403/503 分态 + 501 承流形态」，
 * 纯内存无 DB、无网络。判定不翻转（端点可路由 ≠ 已实现，同 #146w 口径）。</p>
 */
@DisplayName("listForecastBacktests（第33条）契约承流面")
class BacktestContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── 装配 ──

    private MockMvc mvc(WorkspaceSecurityEngineClient client) {
        BacktestReadService service = new BacktestReadService(
                new ScenarioReadGuard(new AbacQueryFilter(client)));
        return MockMvcBuilders.standaloneSetup(new BacktestContractController(service))
                .setControllerAdvice(new WorkspaceExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(MAPPER))
                .build();
    }

    private static WorkspaceSecurityEngineClient rlsClientAllowAll() {
        WorkspaceSecurityEngineClient c = mock(WorkspaceSecurityEngineClient.class);
        Mockito.when(c.applyRlsFilter(any(), any(), anyMap())).thenReturn(RlsFilter.unrestricted());
        return c;
    }

    private static WorkspaceSecurityEngineClient rlsClientDeny() {
        WorkspaceSecurityEngineClient c = mock(WorkspaceSecurityEngineClient.class);
        Mockito.when(c.applyRlsFilter(any(), any(), anyMap()))
                .thenReturn(RlsFilter.deny("DENY", true, List.of()));
        return c;
    }

    private static WorkspaceSecurityEngineClient rlsClientUnavailable() {
        WorkspaceSecurityEngineClient c = mock(WorkspaceSecurityEngineClient.class);
        Mockito.when(c.applyRlsFilter(any(), any(), anyMap()))
                .thenThrow(new SecurityEngineUnavailableException("rls/rows", "process down", null));
        return c;
    }

    private static MvcResult get(MockMvc m, String url) throws Exception {
        return m.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
                .andReturn();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(MvcResult r) throws Exception {
        return MAPPER.readValue(
                r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object o) {
        return (List<String>) o;
    }

    // ── 用例 ──

    @Test
    @DisplayName("契约路径逐字：类级 /api/v1/workspace/backtests × 方法级裸 @GetMapping（getUrl + 两参均可空）")
    void contractPathIsVerbatim() throws Exception {
        org.springframework.web.bind.annotation.RequestMapping classMapping =
                BacktestContractController.class
                        .getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        assertNotNull(classMapping, "缺类级 @RequestMapping");
        assertEquals(List.of("/api/v1/workspace/backtests"), java.util.List.of(classMapping.value()),
                "契约路径段必须落在 /api/v1/workspace/backtests（类级独占前缀）");
        Method hit = null;
        for (Method m : BacktestContractController.class.getDeclaredMethods()) {
            var gm = m.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
            if (gm != null) {
                hit = m;
            }
        }
        assertNotNull(hit, "裸 @GetMapping（类级前缀即全路径）必须在位");
        assertEquals("listForecastBacktests", hit.getName());
        assertEquals(2, hit.getParameterCount(), "参面 = period + granularity，二者均可空");

        // 无参形态可被路由（不 404/405），落 501 承流腿
        MvcResult r = get(mvc(rlsClientAllowAll()), "/api/v1/workspace/backtests");
        assertEquals(501, r.getResponse().getStatus(), "可读裁决下承流腿 = 501，非 405/404");
        assertEquals("STUB_CAPABILITY", json(r).get("errorCode"));
    }

    @Test
    @DisplayName("粒度词表单源（DDL V217 单源）：枚举外取值（中文词/小写/臆造）一律 400 逐字段")
    void granularityOutVocabularyIs400() throws Exception {
        for (String granularity : new String[] {"project", "Project", "PROJECTX", "stage", "Department"}) {
            MvcResult r = get(mvc(rlsClientAllowAll()),
                    "/api/v1/workspace/backtests?granularity=" + granularity);
            assertEquals(400, r.getResponse().getStatus(), "granularity=" + granularity);
            Map<String, Object> root = json(r);
            assertEquals("ACTION_FIELDS_MISSING", root.get("errorCode"),
                    "400 形态与既有逐字段校验同码（X-16 不外设第二套错误面）: " + granularity);
            @SuppressWarnings("unchecked")
            List<String> missing = stringList(((Map<String, Object>) root.get("data")).get("missingFields"));
            assertEquals(List.of("granularity"), missing,
                    "仅 granularity 逐字段点名: " + granularity);
        }
    }

    @Test
    @DisplayName("合法三粒度过词表腿 ⇒ 501 承流（词表腿只拦枚举外，禁过拦）")
    void legalGranularityReachesStubLeg() throws Exception {
        for (String granularity : new String[] {"PROJECT", "DEPARTMENT", "STAGE"}) {
            MvcResult r = get(mvc(rlsClientAllowAll()),
                    "/api/v1/workspace/backtests?period=2026-09&granularity=" + granularity);
            assertEquals(501, r.getResponse().getStatus(),
                    "合法取值禁在本面拦（词表腿只拦枚举外），恒 501 承流: " + granularity);
            assertEquals("STUB_CAPABILITY", json(r).get("errorCode"), granularity);
        }
    }

    @Test
    @DisplayName("501 承流形态：stub=true + path 钉契约 + missing[] 五枚可复验事实（禁 200 空列表、禁指标值）")
    void stubFormIsHonest() throws Exception {
        MvcResult r = get(mvc(rlsClientAllowAll()), "/api/v1/workspace/backtests");
        assertEquals(501, r.getResponse().getStatus());
        Map<String, Object> root = json(r);
        assertEquals("STUB_CAPABILITY", root.get("errorCode"));
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        assertEquals(Boolean.TRUE, data.get("stub"), "stub 键必须真值 true（机读承流标记）");
        assertEquals("GET /api/v1/workspace/backtests", data.get("path"), "path 钉契约字面");
        assertEquals("workspace/backtests", data.get("capability"));
        List<String> missing = stringList(data.get("missing"));
        assertEquals(BacktestReadService.MISSING_FACTS, missing,
                "missing[] 必须逐枚 = 服务内取证清单（禁漂移）");
        assertEquals(BacktestReadService.MISSING_FACTS.size(), missing.size());
        // 禁指标值伪装：五指标列不得出现在承流体里
        for (String k : List.of("mae", "mape", "coverage", "dataCoverage", "biasDirection")) {
            assertTrue(data.get(k) == null, "承流体禁带指标列 " + k);
        }
    }

    @Test
    @DisplayName("403 腿：RLS 拒绝 ⇒ PERMISSION_DENIED 且实体编码钉 fc_backtest（security 单源咽喉在位）")
    void rlsDenyIs403() throws Exception {
        MvcResult r = get(mvc(rlsClientDeny()), "/api/v1/workspace/backtests");
        assertEquals(403, r.getResponse().getStatus());
        Map<String, Object> root = json(r);
        assertEquals("PERMISSION_DENIED", root.get("errorCode"));
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        String permission = String.valueOf(data.get("permission"));
        assertTrue(permission.endsWith(":fc_backtest"),
                "entityCode 必须钉 fc_backtest（安全主体不等于场景域默认实体）: " + permission);
    }

    @Test
    @DisplayName("503 腿：security 进程不可用 ⇒ ECOS-SEC-501（不可用≠无权限，禁折 403）")
    void securityUnavailableIs503() throws Exception {
        MvcResult r = get(mvc(rlsClientUnavailable()), "/api/v1/workspace/backtests");
        assertEquals(503, r.getResponse().getStatus());
        Map<String, Object> root = json(r);
        assertEquals("ECOS-SEC-501", root.get("errorCode"), "不可用态独立机读码");
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        assertEquals("security", data.get("engine"), "承载面 = security 引擎");
    }

    @Test
    @DisplayName("400 优先级在读守卫之前：非法 granularity 下 RLS 亦被咨询但不影响 400 判定")
    void granularityGatePrecedesGuard() throws Exception {
        // 用 deny 客户端同时在场：若词表腿先于守卫执行，形态仍是 400 而非 403
        MvcResult r = get(mvc(rlsClientDeny()),
                "/api/v1/workspace/backtests?granularity=project");
        assertEquals(400, r.getResponse().getStatus(), "入参错先于授权裁决（400 腿不被 403 吞）");
        assertEquals("ACTION_FIELDS_MISSING", json(r).get("errorCode"));
    }

    @Test
    @DisplayName("读守卫实体编码常量单源：fc_backtest 由服务持有，禁散字面量")
    void entityCodeSingleSourcedInService() throws Exception {
        // 反射取服务私有常量使用点不可行 ⇒ 以服务端到客户端的实参回显取证：
        // 单一 stub 按第一参分态（Mockito 双 stub 会 last-wins，禁同时挂两枚）。
        // 服务实发 fc_backtest ⇒ 恒真放行，过守卫落 501 承载；
        // 服务若漂移成其他实体编码 ⇒ 拒绝态上抛 403 ⇒ assertThrows(501 承载) 打红。
        WorkspaceSecurityEngineClient c = mock(WorkspaceSecurityEngineClient.class);
        Mockito.when(c.applyRlsFilter(any(), any(), anyMap())).thenAnswer(inv ->
                "fc_backtest".equals(inv.getArgument(0))
                        ? RlsFilter.unrestricted()
                        : RlsFilter.deny("WRONG_ENTITY", true, List.of()));
        BacktestReadService svc = new BacktestReadService(new ScenarioReadGuard(new AbacQueryFilter(c)));
        assertThrows(BacktestNotLiveException.class,
                () -> svc.list(null, null), "实体编码正确（arg0 逐字 fc_backtest）⇒ 过守卫落 501 承载");
    }
}
