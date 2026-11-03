package com.chinacreator.gzcm.runtime.access.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 模拟签名门（§2.9.9 G2；裁决「先模拟签名，功能过」）。
 *
 * <p><b>三条不变量</b>：① 关闭时不改消息（现状零副作用）；② 开启时在消息尾注
 * {@code Ecos-Signature: simulated:<64hex>}，且对同一内容确定；③ kind 字面量<b>只能</b>是
 * {@code simulated}——真实 GPG/SSH 接入须另立 kind 并同批改本门，禁把模拟签冒充真签。</p>
 *
 * @author ECOS Runtime #147G2
 */
class GitSimulatedSignatureTest {

    private static final String PROP_ENABLED = "ecos.git.signing.enabled";

    @AfterEach
    void clearProps() {
        System.clearProperty(PROP_ENABLED);
    }

    @Test
    @DisplayName("未启用：消息原样（零副作用）")
    void disabledKeepsMessageUntouched() {
        assertEquals("feat: x", new GitServiceImpl().attachSignature("feat: x"));
    }

    @Test
    @DisplayName("启用：尾注 simulated:<sha256(内容)>，同内容确定一致")
    void enabledAppendsSimulatedTrailerDeterministically() {
        System.setProperty(PROP_ENABLED, "true");
        GitServiceImpl git = new GitServiceImpl();

        String signedA = git.attachSignature("feat: x");
        String signedB = new GitServiceImpl().attachSignature("feat: x");

        assertTrue(signedA.startsWith("feat: x\n\n" + GitServiceImpl.SIGNATURE_TRAILER), signedA);
        assertTrue(signedA.contains(GitServiceImpl.SIGNING_KIND_SIMULATED + ":"), signedA);
        assertEquals(signedA, signedB, "同内容同开关必须同结果（可复算）");
        String hash = signedA.substring(signedA.lastIndexOf(':') + 1);
        assertTrue(hash.matches("[0-9a-f]{64}"), "尾注哈希须为 64 位小写十六进制：" + hash);
    }

    @Test
    @DisplayName("kind 字面量钉死为 simulated（禁冒充 gpg/ssh）")
    void kindIsPinnedToSimulated() {
        assertEquals("simulated", GitServiceImpl.SIGNING_KIND_SIMULATED);
    }
}
