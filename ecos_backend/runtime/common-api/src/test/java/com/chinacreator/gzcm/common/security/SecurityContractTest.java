package com.chinacreator.gzcm.common.security;

import com.chinacreator.gzcm.common.security.credential.EcosServiceCredentialSigner;
import com.chinacreator.gzcm.common.security.registry.AnonymousEndpointRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W02/W05 契约层单测（详细设计-00 C.2.1/C.3.2）：
 * ① 服务凭证签名 round-trip 与篡改/过期拒绝；② 匿名面单源匹配一致性。
 */
class SecurityContractTest {

    private static final String SECRET = "unit-test-secret";

    @Test
    void serviceCredentialSignVerifyRoundTrip() {
        long now = 1_700_000_000L;
        EcosServiceCredentialSigner signer = new EcosServiceCredentialSigner("datanet", SECRET);
        String credential = signer.sign(now);
        assertTrue(credential.startsWith("datanet."), "凭证格式 serviceId.sig");
        assertTrue(EcosServiceCredentialSigner.verify(credential, now, SECRET));
    }

    @Test
    void serviceCredentialRejectsTamperedSigAndStaleWindow() {
        long now = 1_700_000_000L;
        String credential = new EcosServiceCredentialSigner("kb", SECRET).sign(now);
        // 篡改签名
        String tampered = credential.substring(0, credential.length() - 2)
                + (credential.endsWith("aa") ? "bb" : "aa");
        assertFalse(EcosServiceCredentialSigner.verify(tampered, now, SECRET));
        // 超窗（>5 分钟）
        assertFalse(EcosServiceCredentialSigner.verify(credential, now + 600, SECRET));
        // 错误密钥
        assertFalse(EcosServiceCredentialSigner.verify(credential, now, "other-secret"));
        // 畸形输入
        assertFalse(EcosServiceCredentialSigner.verify(null, now, SECRET));
        assertFalse(EcosServiceCredentialSigner.verify("a.b.c", now, SECRET));
        assertFalse(EcosServiceCredentialSigner.verify("no-dot", now, SECRET));
    }

    @Test
    void registryPermitAllCoversF7BaselineOfEightPatterns() {
        List<String> perms = AnonymousEndpointRegistry.permitAllPatterns();
        // F-7 基线 8 条 permitAll 必须全部可被单源覆盖（双路径展开）
        assertTrue(perms.contains("/auth/**"));
        assertTrue(perms.contains("/api/v1/auth/**"));
        assertTrue(perms.contains("/api/v1/engine/*/health"));
        assertTrue(perms.contains("/api/v1/knowledge/health"));
        assertTrue(perms.contains("/api/health"));
        assertTrue(perms.contains("/health"));
        assertTrue(perms.contains("/actuator/health"));
        assertTrue(perms.contains("/error"));
    }

    @Test
    void registryMatchingBehavior() {
        assertTrue(AnonymousEndpointRegistry.isAnonymous("/auth/login"));
        assertTrue(AnonymousEndpointRegistry.isAnonymous("/api/v1/auth/refresh"));
        assertTrue(AnonymousEndpointRegistry.isAnonymous("/api/v1/engine/data/health"));
        assertTrue(AnonymousEndpointRegistry.isAnonymous("/health"));
        assertTrue(AnonymousEndpointRegistry.isAnonymous("/error"));
        // 未登记即默认 DENY
        assertFalse(AnonymousEndpointRegistry.isAnonymous("/api/v1/system/users"));
        assertFalse(AnonymousEndpointRegistry.isAnonymous("/api/v1/knowledge/extract/x"));
        // clearance 豁免 ≠ 匿名（H9-T2 口径：security/audit 需认证）
        assertTrue(AnonymousEndpointRegistry.isClearedExempt("/api/v1/security/rls/apply"));
        assertFalse(AnonymousEndpointRegistry.isAnonymous("/api/v1/security/rls/apply"));
        assertTrue(AnonymousEndpointRegistry.isExempt("/api/v1/security/rls/apply"));
        assertTrue(AnonymousEndpointRegistry.isClearedExempt("/api/v1/audit/logs"));
    }

    @Test
    void everyRegistryEntryHasJustification() {
        for (AnonymousEndpointRegistry.Entry e : AnonymousEndpointRegistry.entries()) {
            assertNotNull(e.justification());
            assertFalse(e.justification().isBlank(), "每条必须写为什么可匿名");
            assertNotNull(e.approver());
            assertNotNull(e.date());
        }
    }
}
