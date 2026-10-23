package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.sysman.config.entity.Config;
import com.chinacreator.gzcm.sysman.config.entity.SystemParam;
import com.chinacreator.gzcm.sysman.config.service.IConfigService;
import com.chinacreator.gzcm.sysman.config.service.IConfigVersionService;
import com.chinacreator.gzcm.sysman.config.service.ISystemParamService;
import com.chinacreator.gzcm.sysman.service.SysConfigQueryService;
import com.chinacreator.gzcm.sysman.testscan.SourceCommentStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #142/B4-3e-53 —— 收口差距 <b>D-B4-83</b>：sysman 配置面三枚端点的「依赖未装配」腿禁双向折叠。
 *
 * <h3>差距形态（修法前，实测 3 文件 15 枚腿）</h3>
 * <ul>
 *   <li>{@code ConfigController}：{@code list} 未装配 ⇒ {@code 200＋空集}；{@code versions} 未装配 ⇒
 *       {@code 200＋空列表}；{@code get/create/update/delete} 未装配 ⇒ {@code internalError(-1/500)}；</li>
 *   <li>{@code SystemParamController}：{@code list} 未装配 ⇒ {@code 200＋空集}，余四枚 ⇒ {@code internalError}；</li>
 *   <li>{@code SysConfigController}：四枚方法（列表／更新／元数据／审计）未装配 ⇒
 *       {@code 200＋空集}（{@code Map.of("data", List.of(), "total", 0)}）或 {@code internalError}。</li>
 * </ul>
 * <p>两种折叠方向都违详设 §4.7.2「不可用 ≠ 无权限 ≠ 空结果」：读侧把「服务没装配」谎报成
 * 「库里没数据」，写侧把可预期的缺装配谎报成本服务自身故障。登记时只记到 2 枚端点，
 * 属主侧逐枚勘察后订正为 <b>3 文件 15 枚腿</b>（同名 gateway 那枚 {@code SysConfigController}
 * 路径为 {@code /api/v1/sysconfig}，属主不同、不在本批面内）。</p>
 *
 * <h3>本门钉住的口径（沿用在册承载族，不另立第三形态）</h3>
 * <p>未装配腿统一抛 {@link DataBridgeException}(httpStatus=503, errorCode=503, 消息首段＝
 * {@code SystemErrorCode.ECOS_SYS_503})，由属主 {@code GlobalExceptionHandler}
 * （sysman-boot，#142/B4-3e-29 已收口 {@code resolveHttpStatus} 同号）渲染状态行；
 * 端点自身保留 {@code catch (DataBridgeException e) { throw e; }} 显式承载腿，禁被
 * {@code catch (Throwable|Exception)} 兜底折回 500。正向对照钉住<b>真实空结果不动</b>：
 * 服务在位且查到零条仍是 200＋空集。</p>
 *
 * <h3>能变红</h3>
 * <p>生产码退回缺陷形态时：第 1~3 案红（返回信封而非抛出）、第 4 案红（承载腿被兜底 catch 吃掉）、
 * 第 5~6 案红（正向对照被 fail-closed 取代 ⇒ 真空结果也变 503）、第 7 案红（源码面重新出现
 * {@code == null) return ApiResponse}／{@code 未就绪}／守卫数与承载腿数跌破逐枚计数）。</p>
 */
class SysConfigUnavailableCarrierTest {

    private static final String CONFIG_CTRL =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/"
                    + "gzcm/sysman/controller/ConfigController.java";
    private static final String PARAM_CTRL =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/"
                    + "gzcm/sysman/controller/SystemParamController.java";
    private static final String SYS_CONFIG_CTRL =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/"
                    + "gzcm/sysman/controller/SysConfigController.java";

    /** 未装配守卫（取实例）与显式承载腿的逐枚应有数——少一枚即说明该腿又折回兜底。 */
    private static final int CONFIG_GUARDS = 6;
    private static final int CONFIG_RETHROWS = 6;
    private static final int PARAM_GUARDS = 5;
    private static final int PARAM_RETHROWS = 5;
    private static final int SYS_CONFIG_GUARDS = 4;
    private static final int SYS_CONFIG_RETHROWS = 4;

