package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.UnaryOperator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.core.crypto.SecurityCryptoEgress;

/**
 * W52 / F02-01（详细设计-02 C41，安全 P0）数据源口令加密治理验收：
 * 写前 {@code encryptPassword}（明文→密文，不降级明文），
 * 读后 {@code resolvePassword}（密文解密回填 connectionConfig，读路径不写库）。
 *
 * <p>经 runtime 安全出口 {@link SecurityCryptoEgress}（PMO-49 / H3-T-ARCH），本测试以镜像
 * 加解密出口模拟 KMS，判定契约（不触库 / 不受 profile 影响）：</p>
 * <ul>
 *   <li>round-trip：encrypt→decrypt 还原明文，且密文≠明文</li>
 *   <li>fail-closed：出口缺席（未注入）→ 拒保存明文口令（抛 ValidationException）</li>
 *   <li>fail-closed：ensureKey 失败仍不静默，encrypt 抛 ValidationException（不降级明文）</li>
 *   <li>resolvePassword：密文能解 → 回填 config 密码字段；解密失败 → 原样（不泄露/不阻断）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DatasourceCredentialCipherTest {

    private static final String KEY = "dataSourcePwd";

    /** 镜像加解密：opaque 包装，保证 round-trip 且密文≠明文。 */
    private static String enc(String s) {
        return "ENC(" + s + ")";
    }

    private static String dec(String s) {
        return s.startsWith("ENC(") && s.endsWith(")") ? s.substring(4, s.length() - 1) : s;
    }

    private static void lenientStub(SecurityCryptoEgress e, UnaryOperator<String> encOp) {
        doAnswer(inv -> null).when(e).ensureKey(anyString(), anyString(), anyInt());
        when(e.encrypt(anyString(), anyString()))
                .thenAnswer(inv -> encOp.apply(inv.getArgument(0, String.class)));
        when(e.decrypt(anyString(), anyString()))
                .thenAnswer(inv -> dec(inv.getArgument(0, String.class)));
    }

    /** 只启解密路径（读回填），不 stub encrypt（避免严格模式 UnusedStubbing）。读路径不触 ensureKey。 */
    private static void lenientDecryptOnly(SecurityCryptoEgress e) {
        lenient().when(e.decrypt(anyString(), anyString()))
                .thenAnswer(inv -> dec(inv.getArgument(0, String.class)));
    }

    private DataSourceServiceImpl svcWith(SecurityCryptoEgress egress) {
        DataSourceServiceImpl svc = new DataSourceServiceImpl(
                mock(JdbcTemplate.class), mock(ConnectorFactory.class));
        if (egress != null) {
            svc.setCryptoEgress(egress);
        }
        return svc;
    }

    @Test
    @DisplayName("round-trip — encrypt→decrypt 还原明文，密文≠明文，ensureKey 先行")
    void roundTrip_restoresPlain_andMasksCipher() {
        SecurityCryptoEgress e = mock(SecurityCryptoEgress.class);
        lenientStub(e, DatasourceCredentialCipherTest::enc);
        DataSourceServiceImpl svc = svcWith(e);

        String cipher = svc.encryptPassword("s3cret");
        assertNotEquals("s3cret", cipher, "落库不得是明文");
        assertEquals("s3cret", svc.decryptPassword(cipher), "round-trip 须还原明文");

        InOrder order = inOrder(e);
        order.verify(e).ensureKey(eq(KEY), eq("AES"), anyInt());
        order.verify(e).encrypt(eq("s3cret"), eq(KEY));
    }

    @Test
    @DisplayName("fail-closed — 出口未注入（无 KMS）→ 拒保存明文（抛 ValidationException，不降级）")
    void noEgress_refusesPlainText() {
        DataSourceServiceImpl svc = svcWith(null);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> svc.encryptPassword("plaintext"));
        assertTrue(ex.getMessage().contains("拒绝") || ex.getMessage().contains("KMS"),
                "应明示拒绝保存; 实际 " + ex.getMessage());
    }

    @Test
    @DisplayName("fail-closed — 出口 encrypt 抛异常 → 不降级明文（抛 ValidationException，异常详情不入文案）")
    void encryptFailure_staysFailLoud() {
        SecurityCryptoEgress e = mock(SecurityCryptoEgress.class);
        doAnswer(inv -> null).when(e).ensureKey(anyString(), anyString(), anyInt());
        when(e.encrypt(anyString(), anyString())).thenThrow(new RuntimeException("cipher down"));
        DataSourceServiceImpl svc = svcWith(e);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> svc.encryptPassword("x"));
        assertTrue(!ex.getMessage().contains("cipher down"), "异常详情不得泄入响应文案; 实际 " + ex.getMessage());
        verify(e, never()).decrypt(anyString(), anyString());
    }

    @Test
    @DisplayName("resolvePassword — 解密成功回填 config 密码字段（读路径，不写库）")
    void resolvePassword_injectsDecrypted() {
        SecurityCryptoEgress e = mock(SecurityCryptoEgress.class);
        lenientDecryptOnly(e);
        DataSourceServiceImpl svc = svcWith(e);

        String cfg = svc.resolvePassword("{\"host\":\"pg\",\"password\":\"********\"}", enc("real-pwd"));
        assertTrue(cfg.contains("\"real-pwd\""), "应回填解密值; 实际 " + cfg);
        assertTrue(!cfg.contains("\"********\""), "掩码应被真实值替换; 实际 " + cfg);
    }

    @Test
    @DisplayName("resolvePassword — 解密失败（出口不可用）→ 原样返回（不泄露、不阻断）")
    void resolvePassword_decryptUnavailable_keepsOriginal() {
        DataSourceServiceImpl svc = svcWith(null);
        String cfg = "{\"host\":\"pg\"}";
        assertEquals(cfg, svc.resolvePassword(cfg, "ciphertext"), "解密不可用应保持原 JSON");
        assertNull(svc.decryptPassword("ciphertext"), "出口缺席时 decrypt 返回 null 不抛");
    }
}
