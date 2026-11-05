package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.engine.security.service.SecurityConfigService;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 安全配置面 wire 键面门（DI-2 #147DC）。
 *
 * <p>读数 12 键（source 首键 + 视图 11 键；档案列非空的后 5 键条件出现）、分页 4 键、
 * 删除回执 2 键；更新回执的动态 id 列保持 {@code user_id}/{@code role_id}。</p>
 */
class SecurityConfigWireShapeTest {

    private SecurityConfigService service;
    private SecurityConfigController controller;

    @BeforeEach
    void setUp() {
        service = mock(SecurityConfigService.class);
        controller = new SecurityConfigController(service);
    }

    private static SecurityProfile profile() {
        SecurityProfile p = new SecurityProfile();
        p.setClearanceLevel(2);
        p.setLevel(2);
        p.setLevelName("L2保密");
        p.setLinkedWorkstation("WS-01");
        p.setAuditMode("full");
        p.setSandboxMandatory(true);
        p.setUserId("u1");
        return p;
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode json(Object payload) {
        return new ObjectMapper().valueToTree(payload);
    }

    @Test
    @DisplayName("读数：12 键（source 首键），userId 有值出键、roleId 等空列不出键")
    void readShape() {
        when(service.queryUserProfile(anyString())).thenReturn(profile());
        when(service.populateProfileResult(org.mockito.ArgumentMatchers.any())).thenCallRealMethod();

        JsonNode data = json(controller.getProfile("u1", null, null, null).getData());

        assertEquals(List.of("source", "clearanceLevel", "level", "levelName", "linkedWorkstation",
                "auditMode", "sandboxMandatory", "userId"), keys(data));
        assertEquals("user", data.path("source").asText());
        assertEquals("L2保密", data.path("levelName").asText());
    }

    @Test
    @DisplayName("读数兜底：无档案时 6 键，按 ClearanceLevel 单源缺省且无 userId/roleId")
    void readFallbackShape() {
        when(service.queryUserProfile(anyString())).thenReturn(null);
        when(service.queryGlobalDefaultProfile()).thenReturn(null);
        when(service.populateProfileResult(org.mockito.ArgumentMatchers.any())).thenCallRealMethod();

        JsonNode data = json(controller.getProfile("nobody", null, null, null).getData());

        assertEquals(List.of("source", "clearanceLevel", "level", "levelName", "linkedWorkstation",
                "auditMode", "sandboxMandatory"), keys(data));
        assertEquals("global_default", data.path("source").asText());
    }

    @Test
    @DisplayName("分页：4 键（data/total/page/pageSize）")
    void pageShape() {
        when(service.queryAllUserProfiles()).thenReturn(List.of(profile()));
        when(service.queryAllRoleProfiles()).thenReturn(List.of());

        JsonNode data = json(controller.list(null, 1, 20, null, null).getData());

        assertEquals(List.of("data", "total", "page", "pageSize"), keys(data));
        assertEquals(1, data.path("total").asInt());
    }

    @Test
    @DisplayName("删除回执：2 键（success/id）")
    void deleteShape() {
        when(service.queryUserProfile("p-1")).thenReturn(profile());
        when(service.deleteUserProfile("p-1")).thenReturn(1);

        JsonNode data = json(controller.delete("p-1").getData());

        assertEquals(List.of("success", "id"), keys(data));
        assertEquals(true, data.path("success").asBoolean());
    }
}
