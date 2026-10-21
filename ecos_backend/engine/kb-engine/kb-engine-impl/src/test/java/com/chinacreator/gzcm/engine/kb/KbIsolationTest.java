package com.chinacreator.gzcm.engine.kb;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.common.security.header.ServiceContext;
import com.chinacreator.gzcm.engine.kb.controller.KbAssumptionController;
import com.chinacreator.gzcm.engine.kb.controller.KbProfileController;
import com.chinacreator.gzcm.engine.kb.controller.NavTaxonController;
import com.chinacreator.gzcm.engine.kb.dao.NavTaxonDao;
import com.chinacreator.gzcm.engine.kb.nav.model.NavCategorySaveDTO;
import com.chinacreator.gzcm.engine.kb.security.KnowledgeNavSecurityEngineClient;
import com.chinacreator.gzcm.engine.kb.service.nav.NavLlmRecommendService;
import com.chinacreator.gzcm.engine.kb.service.nav.NavTaxonServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F04-16 隔离/可归因写 验收测试。
 *
 * <p>R2 / B2-kb: 数据访问已由 {@code JdbcTemplate} 收口到 {@code NavTaxonDao}（见
 * {@code engine.kb.dao} 底座）。本测试改为 mock {@code NavTaxonDao}，验证 service 层
 * 把「真实 actor / domain」正确传递给数据访问落点（归因链路的 service 侧职责）；
 * 具体 SQL 文本（{@code kb_nav_category} / {@code c.domain = ?} 等值过滤）断言随 SQL
 * 一并下沉到 DAO 层，此处不再重复。
 *
 * <ol>
 *   <li>{@link #writeUsesRealActorAndDomain()} — 写操作 create_by/update_by 绑定
 *       ServiceContext 的真实 userId（非硬编码 "current-user"）；</li>
 *   <li>{@link #crossDomainReadIsDenied()} — 列表读按 domain 等值过滤，跨域行天然不可见；</li>
 *   <li>{@link #everyWriteEndpointHasPermissionAnnotation()} — 指定写端点均有
 *       {@code @RequirePermission} 注解（GET 读不要求）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class KbIsolationTest {

    @Mock private NavTaxonDao dao;
    @Mock private KnowledgeNavSecurityEngineClient securityClient;
    @Mock private NavLlmRecommendService llmRecommendService;

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    private NavTaxonServiceImpl buildService() {
        // ABAC 放行（测试聚焦写入归因，而非 ABAC 裁决）
        lenient().when(securityClient.evaluatePolicy(anyString(), any()))
                .thenReturn(AbacEvaluateResponseContract.Verdict.ALLOW);
        return new NavTaxonServiceImpl(dao, securityClient, llmRecommendService);
    }

    @Test
    @DisplayName("F04-16 ①：写入 create_by/update_by 绑真实 ServiceContext userId，非 'current-user'")
    void writeUsesRealActorAndDomain() {
        // 注入跨 JVM 身份：userId=u-42, tenantId=t-1, domain=finance
        ServiceContext.set(ServiceContext.of("trace-1", "u-42", "knowledge_admin",
                "t-1", "finance", "kb-engine"));

        NavTaxonServiceImpl service = buildService();

        NavCategorySaveDTO dto = new NavCategorySaveDTO();
        dto.setDomain("finance");
        dto.setName("还款策略");

        // 回读既有行（createCategory 末尾 findByIdRequired）
        Map<String, Object> existing = new HashMap<>();
        existing.put("id", "cat-1");
        existing.put("domain", "finance");
        existing.put("name", "还款策略");
        existing.put("path", "/还款策略");
        existing.put("level", 1);
        existing.put("sort_order", 0);
        existing.put("parent_id", null);
        lenient().when(dao.fetchCategoryByNameRequired(eq("finance"), eq("还款策略")))
                .thenReturn(existing);

        service.createCategory(dto);

        // 捕获 DAO insertCategory 入参（actor 两次：create_by + update_by 同一 actor）
        ArgumentCaptor<String> actorCaptor = ArgumentCaptor.forClass(String.class);
        verify(dao).insertCategory(eq("finance"), isNull(), eq("还款策略"),
                anyString(), anyInt(), anyInt(), actorCaptor.capture());

        String actor = actorCaptor.getValue();
        assertTrue("u-42".equals(actor),
                "create_by/update_by 应绑定真实 userId u-42，实际=" + actor);
        assertTrue(!"current-user".equals(actor),
                "禁止硬编码 'current-user'");
    }

    @Test
    @DisplayName("F04-16 ②：列表读按 domain 等值过滤 — 跨域行不可见")
    void crossDomainReadIsDenied() {
        NavTaxonServiceImpl service = buildService();

        lenient().when(dao.listCategories(anyString(), any(), any())).thenReturn(List.of());

        service.listCategories("finance", null, null);

        // service 把归一化后的 domain（finance）原样透传给 DAO（跨域隔离的入口约束）
        ArgumentCaptor<String> domainCaptor = ArgumentCaptor.forClass(String.class);
        verify(dao).listCategories(domainCaptor.capture(), isNull(), isNull());
        assertTrue("finance".equals(domainCaptor.getValue()),
                "读列表必须按 domain 等值过滤（跨域隔离）; domain=" + domainCaptor.getValue());
    }

    @Test
    @DisplayName("F04-16 ③：指定写端点均带 @RequirePermission（GET 读除外）")
    void everyWriteEndpointHasPermissionAnnotation() {
        // NavTaxonController 写端点
        assertAnnotated(NavTaxonController.class, Set.of(
                "createCategory", "updateCategory", "deleteCategory", "moveCategories",
                "createTag", "deleteTag",
                "setArticleCategories", "setArticleTags",
                "addArticleCategories", "addArticleTags", "removeArticleCategories",
                "undoArticle"));

        // KbProfileController#publish（+ generate）
        assertAnnotated(KbProfileController.class, Set.of("publish", "generate"));

        // KbAssumptionController create/submit/approve
        assertAnnotated(KbAssumptionController.class, Set.of("create", "submit", "approve"));
    }

    /** 断言 clazz 上指定方法名均带 @RequirePermission（值 = permission 以 knowledge: 前缀）。 */
    private static void assertAnnotated(Class<?> clazz, Set<String> methodNames) {
        Map<String, Method> methods = new HashMap<>();
        for (Method m : clazz.getDeclaredMethods()) {
            methods.put(m.getName(), m);
        }
        for (String name : methodNames) {
            Method m = methods.get(name);
            assertNotNull(m, clazz.getSimpleName() + " 未找到方法 " + name);
            RequirePermission rp = m.getAnnotation(RequirePermission.class);
            if (rp == null) {
                fail(clazz.getSimpleName() + "#" + name + " 缺少 @RequirePermission（写端点须声明权限）");
            }
            assertTrue(rp.permission() != null && !rp.permission().isBlank(),
                    clazz.getSimpleName() + "#" + name + " @RequirePermission.permission 非空");
            assertTrue(rp.permission().startsWith("knowledge:"),
                    clazz.getSimpleName() + "#" + name + " 权限码以 knowledge: 前缀，实际=" + rp.permission());
        }
    }
}
