package com.chinacreator.gzcm.runtime.access.git;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 签名能力闸门（§2.9.9 G2；#147G2）。
 *
 * <p><b>钉的是什么</b>：G2 的现状是「零签名」——提交与标签都不签名，而发布链却可能报成功。
 * 在真实 GPG/SSH 签名器接入前，唯一能做实的是<b>拒绝语义</b>：一旦部署声明
 * {@code ecos.git.signing.required=true} 而签名器未配置，三条写路径（{@code commit} /
 * {@code commitAllChanges} / {@code createTag}）必须在**碰文件系统之前**抛
 * {@code SIGNING_NOT_AVAILABLE}，而不是静默产出未签名制品。</p>
 *
 * <p><b>反证口径</b>：本测试用「传不存在的路径」取证——若闸门位于文件操作之后，
 * 抛出的会是路径类错误而非 {@code SIGNING_NOT_AVAILABLE}，断言即失败（证明顺序不可倒置）。</p>
 *
 * @author ECOS Runtime #147G2
 */
class GitSignatureRefusalTest {

    private static final String PROP_REQUIRED = "ecos.git.signing.required";
    private static final String PROP_ENABLED = "ecos.git.signing.enabled";

    @AfterEach
    void clearProps() {
        System.clearProperty(PROP_REQUIRED);
        System.clearProperty(PROP_ENABLED);
    }

    @Test
    @DisplayName("未要求签名：三条写路径不受影响（现状默认，禁误伤）")
    void notRequiredDoesNotBlock() throws Exception {
        GitServiceImpl git = new GitServiceImpl();
        git.assertSignatureCapability("commit");
    }

    @Test
    @DisplayName("要求签名且签名器已配置：放行（真实签名器接入后由此路径生效）")
    void requiredWithSignerDoesNotBlock() throws Exception {
        System.setProperty(PROP_REQUIRED, "true");
        System.setProperty(PROP_ENABLED, "true");
        new GitServiceImpl().assertSignatureCapability("commit");
    }

    @Test
    @DisplayName("要求签名但未配置签名器：显式拒绝，且发生在文件操作之前")
    void requiredWithoutSignerRefusesBeforeFsWork() {
        System.setProperty(PROP_REQUIRED, "true");
        System.setProperty(PROP_ENABLED, "false");
        GitServiceImpl git = new GitServiceImpl();

        GitService.GitException c1 = assertThrows(GitService.GitException.class,
                () -> git.commit("/definitely/not/a/repo", "msg", List.of()));
        assertTrue(c1.getMessage().contains(GitServiceImpl.SIGNING_NOT_AVAILABLE), c1.getMessage());

        GitService.GitException c2 = assertThrows(GitService.GitException.class,
                () -> git.commitAllChanges(null, "msg", "a", "a@b"));
        assertTrue(c2.getMessage().contains(GitServiceImpl.SIGNING_NOT_AVAILABLE), c2.getMessage());

        GitService.GitException c3 = assertThrows(GitService.GitException.class,
                () -> git.createTag(null, "v1", "msg"));
        assertTrue(c3.getMessage().contains(GitServiceImpl.SIGNING_NOT_AVAILABLE), c3.getMessage());
    }
}
