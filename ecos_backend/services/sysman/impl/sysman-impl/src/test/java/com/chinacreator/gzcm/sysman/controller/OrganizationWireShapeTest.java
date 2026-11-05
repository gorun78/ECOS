package com.chinacreator.gzcm.sysman.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.entity.Organization;
import com.chinacreator.gzcm.sysman.iam.service.IOrganizationService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 机构面 wire 键面保真门（#147CB · DoD DI-2 第七批）。
 *
 * <p><b>钉的是什么</b>：平铺 10 键（含 {@code createdTime} 仍为 LocalDateTime 渲染）；
 * 树节点仅在子集非空时追加 {@code children}（属性级 NON_NULL，未消费不得输出空数组）；
 * 建机构缺省 {@code orgType=DEPARTMENT}、缺码自动生成 {@code ORG_<millis>}；
 * 改机构把 4 个字段原样交给属主（不偷偷套默认）。</p>
 *
 * <p><b>变异自证</b>：把树节点 {@code children} 的 NON_NULL 去掉 ⇒ 叶子节点多出
 * {@code "children": null} 键红；把建机构默认值去掉 ⇒ 默认 orgType 用例红。</p>
 */
class OrganizationWireShapeTest {

    /** 与 Spring Boot 默认配置对齐：注册 JavaTime 模块并关闭时间戳形态（生产 mapper 同此）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final List<String> FLAT_KEYS = List.of("orgId", "orgName", "orgCode",
            "parentOrgId", "orgType", "description", "status", "remark", "path", "createdTime");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return MAPPER.convertValue(value, Map.class);
    }

    private static List<String> keysOf(Object value) {
        return new ArrayList<>(asMap(value).keySet());
    }

    private static Organization org(String id, Organization... children) {
        Organization o = new Organization();
        o.setOrgId(id);
        o.setOrgName("机构" + id);
        o.setOrgCode("CODE_" + id);
        o.setParentOrgId(null);
        o.setOrgType("DEPARTMENT");
        o.setDescription("d");
        o.setStatus("ACTIVE");
        o.setRemark("r");
        o.setPath("/root");
        o.setCreatedTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        o.setChildren(children.length == 0 ? null : List.of(children));
        return o;
    }

    @Test
    @DisplayName("平铺 10 键（键序）；createdTime 仍是 LocalDateTime 渲染")
    void flatKeysPreserved() throws Exception {
        IOrganizationService service = mock(IOrganizationService.class);
        when(service.listOrganizations(isNull(), isNull())).thenReturn(List.of(org("1")));
        OrganizationController controller = new OrganizationController(service);

        List<OrganizationController.OrganizationVO> data = controller.all().getData();
        assertEquals(FLAT_KEYS, keysOf(data.get(0)));
        assertEquals("2026-01-02T03:04:05", asMap(data.get(0)).get("createdTime"));
    }

    @Test
    @DisplayName("树节点：有子集时 children 在尾；叶子节点不得出现 children 键")
    void treeChildrenOnlyWhenPresent() throws Exception {
        IOrganizationService service = mock(IOrganizationService.class);
        Organization root = org("root", org("c1"), org("c2"));
        when(service.getOrganizationTree(any())).thenReturn(root);
        OrganizationController controller = new OrganizationController(service);

        OrganizationController.OrganizationTreeNodeVO tree = controller.tree(null).getData();
        List<String> rootKeys = keysOf(tree);
        List<String> expected = new ArrayList<>(FLAT_KEYS);
        expected.add("children");
        assertEquals(expected, rootKeys);
        assertEquals(2, tree.children().size());
        assertEquals(FLAT_KEYS, keysOf(tree.children().get(0)), "叶子节点不得多出 children 键");
    }

    @Test
    @DisplayName("建机构：缺省 orgType=DEPARTMENT、缺码自动生成 ORG_ 前缀；显式值原样透传")
    void createDefaultsAndPassthrough() throws Exception {
        IOrganizationService service = mock(IOrganizationService.class);
        when(service.createOrganization(anyString(), anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(org("new"));
        OrganizationController controller = new OrganizationController(service);

        OrganizationController.OrganizationRequest request = MAPPER.readValue(
                "{\"orgName\":\"新机构\"}", OrganizationController.OrganizationRequest.class);
        assertEquals(FLAT_KEYS, keysOf(controller.create(request).getData()));

        org.mockito.ArgumentCaptor<String> codeCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(service).createOrganization(eq("新机构"), codeCaptor.capture(), isNull(),
                eq("DEPARTMENT"), isNull(), eq("admin"));
        assertTrue(codeCaptor.getValue().startsWith("ORG_"), "缺码须自动生成 ORG_ 前缀");
    }

    @Test
    @DisplayName("改机构：4 字段原样交出（null 不套默认）、回执键面不变")
    void updatePassesFieldsVerbatim() throws Exception {
        IOrganizationService service = mock(IOrganizationService.class);
        when(service.updateOrganization(anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(org("1"));
        OrganizationController controller = new OrganizationController(service);

        OrganizationController.OrganizationRequest request = MAPPER.readValue(
                "{\"orgName\":\"改名\"}", OrganizationController.OrganizationRequest.class);
        assertEquals(FLAT_KEYS, keysOf(controller.update("1", request).getData()));

        verify(service).updateOrganization(eq("1"), eq("改名"), isNull(), isNull(), isNull(), eq("admin"));
        assertNotNull(controller.all());
    }

    @Test
    @DisplayName("空名仍 400（DTO 化不得丢校验）")
    void blankNameStillRejected() throws Exception {
        OrganizationController controller = new OrganizationController(mock(IOrganizationService.class));
        OrganizationController.OrganizationRequest request =
                MAPPER.readValue("{\"orgCode\":\"C\"}", OrganizationController.OrganizationRequest.class);
        ApiResponse<?> resp = controller.create(request);
        assertEquals(400, resp.getCode());
        assertNotNull(resp.getMessage());
    }
}
