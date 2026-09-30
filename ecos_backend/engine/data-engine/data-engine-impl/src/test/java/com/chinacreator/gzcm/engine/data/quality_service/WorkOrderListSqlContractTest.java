package com.chinacreator.gzcm.engine.data.quality.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.chinacreator.gzcm.engine.data.quality.DqKnowledgeSinkService;
import com.chinacreator.gzcm.engine.data.quality.DqRcaService;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * F02-08 规格 2 验收（WorkOrderListSqlContractTest，纯单测无 DB）：
 * 修 {@code DqWorkOrderServiceImpl.listWorkOrders} 分页 SELECT 缺
 * {@code FROM ecos_dq.dq_work_order}（MC06/ST07 表名 schema 限定，
 * 与同方法 count 查询的表限定写法对齐）。
 *
 * <p>断言：分页 executed-SQL 模板（不区分大小写）包含
 * {@code FROM ecos_dq.dq_work_order} + {@code WHERE}（至少 is_deleted 最小集）
 * + {@code ORDER BY}（分页语义保留）+ {@code LIMIT}/ {@code OFFSET}。</p>
 *
 * <p>设计文档规格里"并跑真实查询"部分由 data-engine-impl 既有
 * {@code DqPhase4E2eIT} 层级的 DB IT 承接；本单测只锁 SQL 字符串契约
 * （隔离缺陷的最小复现面，禁 DB 依赖）。</p>
 */
class WorkOrderListSqlContractTest {

    private static final String QUALIFIED_TABLE = "ecos_dq.dq_work_order";

    @Test
    void listWorkOrders_pagedSelectContainsFromSchemaQualifiedTable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // 最小 count 返回（任何值都行；不是本用例断言重点）
        when(jdbc.queryForObject(
                org.mockito.ArgumentMatchers.startsWith("SELECT COUNT(*)"),
                eq(Long.class),
                org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(0L);
        when(jdbc.query(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<DqWorkOrderVO>>any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new ArrayList<DqWorkOrderVO>());

        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> jdbcProvider = mock(ObjectProvider.class);
        when(jdbcProvider.getIfAvailable()).thenReturn(jdbc);
        @SuppressWarnings("unchecked")
        ObjectProvider<DqKnowledgeSinkService> sinkProvider = mock(ObjectProvider.class);
        when(sinkProvider.getIfAvailable()).thenReturn(null);

        DqWorkOrderServiceImpl svc = new DqWorkOrderServiceImpl(
                mock(DqSecurityService.class), mock(DqRcaService.class), jdbcProvider, sinkProvider);

        PageResult<DqWorkOrderVO> page = svc.listWorkOrders(new DqWorkOrderQuery());
        assertNotNull(page);

        // 捕获 paged SELECT 实际 SQL
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<DqWorkOrderVO>>any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());

        String sql = sqlCaptor.getValue();
        String norm = sql.toLowerCase();
        assertTrue(norm.contains("from " + QUALIFIED_TABLE.toLowerCase()),
                "listWorkOrders 的 paged SELECT 必须含 FROM " + QUALIFIED_TABLE
                        + "（F02-08 规格 2）— 实际 SQL：" + sql);
        assertTrue(norm.contains(" where "),
                "paged SELECT 必须含 WHERE 子句（至少 is_deleted = FALSE 最小集）— 实际 SQL：" + sql);
        assertTrue(norm.contains(" where ") && norm.contains("is_deleted"),
                "WHERE 应至少包含 is_deleted = FALSE 基础过滤 — 实际 SQL：" + sql);
        assertTrue(norm.contains("order by"),
                "paged SELECT 必须保留 ORDER BY（分页语义）— 实际 SQL：" + sql);
        assertTrue(norm.contains("limit") && norm.contains("offset"),
                "paged SELECT 必须是 LIMIT ? OFFSET ? 分页形式 — 实际 SQL：" + sql);
    }
}
