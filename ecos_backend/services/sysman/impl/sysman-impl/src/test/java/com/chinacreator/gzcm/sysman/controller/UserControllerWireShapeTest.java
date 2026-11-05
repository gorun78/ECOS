package com.chinacreator.gzcm.sysman.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.dto.UserBatchSaveDTO;
import com.chinacreator.gzcm.sysman.dto.UserSaveDTO;
import com.chinacreator.gzcm.sysman.iam.cache.PermissionCacheService;
import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;
import com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;
import com.chinacreator.gzcm.sysman.service.UserQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 用户面 wire 键面保真门（#147CI · DoD DI-2 第十三批）。
 *
 * <p><b>钉的是什么</b>：列表 4 键＋行 11 键、详情 10 键、强制下线/随机密码各 3 键、
 * 批量创建 {@code errors} 仅非空时出现；建用户缺省（realName 回退 username、ACTIVE、locked=0）；
 * 改用户 {@code xxxPresent} 语义（显式 null 写空、未提供不动）。</p>
 *
 * <p><b>变异自证</b>：把 {@code UserVO#username} 改名 ⇒ 键集断言红；去掉
 * {@code UserUpdateRequest#setEmail} 的 present 标记 ⇒ 显式 null 写空用例红。</p>
 */
class UserControllerWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return MAPPER.convertValue(value, Map.class);
    }

    private static List<String> keysOf(Object value) {
        return new ArrayList<>(asMap(value).keySet());
    }

    private static UserController controller(IUserService users, UserQueryService queries) {
        return new UserController(users, queries, mock(PermissionCacheService.class),
                mock(IPermissionCheckService.class));
    }

    private static UserAccount account() {
        UserAccount u = new UserAccount();
        u.setUserId("u-1");
        u.setUsername("alice");
        u.setRealName("Alice");
        u.setEmail("a@e.local");
        u.setPhone("13800000000");
        u.setOrgId("o-1");
        u.setStatus("ACTIVE");
        u.setLocked("0");
        u.setLastLoginTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        u.setCreatedTime(LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        return u;
    }

    @Test
    @DisplayName("列表：4 键＋行 11 键（键序）；时间列保持 JDBC Timestamp")
    void listKeepsKeys() throws Exception {
        UserQueryService queries = mock(UserQueryService.class);
        when(queries.listUsers(any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new UserQueryService.UserPage(1, 20, 1, List.of(
                        new UserQueryService.UserListRow("u-1", "alice", "Alice", "a@e.local",
                                "138", "ACTIVE", "0",
                                Timestamp.valueOf("2026-01-02 03:04:05"),
                                Timestamp.valueOf("2026-01-01 00:00:00"), "o-1", "机构"))));
        UserController controller = controller(mock(IUserService.class), queries);

        UserQueryService.UserPage page = controller.list(null, null, 1, 20).getData();
        assertEquals(List.of("page", "pageSize", "total", "data"), keysOf(page));
        assertEquals(List.of("userId", "username", "realName", "email", "phone", "status",
                        "locked", "lastLoginTime", "createdTime", "orgId", "orgName"),
                keysOf(page.data().get(0)));
    }

    @Test
    @DisplayName("详情/创建回执：10 键（键序），口令字段永不出现")
    void userVOKeys() throws Exception {
        IUserService users = mock(IUserService.class);
        when(users.getUser("u-1")).thenReturn(account());
        UserController controller = controller(users, mock(UserQueryService.class));

        UserController.UserVO vo = controller.get("u-1").getData();
        assertEquals(List.of("userId", "username", "realName", "email", "phone", "orgId",
                "status", "locked", "lastLoginTime", "createdTime"), keysOf(vo));
        assertEquals(Set.of("userId", "username", "realName", "email", "phone", "orgId",
                        "status", "locked", "lastLoginTime", "createdTime"),
                asMap(vo).keySet(), "不得出现 password/passwordHash 等额外键");
    }

    @Test
    @DisplayName("建用户缺省：realName 回退 username、status=ACTIVE、locked=0")
    void createDefaults() throws Exception {
        IUserService users = mock(IUserService.class);
        when(users.createUser(any(), anyString(), anyString())).thenReturn(account());
        UserController controller = controller(users, mock(UserQueryService.class));

        UserController.UserCreateRequest req = new UserController.UserCreateRequest();
        req.setUsername("alice");
        req.setPassword("passw0rd!");
        controller.create(req);

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).createUser(captor.capture(), anyString(), anyString());
        assertEquals("alice", captor.getValue().getRealName());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        assertEquals("0", captor.getValue().getLocked());
    }

    @Test
    @DisplayName("改用户：显式 null 写空（containsKey 语义）、未提供不动")
    void updatePresenceSemantics() throws Exception {
        IUserService users = mock(IUserService.class);
        when(users.getUser("u-1")).thenReturn(account());
        when(users.updateUser(any(), anyString())).thenAnswer(inv -> inv.getArgument(0));
        UserController controller = controller(users, mock(UserQueryService.class));

        UserController.UserUpdateRequest onlyEmail = MAPPER.readValue(
                "{\"email\":\"b@e.local\"}", UserController.UserUpdateRequest.class);
        controller.update("u-1", onlyEmail);
        ArgumentCaptor<UserAccount> c1 = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).updateUser(c1.capture(), anyString());
        assertEquals("b@e.local", c1.getValue().getEmail());
        assertEquals("Alice", c1.getValue().getRealName(), "未提供 realName 不得动");

        when(users.getUser("u-1")).thenReturn(account());
        UserController.UserUpdateRequest explicitNull = MAPPER.readValue(
                "{\"phone\":null}", UserController.UserUpdateRequest.class);
        controller.update("u-1", explicitNull);
        ArgumentCaptor<UserAccount> c2 = ArgumentCaptor.forClass(UserAccount.class);
        verify(users, org.mockito.Mockito.atLeast(2)).updateUser(c2.capture(), anyString());
        assertNull(c2.getAllValues().get(1).getPhone(), "显式 null 必须写空");
    }

    @Test
    @DisplayName("强制下线/随机密码/批量创建回执键面；批量 errors 仅非空时出现")
    void receiptKeys() throws Exception {
        IUserService users = mock(IUserService.class);
        when(users.createUser(any(), anyString(), anyString())).thenReturn(account());
        UserQueryService queries = mock(UserQueryService.class);
        when(queries.update(anyString(), any(Object[].class))).thenReturn(2);
        UserController controller = controller(users, queries);

        assertEquals(List.of("userId", "sessionsCleared", "msg"),
                keysOf(controller.forceLogout("u-1").getData()));
        assertEquals(List.of("userId", "tempPassword", "msg"),
                keysOf(controller.resetPasswordT1("u-1").getData()));

        UserSaveDTO ok = new UserSaveDTO();
        ok.setUsername("bob");
        ok.setPassword("passw0rd!");
        UserBatchSaveDTO batch = new UserBatchSaveDTO();
        batch.setUsers(List.of(ok));
        UserController.BatchCreateResultVO clean = controller.batchCreate(batch).getData();
        assertEquals(Set.of("created", "failed"), asMap(clean).keySet(), "无错误时不得输出 errors 键");
        assertTrue(clean.failed() == 0);

        batch.setUsers(List.of(new UserSaveDTO()));
        UserController.BatchCreateResultVO failed = controller.batchCreate(batch).getData();
        assertEquals(Set.of("created", "failed", "errors"), asMap(failed).keySet());
        assertEquals(1, failed.failed());
    }
}
