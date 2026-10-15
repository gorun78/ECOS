package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.crypto.KeyManagementService;
import com.chinacreator.gzcm.engine.security.crypto.KeyManagementService.KeyManagementException;
import com.chinacreator.gzcm.engine.security.crypto.model.KeyMetadata;
import com.chinacreator.gzcm.engine.security.crypto.service.CryptoKeyRegistryService;
import com.chinacreator.gzcm.engine.security.crypto.service.impl.KeyManagementServiceFullImpl;
import com.chinacreator.gzcm.engine.security.service.SecurityAuditService;
import com.chinacreator.gzcm.sysman.security.MinimumClearance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 密钥登记与轮换端点契约门禁（详设-01 §4.3.1 L2585-2586，#142/B4-1 第 4/5 项）。
 *
 * <p>钉死六件事：
 * <ol>
 *   <li><b>永不返回密钥材料</b>：{@code GET /keys} 每条 item 的键集合<b>恰等于</b>契约七键，
 *       且任何值都不得是 {@code byte[]} / {@code Key} 实例；再配一道源码面守卫
 *       （端点与投影层不得出现 {@code getKey(} / {@code getKeyBytes(}）；</li>
 *   <li>轮换 <b>L4 且二次确认</b>：缺 {@code confirm} → 428 + errorCode + 审计，
 *       <b>且底座 {@code rotateKey} 一次都不许被调</b>（否则「确认门禁」只是响应码装饰）；</li>
 *   <li><b>主密钥缺失 → 500 {@code ECOS-SEC-430}</b>（§4.3.3「禁静默建钥」）：
 *       同样要断言 {@code rotateKey} 未被调用——底座本身不看 {@code ECOS_MASTER_KEY}，
 *       若只断言响应码，「先建钥再报错」也能骗过；</li>
 *   <li>成功形态 = 契约三键 {@code {keyId,newKeyVersion,retiredVersion}}，
 *       且轮换后同 keyId <b>只剩一个 ACTIVE</b>（底座原生行为是两个都 ACTIVE，
 *       {@code retiredVersion} 因此恒无值可取）；</li>
 *   <li>未登记 keyId → 404；底座异常 → 503 {@code ECOS-SEC-501}（DENY，不返回空清单冒充成功）；</li>
 *   <li>档位与双路径映射在位。</li>
 * </ol>
 *
 * <p>轮换主链路用<b>真实的</b> {@link KeyManagementServiceFullImpl}（内存底座）而非替身，
 * 这样「两个版本都留 ACTIVE」的底座缺陷、「退役后仍可解密旧版本」的矩阵纪律才是在真实行为上被验证的。</p>
 */
class CryptoKeyAdminContractTest {

    private static final String KEY_ID = "dek.contract.test";

    private final SecurityAuditService audit = mock(SecurityAuditService.class);

    /** 主密钥在场可控的登记表：覆写 {@code masterKeyPresent()} 接缝，禁为测试改生产装配。 */
    private static final class SeamedRegistry extends CryptoKeyRegistryService {
        private final boolean masterPresent;

        SeamedRegistry(KeyManagementService svc, boolean masterPresent) {
            super(svc);
            this.masterPresent = masterPresent;
        }

        @Override
        protected boolean masterKeyPresent() {
            return masterPresent;
        }
    }

    private CryptoKeyAdminController controllerWithRealBackend(boolean masterPresent) {
        return new CryptoKeyAdminController(new SeamedRegistry(new KeyManagementServiceFullImpl(), masterPresent), audit);
    }

    /** 底座里预置一把 v1 ACTIVE 的 DEK（模拟「已登记密钥」的真实状态）。 */
    private static KeyManagementServiceFullImpl backendWithActiveKey(String keyId) throws Exception {
        KeyManagementServiceFullImpl backend = new KeyManagementServiceFullImpl();
        backend.createKey(keyId, "DEK", "AES", 256, "seed");
        return backend;
    }

    @Test
    @DisplayName("GET /keys：item 键集合恰为契约七键，值不得是密钥材料")
    void listKeysProjectsExactlySevenContractFields() throws Exception {
        KeyManagementServiceFullImpl backend = backendWithActiveKey(KEY_ID);
        CryptoKeyAdminController controller =
                new CryptoKeyAdminController(new SeamedRegistry(backend, true), audit);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.listKeys(null);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> data = resp.getBody().getData();
        assertNotNull(data);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertFalse(items.isEmpty(), "底座已建钥时清单不得为空（为空即投影失效）");
        for (Map<String, Object> item : items) {
            assertEquals(Set.of("keyId", "algorithm", "keyVersion", "status",
                            "masterKeyRef", "rotateAt", "retiredAt"),
                    item.keySet(), "item 键集合必须恰等于契约 L2585 的七键");
            for (Map.Entry<String, Object> e : item.entrySet()) {
                Object v = e.getValue();
                assertFalse(v instanceof byte[], "字段 " + e.getKey() + " 不得是密钥字节");
                assertFalse(v instanceof Key, "字段 " + e.getKey() + " 不得是 Key 对象");
            }
        }
        assertEquals(items.size(), data.get("total"));
        assertEquals("IN_MEMORY_KEY_MANAGEMENT_SERVICE", data.get("registrySource"),
                "台账数据面必须自我标注，防止空清单被读成「平台无密钥治理」");
    }

    @Test
    @DisplayName("投影层源码守卫：登记面不得调用 getKey/getKeyBytes（材料只许在加解密面出现）")
    void registryAndControllerNeverTouchKeyMaterial() throws IOException {
        for (String rel : List.of(
                "src/main/java/com/chinacreator/gzcm/engine/security/controller/CryptoKeyAdminController.java",
                "src/main/java/com/chinacreator/gzcm/engine/security/crypto/service/CryptoKeyRegistryService.java")) {
            Path p = Paths.get(rel);
            // 取不到源文件必须 FAIL：静默跳过＝把这条守卫变成恒 PASS（差距文档 D-15 那类假绿）
            assertTrue(Files.exists(p), "源文件不可达，无法做材料面守卫：" + p.toAbsolutePath());
            String src = Files.readString(p, StandardCharsets.UTF_8);
            assertFalse(src.contains("getKeyBytes("), rel + " 不得读取密钥字节");
            assertFalse(src.contains(".getKey("), rel + " 不得取 Key 对象");
        }
    }

    @Test
    @DisplayName("retiredAt 语义：在用钥匙不给退役时刻，RETIRED 才回填更新时间")
    void retiredAtOnlyForNonActiveStates() {
        KeyMetadata active = new KeyMetadata();
        active.setKeyId(KEY_ID);
        active.setStatus("ACTIVE");
        active.setUpdatedTime(new java.util.Date());
        assertNull(CryptoKeyRegistryService.project(active).get("retiredAt"),
                "ACTIVE 钥匙的 retiredAt 必须是 null（用更新时间冒充即假数据）");

        KeyMetadata retired = new KeyMetadata();
        retired.setKeyId(KEY_ID);
        retired.setStatus("RETIRED");
        java.util.Date at = new java.util.Date(1_700_000_000_000L);
        retired.setUpdatedTime(at);
        assertEquals(at, CryptoKeyRegistryService.project(retired).get("retiredAt"));
    }

    @Test
    @DisplayName("缺 confirm → 428 + errorCode + 审计留痕，且 rotateKey 一次都没调")
    void rotateWithoutConfirmIs428AndDoesNotRotate() throws Exception {
        KeyManagementService backend = mock(KeyManagementService.class);
        CryptoKeyAdminController controller =
                new CryptoKeyAdminController(new SeamedRegistry(backend, true), audit);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.rotate(
                KEY_ID, Map.of("reason", "季度轮换"));

        assertEquals(HttpStatus.PRECONDITION_REQUIRED, resp.getStatusCode());
        ApiResponse<Map<String, Object>> body = resp.getBody();
        assertEquals(428, body.getCode());
        assertEquals(CryptoKeyAdminController.ERROR_ROTATE_CONFIRM_REQUIRED, body.getErrorCode());
        assertNotNull(body.getTraceId());
        verify(backend, never()).rotateKey(anyString(), anyString());
        verify(audit).asyncWrite(anyString(), any(), any(), eq("CRYPTO_KEY_ROTATE"),
                eq("crypto:key:" + KEY_ID), eq("REJECTED_CONFIRM_REQUIRED"), any(), any());
    }

    @Test
    @DisplayName("缺 reason → 400（契约 body 要求 reason，L4 操作不许无因由）")
    void rotateWithoutReasonIs400() throws Exception {
        KeyManagementService backend = mock(KeyManagementService.class);
        CryptoKeyAdminController controller =
                new CryptoKeyAdminController(new SeamedRegistry(backend, true), audit);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.rotate(KEY_ID, Map.of("confirm", true));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(400, resp.getBody().getCode());
        verify(backend, never()).rotateKey(anyString(), anyString());
    }

    @Test
    @DisplayName("主密钥缺失 → 500 ECOS-SEC-430 且禁静默建钥（rotateKey 不得被调用）")
    void masterKeyMissingDeniesAndCreatesNothing() throws Exception {
        KeyManagementService backend = mock(KeyManagementService.class);
        CryptoKeyAdminController controller =
                new CryptoKeyAdminController(new SeamedRegistry(backend, false), audit);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.rotate(
                KEY_ID, Map.of("reason", "轮换", "confirm", true));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        ApiResponse<Map<String, Object>> body = resp.getBody();
        assertEquals(CryptoKeyAdminController.ERROR_MASTER_KEY_MISSING, body.getErrorCode());
        assertEquals("ECOS-SEC-430", CryptoKeyAdminController.ERROR_MASTER_KEY_MISSING,
                "错误码单源必须与 §4.3.3 一致");
        assertNull(body.getData(), "500 分支禁带 data");
        verify(backend, never()).rotateKey(anyString(), anyString());
        verify(backend, never()).createKey(anyString(), anyString(), anyString(), anyInt(), anyString());
        verify(audit).asyncWrite(anyString(), any(), any(), eq("CRYPTO_KEY_ROTATE"),
                eq("crypto:key:" + KEY_ID), eq("REJECTED_MASTER_KEY_MISSING"), any(), any());
    }

    @Test
    @DisplayName("已确认 + 主密钥在场 → 真轮换：契约三键 + 旧版本退役只剩一个 ACTIVE")
    void confirmedRotationRetiresPreviousVersion() throws Exception {
        KeyManagementServiceFullImpl backend = backendWithActiveKey(KEY_ID);
        CryptoKeyAdminController controller =
                new CryptoKeyAdminController(new SeamedRegistry(backend, true), audit);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.rotate(
                KEY_ID, Map.of("reason", "季度轮换", "confirm", "true"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> data = resp.getBody().getData();
        assertEquals(Set.of("keyId", "newKeyVersion", "retiredVersion"), data.keySet(),
                "响应必须恰为契约 L2586 三键");
        assertEquals(KEY_ID, data.get("keyId"));
        assertEquals(2, data.get("newKeyVersion"));
        assertEquals(1, data.get("retiredVersion"), "retiredVersion 必须是被退役的旧版本号，不是 null");

        List<KeyMetadata> versions = backend.getKeyVersions(KEY_ID);
        assertEquals(2, versions.size());
        long active = versions.stream().filter(m -> "ACTIVE".equals(m.getStatus())).count();
        assertEquals(1, active,
                "底座原生 rotateKey 会把两个版本都留 ACTIVE（契约 retiredVersion 无从取值），登记层必须收敛为一个");
        // 矩阵纪律 §4.7.2：退役只改元数据，旧版本 Key 仍可取 ⇒ 既有密文可继续解密
        assertNotNull(backend.getKey(KEY_ID, 1), "旧版本密钥对象必须保留，否则历史密文无法解密");
        verify(audit).asyncWrite(anyString(), any(), any(), eq("CRYPTO_KEY_ROTATE"),
                eq("crypto:key:" + KEY_ID), eq("SUCCESS"), any(), any());
    }

    @Test
    @DisplayName("未登记 keyId → 404（禁凭空建钥）")
    void rotationOfUnknownKeyIs404() throws Exception {
        KeyManagementServiceFullImpl backend = new KeyManagementServiceFullImpl();
        CryptoKeyAdminController controller =
                new CryptoKeyAdminController(new SeamedRegistry(backend, true), audit);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.rotate(
                "key.never.registered", Map.of("reason", "轮换", "confirm", true));

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertTrue(backend.queryKeys(null, null).isEmpty(), "404 分支不得留下任何密钥版本");
    }

    @Test
    @DisplayName("底座异常 → 清单 503 ECOS-SEC-501 / 轮换 503，禁返回空清单冒充成功")
    void backendFailureFailsClosed() throws Exception {
        KeyManagementService backend = mock(KeyManagementService.class);
        when(backend.queryKeys(any(), any())).thenThrow(new KeyManagementException("keystore unavailable"));
        CryptoKeyAdminController controller =
                new CryptoKeyAdminController(new SeamedRegistry(backend, true), audit);

        ResponseEntity<ApiResponse<Map<String, Object>>> list = controller.listKeys(null);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, list.getStatusCode());
        assertEquals(CryptoKeyAdminController.ERROR_SEC_UNAVAILABLE, list.getBody().getErrorCode());
        assertNull(list.getBody().getData(), "DENY 面禁带 items 空列表冒充「无密钥」");

        KeyManagementService rotateBackend = mock(KeyManagementService.class);
        when(rotateBackend.queryKeys(any(), any())).thenReturn(List.of(activeMetadata()));
        when(rotateBackend.rotateKey(anyString(), anyString()))
                .thenThrow(new KeyManagementException("rotation failed"));
        CryptoKeyAdminController c2 =
                new CryptoKeyAdminController(new SeamedRegistry(rotateBackend, true), audit);
        ResponseEntity<ApiResponse<Map<String, Object>>> rot = c2.rotate(
                KEY_ID, Map.of("reason", "轮换", "confirm", true));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, rot.getStatusCode());
        assertEquals(CryptoKeyAdminController.ERROR_SEC_UNAVAILABLE, rot.getBody().getErrorCode());
    }

    @Test
    @DisplayName("档位 = L4（契约两行都写 L4）且双路径映射在位")
    void clearanceL4AndDualPathMapping() throws Exception {
        Method list = CryptoKeyAdminController.class.getMethod("listKeys", String.class);
        Method rotate = CryptoKeyAdminController.class.getMethod(
                "rotate", String.class, Map.class);
        for (Method m : List.of(list, rotate)) {
            MinimumClearance mc = m.getAnnotation(MinimumClearance.class);
            assertNotNull(mc, m.getName() + " 缺 @MinimumClearance ⇒ 落 /api/v1/security/ 默认 L2，低于契约 L4");
            assertEquals(4, mc.level());
        }
        assertNotNull(list.getAnnotation(GetMapping.class));
        assertNotNull(rotate.getAnnotation(PostMapping.class));
        RequestMapping rm = CryptoKeyAdminController.class.getAnnotation(RequestMapping.class);
        assertTrue(List.of(rm.value()).contains("/api/v1/security/crypto"));
        assertTrue(List.of(rm.value()).contains("/api/security/crypto"));
    }

    private static KeyMetadata activeMetadata() {
        KeyMetadata m = new KeyMetadata();
        m.setKeyId(KEY_ID);
        m.setVersion(1);
        m.setStatus("ACTIVE");
        return m;
    }
}
