package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.dao.RowLevelSecurityDao;
import com.chinacreator.gzcm.engine.security.decision.SubjectDepartmentResolutionService;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RlsControllerTest — 行级安全应用端点。
 *
 * <p>Wave-5.1 T-05：验证 tableName/userId 校验与 WHERE 子句 snippet 透传。
 * 使用真实 {@link RowLevelSecurityServiceImpl}，但 mock {@link RowLevelSecurityDao}
 * 的查询让它返回空策略，确保不连 PG。</p>
 */
class RlsControllerTest {

    private RlsController controller;

    @BeforeEach
    void setUp() {
        // 构造一个 mock DAO：查询直接抛异常，
        // RowLevelSecurityServiceImpl 内部 catch 后返回空策略 → condition=1=1
        RowLevelSecurityDao dao = mock(RowLevelSecurityDao.class);
        when(dao.findPoliciesForUser(anyString(), anyString()))
                .thenThrow(new RuntimeException("DB not available in unit test"));
        RowLevelSecurityServiceImpl rlsService = new RowLevelSecurityServiceImpl(dao);
        // 主体域真值：本组用例只验 snippet 透传，给一个固定 org 即可（判据腿由
        // RlsControllerSubjectDepartmentWiringTest 具名覆盖，不在此处重复）。
        SubjectDepartmentResolutionService subjectResolver = mock(SubjectDepartmentResolutionService.class);
        when(subjectResolver.resolvePrimaryOrgId(anyString())).thenReturn("ORG-TEST-1");
        this.controller = new RlsController(rlsService, subjectResolver);
    }

    @Test
    @DisplayName("POST /api/security/rls/apply — 正常返回 WHERE 子句 snippet")
    void applyReturnsWhereSnippet() {
        Map<String, Object> body = Map.of(
                "tableName", "td_user",
                "userId", "alice",
                "subject", "user"
        );
        ApiResponse<Map<String, Object>> resp = controller.apply(body);
        assertTrue(resp.isSuccess(), "apply 应 code=0");
        assertNotNull(resp.getData());
        // 空策略下返回 1=1
        assertEquals("1=1", resp.getData().get("condition"));
        assertTrue(resp.getData().containsKey("tableName"));
        assertEquals("td_user", resp.getData().get("tableName"));
    }

    @Test
    @DisplayName("POST /api/security/rls/apply — tableName 缺失应 400")
    void applyMissingTableNameReturns400() {
        Map<String, Object> body = Map.of("userId", "alice");
        ApiResponse<Map<String, Object>> resp = controller.apply(body);
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode());
        assertTrue(resp.getMessage().contains("tableName"));
    }

    @Test
    @DisplayName("POST /api/security/rls/apply — userId 缺失应 400")
    void applyMissingUserIdReturns400() {
        Map<String, Object> body = Map.of("tableName", "td_user");
        ApiResponse<Map<String, Object>> resp = controller.apply(body);
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode());
        assertTrue(resp.getMessage().contains("userId"));
    }
}
