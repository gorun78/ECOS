package com.chinacreator.gzcm.sysman.boot.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.sysman.boot.service.AuthService;
import com.chinacreator.gzcm.sysman.dto.RefreshTokenRequest;
import com.chinacreator.gzcm.sysman.dto.TokenRefreshResponse;
import com.chinacreator.gzcm.sysman.security.JwtTokenProvider;

import io.jsonwebtoken.Claims;

/**
 * 缺陷 D157 门禁：刷新令牌后签发的 accessToken 必须带完整角色。
 *
 * <p>原症状：首登 accessToken 的 {@code roles} claim 完整，用同一会话的 refreshToken
 * 换新 accessToken 后 {@code roles} 变成 {@code []}。OPA 取 {@code input.subject.roles}、
 * {@code PermissionAspect} 与 {@code GitRepoOrgGuard} 比 {@code ROLE_SUPER_ADMIN}，
 * 于是超管在前端到期刷新后变成无角色主体。</p>
 *
 * <p>根因是 {@code createRefreshToken} 从不写 roles claim，而 refresh 偏从该 claim 取角色。
 * 本用例钉住修法＝按 userId 重读隶属表现值，因此「撤权后刷新角色收缩」也必须成立。</p>
 */
class AuthControllerRefreshRolesTest {

    private static final List<String> ADMIN_ROLES =
            List.of("ROLE_SUPER_ADMIN", "ADMIN", "SECURITY_AUDITOR");

    /** 隶属表替身：角色现值可改，用于区分「重读」与「透传旧 claim」。 */
    private static final class StubAuth extends AuthService {
        private List<String> current;

        StubAuth(List<String> initial) {
            super(null);
            this.current = initial;
        }

        @Override
        public List<String> findRoleCodes(String userId) {
            return current;
        }
    }

    private JwtTokenProvider jwt;
    private StubAuth auth;
    private AuthController controller;

    @BeforeEach
    void setUp() throws Exception {
        // 空密钥 ⇒ JwtTokenProvider 自生成临时 RSA 对，不依赖本机 pem
        jwt = new JwtTokenProvider("", "");
        auth = new StubAuth(ADMIN_ROLES);
        controller = new AuthController(jwt, auth, null);
    }

    /** 登录腿把 refreshToken 记进内存吊销表（AuthController:224），此处照同一形态登记。 */
    @SuppressWarnings("unchecked")
    private static Map<String, String> revocationStore() throws Exception {
        Field f = AuthController.class.getDeclaredField("refreshTokens");
        f.setAccessible(true);
        return (Map<String, String>) f.get(null);
    }

    private String issueRegisteredRefreshToken(String userId) throws Exception {
        String token = jwt.createRefreshToken(userId);
        revocationStore().put(token, userId);
        return token;
    }

    private List<String> rolesOf(String accessToken) {
        Claims claims = jwt.validateToken(accessToken);
        assertEquals("access", claims.get("type"), "签发的必须是 accessToken");
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        return roles;
    }

    @Test
    @DisplayName("刷新后 accessToken 角色完整（D157 原症状：恒为空表）")
    void refreshKeepsFullRoles() throws Exception {
        String refresh = issueRegisteredRefreshToken("u-admin");

        var resp = controller.refresh(new RefreshTokenRequest(refresh));

        assertEquals(200, resp.getStatusCode().value(), "刷新应 200");
        assertNotNull(resp.getBody(), "响应体不得为空");
        assertEquals(0, resp.getBody().getCode(), "信封 code 应为 0");
        TokenRefreshResponse data = resp.getBody().getData();
        assertNotNull(data.accessToken(), "必须返回新 accessToken");
        assertEquals(ADMIN_ROLES, rolesOf(data.accessToken()),
                "刷新后角色被清空 ⇒ 超管在令牌刷新后失去全部授权面");
    }

    @Test
    @DisplayName("撤权后刷新：角色按隶属表现值收缩（证明是重读，不是透传旧 claim）")
    void refreshReflectsRevokedRoles() throws Exception {
        String first = issueRegisteredRefreshToken("u-admin");
        var r1 = controller.refresh(new RefreshTokenRequest(first));
        String rotated = r1.getBody().getData().refreshToken();
        assertNotNull(rotated, "刷新应轮换 refreshToken");

        // 撤掉超管与审计角色，只留部门用户
        auth.current = List.of("DEPT_USER");
        var r2 = controller.refresh(new RefreshTokenRequest(rotated));

        assertEquals(200, r2.getStatusCode().value());
        assertEquals(List.of("DEPT_USER"), rolesOf(r2.getBody().getData().accessToken()),
                "撤权未在刷新时生效 ⇒ 旧角色被续命");
    }

    @Test
    @DisplayName("refreshToken 自身不带 roles claim（钉住根因：透传路线无从取角色）")
    void refreshTokenCarriesNoRolesClaim() throws Exception {
        String refresh = issueRegisteredRefreshToken("u-admin");
        Claims claims = jwt.validateToken(refresh);
        assertEquals("refresh", claims.get("type"));
        assertNull(claims.get("roles"),
                "createRefreshToken 写了 roles ⇒ 根因描述失效，需重新裁定修法");
    }

    @Test
    @DisplayName("无角色用户刷新仍 200，角色为空表而非 null")
    void refreshWithNoRolesStillSucceeds() throws Exception {
        auth.current = List.of();
        String refresh = issueRegisteredRefreshToken("u-norole");

        var resp = controller.refresh(new RefreshTokenRequest(refresh));

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(List.of(), rolesOf(resp.getBody().getData().accessToken()));
    }

    @Test
    @DisplayName("已吊销的 refreshToken 仍 401（修角色不得放开吊销校验）")
    void revokedRefreshTokenStillRejected() throws Exception {
        String refresh = issueRegisteredRefreshToken("u-admin");
        revocationStore().remove(refresh);

        var resp = controller.refresh(new RefreshTokenRequest(refresh));

        assertEquals(401, resp.getStatusCode().value());
        assertTrue(resp.getBody().getMessage().contains("吊销"),
                "拒绝理由应指明已吊销，实测：" + resp.getBody().getMessage());
    }
}