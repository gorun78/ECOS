package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PipelineServiceImplTest — CRUD / ABAC / 分页约束（Wave 4 T1，不连 PG）。
 *
 * <p>PipelineServiceImpl 基于 JdbcTemplate（PipelineRepository），本测试 mock
 * Repository 与 SecurityService，验证业务门面分支：
 * <ul>
 *   <li>updateDefinition 定义不存在 → NotFoundException</li>
 *   <li>deleteDefinition 逻辑删除 + 审计（架构铁律 §2.4 第 5 条）</li>
 *   <li>checkAbacBeforeExecute security DENY → BusinessException(403)（§2.4 第 6 条）</li>
 *   <li>listExecutions 页大小上限 200 / 页参数下限 1（SQL 规范 §六）</li>
 * </ul>
 *
 * <p>需真实 PG 的端到端 CRUD（INSERT/UPDATE 回填、schedule 注册等）由
 * {@code ecos-tests/data-workbench-pipeline-smoke.mjs} 覆盖（见 Wave 4 T1 说明）。
 */
@ExtendWith(MockitoExtension.class)
class PipelineServiceImplTest {

    @Mock
    private PipelineRepository repository;

    @Mock
    private PipelineSecurityService securityService;

    private PipelineServiceImpl service() {
        return new PipelineServiceImpl(repository, securityService);
    }

    private PipelineDefinition def(String id, String name, String status) {
        PipelineDefinition d = new PipelineDefinition();
        d.setId(id);
        d.setName(name);
        d.setStatus(status);
        return d;
    }

    @Test
    @DisplayName("updateDefinition — 定义不存在 → NotFoundException")
    void updateDefinitionNotFound() {
        when(repository.definitionExists("ghost")).thenReturn(false);
        PipelineSaveDTO dto = new PipelineSaveDTO();
        dto.setName("renamed");
        assertThrows(NotFoundException.class, () -> service().updateDefinition("ghost", dto));
    }

    @Test
    @DisplayName("updateDefinition — 重命名成功且触发审计（写操作异步审计铁律 §2.4 ⑤）")
    void updateDefinitionSuccessAudited() {
        when(repository.definitionExists("p-1")).thenReturn(true);
        when(repository.updateDefinition("p-1", "renamed", null, null))
                .thenReturn(def("p-1", "renamed", "DRAFT"));

        PipelineSaveDTO dto = new PipelineSaveDTO();
        dto.setName("renamed");
        PipelineDefinition updated = service().updateDefinition("p-1", dto);

        assertEquals("p-1", updated.getId());
        assertEquals("renamed", updated.getName());
        verify(securityService).auditWrite("PIPELINE_UPDATE", "p-1", "system");
    }

    @Test
    @DisplayName("deleteDefinition — 逻辑删除（status→ARCHIVED）+ 审计（架构铁律 §4.8.4）")
    void deleteDefinitionCallsLogicalDelete() {
        when(repository.definitionExists("p-1")).thenReturn(true);

        service().deleteDefinition("p-1");

        // Repository.deleteDefinition = UPDATE status='ARCHIVED'（逻辑删除语义）
        verify(repository).deleteDefinition("p-1");
        verify(repository).deleteNodesByDefinitionId("p-1");
        verify(securityService).auditWrite("PIPELINE_DELETE", "p-1", "system");
    }

    @Test
    @DisplayName("checkAbacBeforeExecute — security DENY → BusinessException errorCode=403（§2.4 ⑥ 默认 DENY 可达）")
    void abacDenyBlocksExecution() {
        when(securityService.evaluateExecute("p-1"))
                .thenReturn(PipelineSecurityService.AllowedResult.deny("DENY_BY_POLICY"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().checkAbacBeforeExecute("p-1"));
        assertEquals(403, ex.getErrorCode(),
                "ABAC DENY 必须以 403 业务码透出，实际 errorCode=" + ex.getErrorCode());
        assertTrue(ex.getMessage().contains("POLICY") || ex.getMessage().contains("DENY_BY_POLICY"),
                "message 应含拒绝原因，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("checkAbacBeforeExecute — ALLOW 不抛异常")
    void abacAllowPasses() {
        when(securityService.evaluateExecute("p-1"))
                .thenReturn(PipelineSecurityService.AllowedResult.allow("ALLOW"));
        service().checkAbacBeforeExecute("p-1");
    }

    @Test
    @DisplayName("listExecutions — pageSize 上限被截到 200（SQL 规范 §六 防滥用）")
    void listExecutionsPageSizeCappedAt200() {
        when(repository.countExecutionsByDefinitionId("p-1")).thenReturn(0L);
        PipelineExecutionPageVO ret = service().listExecutions("p-1", 1, 999);
        assertEquals(200, ret.getPageSize());
        assertEquals(1, ret.getPage());
    }

    @Test
    @DisplayName("listExecutions — page < 1 自动纠正为 1（下边界）")
    void listExecutionsPageFlooredAt1() {
        when(repository.countExecutionsByDefinitionId("p-1")).thenReturn(0L);
        PipelineExecutionPageVO ret = service().listExecutions("p-1", 0, 10);
        assertEquals(1, ret.getPage());
    }

    @Test
    @DisplayName("getDefinition — 定义不存在 → NotFoundException")
    void getDefinitionNotFound() {
        when(repository.findDefinitionById("ghost")).thenReturn(null);
        assertThrows(NotFoundException.class, () -> service().getDefinition("ghost"));
    }
}
