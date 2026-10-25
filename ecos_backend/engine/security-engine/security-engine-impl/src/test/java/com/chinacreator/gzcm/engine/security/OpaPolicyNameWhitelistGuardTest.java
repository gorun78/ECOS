package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 策略名白名单是唯一路径护栏 —— 运行期证据（#142/B4-3e-60／差距 D-B4-91 的死面删除配套）。
 *
 * <p><b>为什么存在</b>：本批删除了 {@code OpaPolicyService.resolveSafe} 的后置守卫
 * {@code if (!file.startsWith(policyDir)) throw new SecurityException("路径越界…")}。
 * 删它的依据是「{@code SAFE_NAME} 白名单在前，越界分支恒不可达＝死面」（详见该方法 javadoc），
 * 而<b>「死面」这个结论本身必须可跑</b>：若日后有人放松白名单或新增绕过它的调用方，
 * 后置守卫已经不在，护栏就真没了。故把「越界名被白名单拒掉、且拒发生在文件访问之前」
 * 钉成运行期用例，而不是只留一段叙述。</p>
 *
 * <p><b>与门面棘轮门的分工</b>：{@code ServiceThrownSecurityExceptionFaceRatchetTest}（common-api）
 * 数的是源码形态（抛点面枚数、白名单词表在位、{@code resolveSafe} 站点数）；本类数的是<b>行为</b>。
 * 两者互为反面证据：源码门防「守卫被删」，本门防「守卫删了且白名单也松了」。</p>
 */
@DisplayName("OPA 策略名白名单护栏（D-B4-91 死面删除后的唯一防线）")
class OpaPolicyNameWhitelistGuardTest {

    /**
     * 合法但不可能预先存在的策略名（过白名单词表，且带 UUID 保证任何策略目录下都没有该文件）。
     * 用固定名会依赖「解析后的目录里恰好没有该 {@code .rego}」——运维设了
     * {@code ECOS_OPA_POLICY_DIR} 时该前提不再由本类掌控。
     */
    private static final String ABSENT_LEGAL_NAME =
            "probe-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);

    private static OpaPolicyService serviceAt(Path dir) {
        // opa.url 用不可达地址：本类只关心「文件访问之前」的名称校验，不该真的咨询 OPA
        return new OpaPolicyService("http://nonexistent.invalid:1", dir.toString());
    }

    @Test
    @DisplayName("读策略传越界名 → IllegalArgumentException（拒在白名单，不落 SecurityException）")
    void getPolicyRejectsTraversalNameBeforeAnyFileAccess(@TempDir Path tmp) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> serviceAt(tmp).getPolicy("../../etc/passwd"));
        assertTrue(ex.getMessage().contains("非法策略名称"),
                "必须由白名单出言拒绝：" + ex.getMessage());
        assertFalse(ex.getMessage().contains("路径越界"),
                "后置守卫（SecurityException「路径越界」）本批已按死面删除；"
                        + "若此断言变红＝有人补回了那枚恒不可达的腿，须重裁死/活（D-B4-77 治理口径）");
    }

    @Test
    @DisplayName("写策略传越界名 → IllegalArgumentException 且目标文件未被创建")
    void updatePolicyRejectsTraversalNameWithoutWriting(@TempDir Path tmp) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> serviceAt(tmp).updatePolicy("ok/../../escape", "package ecos.escape"));
        assertTrue(ex.getMessage().contains("非法策略名称"),
                "写通道同样必须先过白名单：" + ex.getMessage());
        assertFalse(java.nio.file.Files.exists(tmp.resolve("escape.rego")),
                "越界名不得产生任何落盘副作用");
    }

    @Test
    @DisplayName("正向对照：合法但不存在的策略名返回 null（白名单不得误伤成一律抛异常）")
    void legalMissingNameStillReturnsNull(@TempDir Path tmp) {
        OpaPolicyService svc = serviceAt(tmp);
        assertNull(svc.getPolicy(ABSENT_LEGAL_NAME),
                "合法名＋文件不存在＝真实空结果，必须返回 null；"
                        + "把它改成抛异常＝用「一律拒绝」伪装收口（§4.7.2 空结果 ≠ 不可用 ≠ 无权限）");
    }
}
