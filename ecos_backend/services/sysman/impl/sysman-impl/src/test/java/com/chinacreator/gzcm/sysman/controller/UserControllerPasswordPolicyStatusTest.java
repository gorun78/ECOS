package com.chinacreator.gzcm.sysman.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.cache.PermissionCacheService;
import com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;
import com.chinacreator.gzcm.sysman.service.UserQueryService;

/**
 * D84 回归：口令策略拒绝必须回 400，不能回 500。
 *
 * <p>缺陷原形：`UserServiceImpl.validatePasswordPolicy` 抛 `UserException("密码需包含大小写字母、数字和特殊字符")`，
 * 而 controller 的 `catch (Exception)` 一刀切 `internalError` ⇒ 用户输入不合规被报成「服务端故障 500」，
 * 客户端拿不到任何可操作提示。</p>
 *
 * <p>判别口径：`UserException` 无 cause = 业务拒绝（策略不满足/对象不存在）→ 400；
 * 带 cause = DAO 包装失败 → 仍回 500。两条腿必须同时成立，防止「全部改 400」把真故障洗成客户端错误。</p>
 */
class UserControllerPasswordPolicyStatusTest {

    private static UserController controller(IUserService users) {
        return new UserController(users, mock(UserQueryService.class), mock(PermissionCacheService.class),
                mock(IPermissionCheckService.class),
                mock(com.chinacreator.gzcm.sysman.iam.service.impl.LoginCredentialService.class));
    }

    @Test
    @DisplayName("口令不合策略（UserException 无 cause）⇒ 400 且带原文案，不是 500")
    void passwordPolicyRejectionReturnsBadRequest() throws Exception {
        IUserService users = mock(IUserService.class);
        String reason = "密码需包含大小写字母、数字和特殊字符";
        doThrow(new IUserService.UserException(reason))
                .when(users).resetPassword(eq("u-1"), anyString(), anyString());

        ApiResponse<Void> res = controller(users).resetPassword("u-1", Map.of("password", "simple"));

        assertEquals(400, res.getCode(), "策略拒绝必须是 400（客户端可改），不是 500");
        assertEquals(reason, res.getMessage(), "必须回传具体原因，否则用户不知道改哪里");
    }

    @Test
    @DisplayName("DAO 失败（UserException 带 cause）⇒ 仍走内部故障分支，不伪装成客户端错误")
    void daoFailureStillReturnsInternalError() throws Exception {
        IUserService users = mock(IUserService.class);
        doThrow(new IUserService.UserException("重置密码失败: connection refused", new RuntimeException("boom")))
                .when(users).resetPassword(eq("u-1"), anyString(), anyString());

        ApiResponse<Void> res = controller(users).resetPassword("u-1", Map.of("password", "Str0ng#Passw0rd"));

        // internalError 的 code 约定是 -1（由 HttpStatusFidelityAdvice 在 HTTP 层映射 500），
        // 关键断言是「不能被 400 分支吞掉」——失败细节也不得回传客户端。
        assertEquals(-1, res.getCode(), "带 cause 的是服务端故障，必须走 internalError 而非 badRequest");
        assertEquals("重置密码失败（详情见服务端日志）", res.getMessage(), "内部异常细节不得回传");
    }

    @Test
    @DisplayName("建用户时用户名已存在（无 cause）⇒ 400")
    void duplicateUsernameReturnsBadRequest() throws Exception {
        IUserService users = mock(IUserService.class);
        doThrow(new IUserService.UserException("用户名已存在"))
                .when(users).createUser(any(), anyString(), anyString());
        UserController.UserCreateRequest body = new UserController.UserCreateRequest();
        body.setUsername("dup");
        body.setPassword("Str0ng#Passw0rd");

        ApiResponse<?> res = controller(users).create(body);

        assertNotNull(res);
        assertEquals(400, res.getCode());
    }
}