    /**
     * 缺陷形态的字面特征（修法前实读），复现即红。
     * <p>守卫只认「{@code == null} 直接 return 信封」这两条腿：入参校验的
     * {@code if (val == null) return ApiResponse.badRequest(...)} 属正常语义面，
     * 不在折叠判据内（本批首跑实测把它误计为违例，故按腿形收窄）。</p>
     */
    private static final List<String> FOLD_GUARD_ONE_LINERS = List.of(
            "== null) return ApiResponse.internalError",
            "== null) return ApiResponse.success");
    private static final String FOLD_EMPTY_RESULT = "return ApiResponse.success(Collections.emptyList())";
    private static final String FOLD_EMPTY_WRAPPER = "\"data\", List.of(), \"total\", 0";
    private static final String NOT_READY_MESSAGE = "未就绪";

    // ── 1~3. 未装配腿：逐枚显式不可用 ─────────────────────────

    @Test
    @Timeout(120)
    @DisplayName("ConfigController 六枚腿：服务未装配一律抛 503＋ECOS-SYS-503，禁回 200＋空集也禁折 500")
    void configControllerLegsThrowCarriedUnavailable() throws Exception {
        ConfigController controller = new ConfigController(absentProvider(), absentProvider());

        assertCarrier(() -> controller.list(null, null, 1, 20), "list");
        assertCarrier(() -> controller.get("c-1"), "get");
        assertCarrier(() -> controller.create(new Config()), "create");
        assertCarrier(() -> controller.update("c-1", new Config()), "update");
        assertCarrier(() -> controller.delete("c-1"), "delete");
        assertCarrier(() -> controller.versions("c-1"), "versions");
    }

    @Test
    @Timeout(120)
    @DisplayName("SystemParamController 五枚腿：参数服务未装配一律抛 503＋ECOS-SYS-503")
    void systemParamControllerLegsThrowCarriedUnavailable() throws Exception {
        SystemParamController controller = new SystemParamController(absentProvider());

        assertCarrier(() -> controller.list(null, 1, 20), "list");
        assertCarrier(() -> controller.get("p-1"), "get");
        assertCarrier(() -> controller.create(new SystemParam()), "create");
        assertCarrier(() -> controller.update("p-1", new SystemParam()), "update");
        assertCarrier(() -> controller.delete("p-1"), "delete");
    }

    @Test
    @Timeout(120)
    @DisplayName("SysConfigController 四枚腿：查询服务未装配一律抛 503＋ECOS-SYS-503")
    void sysConfigControllerLegsThrowCarriedUnavailable() throws Exception {
        SysConfigController controller = new SysConfigController(absentProvider());

        assertCarrier(() -> controller.listByGroup("", ""), "listByGroup");
        assertCarrier(() -> controller.update("k", Map.of("value", "v")), "update");
        assertCarrier(() -> controller.metadata(), "metadata");
        assertCarrier(() -> controller.audit(), "audit");
    }

    // ── 4. 端点显式承载腿：不得被兜底 catch 折回 500 ──────────

    @Test
    @Timeout(120)
    @DisplayName("服务在位但抛承载型拒绝（403/410）时，三文件端点必须原样上抛，禁折 internalError")
    void endpointsRethrowCarriedDenialInsteadOfCollapsingTo500() throws Exception {
        IConfigService configService = mock(IConfigService.class);
        when(configService.listConfigs(any())).thenThrow(
                new DataBridgeException(403, 403, SystemErrorCode.ECOS_SYS_403 + ": 拒绝"));
        assertCarrier(() -> new ConfigController(
                        availableProvider(configService), absentProvider())
                        .list(null, null, 1, 20),
                403, "ConfigController.list");

        ISystemParamService paramService = mock(ISystemParamService.class);
        when(paramService.getParam(anyString())).thenThrow(
                new DataBridgeException(410, 410, SystemErrorCode.ECOS_SYS_403 + ": gone"));
        assertCarrier(() -> new SystemParamController(availableProvider(paramService)).get("p-1"),
                410, "SystemParamController.get");

        SysConfigQueryService queryService = mock(SysConfigQueryService.class);
        when(queryService.queryForList(anyString())).thenThrow(
                new DataBridgeException(503, 503, SystemErrorCode.ECOS_SYS_503 + ": dao down"));
        assertCarrier(() -> new SysConfigController(availableProvider(queryService)).metadata(),
                503, "SysConfigController.metadata");
    }

