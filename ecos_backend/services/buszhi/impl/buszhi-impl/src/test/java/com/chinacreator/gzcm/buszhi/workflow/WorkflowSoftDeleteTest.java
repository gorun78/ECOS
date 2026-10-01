package com.chinacreator.gzcm.buszhi.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chinacreator.gzcm.buszhi.workflow.dao.WorkflowDao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * W86/O-18 软删守卫（《详细设计-03-本体域》W86）。
 *
 * <p>断言：{@code WorkflowRepository.deleteById（门面 → WorkflowDao 委托）} 走
 * {@code {schema}.ecos_workflow} 软删
 * （{@code is_deleted=1 + update_time=NOW() + update_by=?}），**禁物理 DELETE**；
 * schema 限定到 {@code ecos_ontology}（默认值）。</p>
 *
 * <p>2026-10 B5：数据访问下沉 {@code ..dao..}（架构铁律 §2.5 #1 / IR06），本守卫对
 * {@link WorkflowDao}（门面 {@code WorkflowRepository} 的 JDBC 委托目标）直接断言软删语义。</p>
 */
class WorkflowSoftDeleteTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WorkflowDao repository = new WorkflowDao(jdbc, "ecos_ontology");

    @AfterEach
    void clearActor() {
        WorkflowActorContext.clear();
    }

    @Test
    void deleteById_emits_soft_delete_update_to_ecos_ontology_workflow() {
        WorkflowActorContext.setActor("admin");
        repository.deleteById("wf-1");

        // deleteById -> jdbc.update(sql, actor, id)  = 2 varargs
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> actorCap = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> idCap = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(sqlCap.capture(), actorCap.capture(), idCap.capture());

        String sql = sqlCap.getValue();
        // 软删 UPDATE 命中控制域主表（ecos_ontology.ecos_workflow）
        assertTrue(sql.matches(
            "UPDATE\\s+ecos_ontology\\.ecos_workflow\\s+SET\\s+is_deleted=1,\\s*update_time=NOW\\(\\),\\s*update_by=\\?\\s+WHERE\\s+id=\\?"),
            "删除应改写软删 UPDATE 命中控制域主表，实际 SQL=" + sql);
        // 禁物理 DELETE
        assertFalse(sql.toUpperCase().contains("DELETE FROM"), "禁物理 DELETE，实际 SQL=" + sql);
        // 审计列 = 当前操作人，目标 id 透传
        assertEqualsRole("admin", actorCap.getValue().toString());
        assertEqualsRole("wf-1", idCap.getValue().toString());
    }

    @Test
    void deleteById_falls_back_to_system_when_no_actor() {
        repository.deleteById("wf-2");
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> actorCap = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> idCap = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(sqlCap.capture(), actorCap.capture(), idCap.capture());
        assertEqualsRole("system", actorCap.getValue().toString());
    }

    private static void assertEqualsRole(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("actor 应回退 system，实际=" + actual);
        }
    }
}
