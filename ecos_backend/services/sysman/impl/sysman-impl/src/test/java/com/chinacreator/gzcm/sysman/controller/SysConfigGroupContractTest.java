package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AuthErrorCode;
import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.sysman.security.MinimumClearance;
import com.chinacreator.gzcm.sysman.service.SysConfigQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #146u —— 差距台账附录A 第 3 条：{@code GET /api/v1/system/config/group}（getConfigByGroup）契约路径。
 *
 * <h3>本批补的是什么</h3>
 * <p>原文记「偏差（实为根 GET 分页返回，无 /group 子路径）」。契约行（《ECOS详细设计说明书-从零实施版》
 * 接口总表 getConfigByGroup）要求入参 {@code configGroup、subsystem、keys[]}、出参
 * {@code {items[{key,value,valueType,versionNo}]}}、面级 {@code L0 需服务凭证}。本批在
 * {@link SysConfigController} 追加 {@code @GetMapping("/group")}，四枚既有端点一字未动。</p>
 *
 * <h3>钉住的四条如实口径（禁造假值）</h3>
 * <ol>
 *   <li>服务凭证：缺头/非 {@code serviceId.sig} 形态 ⇒ 403 {@link AuthErrorCode#ECOS_AUTH_011}，
 *       且状态行由 {@link DataBridgeException#resolveHttpStatus} 单源推出（契约错误列的
 *       {@code ECOS-AUTH-410} 在属主族无条目，按「零新增码表条目」复用 011 并在类注释在册）；</li>
 *   <li>{@code versionNo} 恒 {@code null} ＋ {@code versionNoApplicable=false}：
 *       {@code sys_config} 无版本列（DB 只存在用版本、历史进 Git），禁按 {@code updated_at} 反推版本号；</li>
 *   <li>{@code subsystem} 只作 {@code config_group} 同名别名的回显（{@code subsystemSource} 自证），
 *       二者并存互异 ⇒ 400；</li>
 *   <li>{@code keys[]} 真过滤，未命中逐枚进 {@code missingKeys[]}，不补空值。</li>
 * </ol>
 *
 * <h3>能变红</h3>
 * <p>去掉凭证校验（第 1／2 案）、把 {@code versionNo} 填成非空或漏 {@code versionNoApplicable}
 * （第 5 案）、丢掉 {@code missingKeys}（第 5 案）、把 method 上的 {@link MinimumClearance}(0)
 * 摘掉（第 7 案落 {@code /api/v1/system/} 路径默认 L3）、keys 不再透传给服务（第 6 案
 * {@code verify} 失败）都会红。</p>
 */
class SysConfigGroupContractTest {

    private static final String VALID_CREDENTIAL = "kb-engine.9f8e7d6c5b4a";

    // ── 1~2. 服务凭证：缺头 / 形态非法 ────────────────────────

    @Test
    @Timeout(120)
    @DisplayName("缺 X-ECOS-SERVICE ⇒ 403＋ECOS-AUTH-011（状态行随携带值，不走 200 信封）")
    void missingServiceCredentialIsForbidden() {
        SysConfigController controller = new SysConfigController(availableProvider(mock(SysConfigQueryService.class)));

        assertDeny(() -> controller.getConfigByGroup("global", "", null, "", null), "缺凭证头");
        assertDeny(() -> controller.getConfigByGroup("global", "", null, "", "   "), "空白凭证");
        assertDeny(() -> controller.getConfigByGroup("global", "", null, "", "kb-engine"), "无签名段");
        assertDeny(() -> controller.getConfigByGroup("global", "", null, "", ".sig"), "缺 serviceId");
        assertDeny(() -> controller.getConfigByGroup("global", "", null, "", "kb-engine."), "缺签名");
    }

    // ── 3. 分组入参：两个别名互异 / 皆空 ──────────────────────

    @Test
    @Timeout(120)
    @DisplayName("configGroup 与 subsystem 并存互异 ⇒ 400＋ECOS-SYS-400；二者皆空同为 400")
    void groupResolutionRejectsAmbiguousOrEmptyInput() {
        SysConfigController controller = new SysConfigController(availableProvider(mock(SysConfigQueryService.class)));

        DataBridgeException mismatch = assertThrows(DataBridgeException.class,
                () -> controller.getConfigByGroup("cognitive", "general", null, "", VALID_CREDENTIAL));
        assertEquals(400, mismatch.getHttpStatus());
        assertEquals(400, mismatch.getErrorCode());
        assertTrue(mismatch.getMessage().startsWith(SystemErrorCode.ECOS_SYS_400 + ":"),
                "消息首段须为机读码，实际：" + mismatch.getMessage());

        DataBridgeException empty = assertThrows(DataBridgeException.class,
                () -> controller.getConfigByGroup("", "  ", null, "", VALID_CREDENTIAL));
        assertEquals(400, empty.getHttpStatus());
        assertTrue(empty.getMessage().startsWith(SystemErrorCode.ECOS_SYS_400 + ":"));
    }

    // ── 4~6. 正常面：载荷诚实位 + 真过滤 + 透传 ───────────────

    @Test
    @Timeout(120)
    @DisplayName("命中行投影契约键：versionNo 恒 null＋versionNoApplicable=false；missingKeys 逐枚留痕；subsystem 回显自证")
    void contractPayloadIsHonestAndFilteringIsReal() {
        SysConfigQueryService service = mock(SysConfigQueryService.class);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("config_key", "default_page_size");
        row.put("config_value", "20");
        row.put("config_type", "number");
        row.put("edition", "all");
        row.put("updated_at", Timestamp.from(Instant.parse("2026-10-28T02:03:04Z")));
        when(service.listByConfigGroupForContract(eq("general"), any(), eq(""), any()))
                .thenReturn(List.of(row));

        SysConfigController controller = new SysConfigController(availableProvider(service));
        ApiResponse<Map<String, Object>> resp = controller.getConfigByGroup(
                "", "general", List.of("default_page_size", "no_such_key"), "", VALID_CREDENTIAL);

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        Map<String, Object> data = resp.getData();
        assertEquals("general", data.get("configGroup"));
        assertEquals("general", data.get("subsystem"), "subsystem 按 §十.1 与 config_group 同名回显");
        assertEquals("config_group_alignment", data.get("subsystemSource"),
                "回显必须自证是「同名对齐」而非独立列");
        assertEquals(List.of("default_page_size", "no_such_key"), data.get("requestedKeys"));
        assertEquals(1, data.get("itemCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertEquals(1, items.size());
        Map<String, Object> item = items.get(0);
        assertEquals("default_page_size", item.get("key"));
        assertEquals("20", item.get("value"));
        assertEquals("number", item.get("valueType"));
        assertNull(item.get("versionNo"), "sys_config 无版本列 ⇒ 该键必须为空，禁合成版本号");
        assertEquals(false, item.get("versionNoApplicable"), "空版本面必须带 applicability 自证位");
        assertEquals("2026-10-28T02:03:04Z", item.get("updatedAt"), "真实时间面交付 ISO-8601");

        assertEquals(List.of("no_such_key"), data.get("missingKeys"),
                "请求键未命中必须逐枚留痕，不得静默丢弃也不得补空值");
    }

    @Test
    @Timeout(120)
    @DisplayName("keys 归一（空白/重复/逗号混排）后透传给服务；edition 原样透传")
    void keysAreNormalizedAndForwarded() {
        SysConfigQueryService service = mock(SysConfigQueryService.class);
        when(service.listByConfigGroupForContract(eq("cognitive"), any(), eq("enterprise"), any()))
                .thenReturn(List.of());

        SysConfigController controller = new SysConfigController(availableProvider(service));
        ApiResponse<Map<String, Object>> resp = controller.getConfigByGroup(
                "cognitive", "", List.of(" a , b", "a", "  "), "enterprise", VALID_CREDENTIAL);

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertEquals(List.of("a", "b"), resp.getData().get("requestedKeys"));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<String>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(service).listByConfigGroupForContract(eq("cognitive"), captor.capture(),
                eq("enterprise"), any());
        assertEquals(List.of("a", "b"), captor.getValue(), "服务侧必须收到归一后的键集");
    }

    // ── 7. 注解面：/group 必须显式 L0（否则落 /api/v1/system/ 默认 L3） ──

    @Test
    @Timeout(120)
    @DisplayName("/group 方法必须带 @GetMapping(\"/group\") 与 @MinimumClearance(0)（路径默认 L3 会把 L0 契约面挡死）")
    void endpointCarriesExplicitContractAnnotations() throws Exception {
        Method method = SysConfigController.class.getMethod("getConfigByGroup",
                String.class, String.class, List.class, String.class, String.class);

        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertNotNull(mapping, "缺 @GetMapping ⇒ 契约子路径不存在");
        assertEquals(List.of("/group"), List.of(mapping.value()),
                "契约路径段必须是 /group（与类级 /api/v1/system/config 拼出契约全路径）");

        MinimumClearance clearance = method.getAnnotation(MinimumClearance.class);
        assertNotNull(clearance, "缺 @MinimumClearance ⇒ 落路径默认 L3，与契约 L0 不符");
        assertEquals(0, clearance.level(), "契约面为 L0");
    }

    // ── helpers ─────────────────────────────────────────────

    private interface Call {
        Object run() throws Exception;
    }

    private static void assertDeny(Call call, String leg) {
        DataBridgeException thrown = assertThrows(DataBridgeException.class, call::run,
                leg + " 必须抛携带型拒绝，不得返回 200 信封");
        assertEquals(403, thrown.getHttpStatus(), leg + " 状态行不符");
        assertEquals(403, thrown.getErrorCode(), leg + " 携带码须与状态行同号");
        assertTrue(thrown.getMessage().startsWith(AuthErrorCode.ECOS_AUTH_011 + ":"),
                leg + " 消息首段须为 ECOS-AUTH-011，实际：" + thrown.getMessage());
        assertEquals(thrown.getHttpStatus(), DataBridgeException.resolveHttpStatus(thrown.getErrorCode()),
                leg + " 状态行必须由 resolveHttpStatus 单源推出");
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> availableProvider(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }
}