    // ── 5~6. 正向对照：真实空结果与真实数据都不受 fail-closed 影响 ──

    @Test
    @Timeout(120)
    @DisplayName("正向对照 A：服务在位且真零条＝仍是 200＋空集（不可用与空结果必须可区分）")
    void realEmptyResultStillReturnsSuccessWithEmptyData() throws Exception {
        IConfigService configService = mock(IConfigService.class);
        when(configService.listConfigs(any())).thenReturn(List.of());
        ApiResponse<Map<String, Object>> resp = new ConfigController(
                availableProvider(configService), absentProvider()).list(null, null, 1, 20);
        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(), "真实空结果不得被 fail-closed 折成不可用");
        assertEquals(List.of(), resp.getData().get("data"));
        assertEquals(0, resp.getData().get("total"));

        ISystemParamService paramService = mock(ISystemParamService.class);
        when(paramService.listParams(any())).thenReturn(List.of());
        ApiResponse<Map<String, Object>> paramResp = new SystemParamController(
                availableProvider(paramService)).list(null, 1, 20);
        assertEquals(ApiResponse.CODE_SUCCESS, paramResp.getCode());
        assertEquals(0, paramResp.getData().get("total"));

        SysConfigQueryService queryService = mock(SysConfigQueryService.class);
        when(queryService.queryForList(anyString())).thenReturn(List.of());
        ApiResponse<Map<String, Object>> sysResp = new SysConfigController(
                availableProvider(queryService)).listByGroup("", "");
        assertEquals(ApiResponse.CODE_SUCCESS, sysResp.getCode(),
                "§4.7.2：空结果走成功信封，只有「依赖不在」才走 503");
        assertEquals(List.of(), sysResp.getData().get("data"));
    }

    @Test
    @Timeout(120)
    @DisplayName("正向对照 B：服务在位且有数据＝回真实数据（证明对照 A 的空集不是判据失灵）")
    void assembledServiceWithRowsReturnsRealData() throws Exception {
        Config row = new Config();
        row.setConfigId("c-1");
        IConfigService configService = mock(IConfigService.class);
        when(configService.listConfigs(any())).thenReturn(List.of(row));
        ApiResponse<Map<String, Object>> resp = new ConfigController(
                availableProvider(configService), absentProvider()).list(null, null, 1, 20);
        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertEquals(1, resp.getData().get("total"), "对照 A 的 0 必须来自空集而非恒 0");

        SysConfigQueryService queryService = mock(SysConfigQueryService.class);
        when(queryService.queryForList(anyString())).thenReturn(List.of(Map.of("config_key", "k-1")));
        ApiResponse<List<Map<String, Object>>> meta = new SysConfigController(
                availableProvider(queryService)).metadata();
        assertEquals(ApiResponse.CODE_SUCCESS, meta.getCode());
        assertEquals(1, meta.getData().size());
    }

    // ── 7. 源码面：折叠形态不得复现，逐枚守卫与承载腿必须齐 ────

    @Test
    @Timeout(120)
    @DisplayName("主源码面：三文件零折叠字面形态，且未装配守卫／承载腿数量与逐枚勘察一致")
    void mainSourceHasNoFoldingShapeAndCarriesEveryLeg() throws IOException {
        String config = stripped(CONFIG_CTRL);
        String param = stripped(PARAM_CTRL);
        String sysConfig = stripped(SYS_CONFIG_CTRL);

        for (Map.Entry<String, String> file : Map.of(
                "ConfigController", config, "SystemParamController", param,
                "SysConfigController", sysConfig).entrySet()) {
            for (String guard : FOLD_GUARD_ONE_LINERS) {
                assertFalse(file.getValue().contains(guard),
                        file.getKey() + " 复现「未装配 ⇒ 直接 return 信封」的折叠守卫 ["
                                + guard + "]（D-B4-83）");
            }
            assertFalse(file.getValue().contains(NOT_READY_MESSAGE),
                    file.getKey() + " 复现「服务未就绪」文案（折叠腿的自证字样）");
            assertFalse(file.getValue().contains(FOLD_EMPTY_WRAPPER),
                    file.getKey() + " 复现「200＋空集」谎报形态");
        }
        assertFalse(config.contains(FOLD_EMPTY_RESULT),
                "ConfigController.versions 不得再把缺服务折成 200＋空列表");
        assertFalse(param.contains(FOLD_EMPTY_RESULT),
                "SystemParamController 不得再把缺服务折成 200＋空列表");

        assertEquals(CONFIG_GUARDS, occurrences(config, "getIfAvailable()"),
                "ConfigController 未装配守卫数漂移（逐枚勘察＝6 枚腿）");
        assertEquals(CONFIG_RETHROWS, occurrences(config, "catch (DataBridgeException"),
                "ConfigController 显式承载腿数漂移");
        assertEquals(PARAM_GUARDS, occurrences(param, "getIfAvailable()"),
                "SystemParamController 未装配守卫数漂移（逐枚勘察＝5 枚腿）");
        assertEquals(PARAM_RETHROWS, occurrences(param, "catch (DataBridgeException"),
                "SystemParamController 显式承载腿数漂移");
        assertEquals(SYS_CONFIG_GUARDS, occurrences(sysConfig, "getIfAvailable()"),
                "SysConfigController 未装配守卫数漂移（逐枚勘察＝4 枚腿）");
        assertEquals(SYS_CONFIG_RETHROWS, occurrences(sysConfig, "catch (DataBridgeException"),
                "SysConfigController 显式承载腿数漂移");

        assertTrue(config.contains("SystemErrorCode.ECOS_SYS_503"),
                "载体机读码必须取单源常量，禁写字面量");
    }

    // ── helpers ────────────────────────────────────────────

    private interface Call {
        Object run() throws Exception;
    }

    private static void assertCarrier(Call call, String leg) {
        assertCarrier(call, 503, leg);
    }

    private static void assertCarrier(Call call, int expectedStatus, String leg) {
        DataBridgeException thrown = assertThrows(DataBridgeException.class,
                () -> call.run(), leg + " 必须抛出承载型异常，不得返回信封（折叠即谎报）");
        assertEquals(expectedStatus, thrown.getHttpStatus(), leg + " 携带状态行不符");
        assertEquals(expectedStatus, thrown.getErrorCode(), leg + " 携带码须与状态行同号");
        assertNotNull(thrown.getMessage(), leg + " 消息不得为 null");
        if (expectedStatus == 503) {
            assertTrue(thrown.getMessage().startsWith(SystemErrorCode.ECOS_SYS_503 + ":"),
                    leg + " 消息首段必须是机读码 ECOS-SYS-503，实际：" + thrown.getMessage());
        }
        assertEquals(thrown.getHttpStatus(), DataBridgeException.resolveHttpStatus(thrown.getErrorCode()),
                leg + " 状态行必须由 resolveHttpStatus 单源推出");
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> absentProvider() {
        return mock(ObjectProvider.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> availableProvider(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }

    private static String stripped(String repoRelative) throws IOException {
        return SourceCommentStripper.strip(Files.readString(repoFile(repoRelative)));
    }

    private static int occurrences(String code, String needle) {
        int count = 0;
        for (int i = code.indexOf(needle); i >= 0; i = code.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    private static Path repoFile(String repoRelative) {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(repoRelative);
            if (Files.isRegularFile(candidate)) return candidate;
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到判据两侧源码（禁跳过校验）: " + repoRelative);
    }
}
