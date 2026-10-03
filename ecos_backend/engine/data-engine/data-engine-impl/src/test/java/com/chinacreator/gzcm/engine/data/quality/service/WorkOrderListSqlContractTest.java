package com.chinacreator.gzcm.engine.data.quality.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.chinacreator.gzcm.engine.data.quality.DqKnowledgeSinkService;
import com.chinacreator.gzcm.engine.data.quality.DqRcaService;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderQuery;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * F02-08-2 / W47（详细设计-02）工单列表 SQL 契约验收：
 * <ul>
 *   <li>{@code WorkOrderListSqlContractTest} —— D-6b 实证缺陷曾缺 {@code FROM ecos_dq.dq_work_order}
 *       子句导致 {@code BadSqlGrammarException} 被掩蔽成 404；本测试对
 *       {@code WO_COLUMNS + whereSql} 组装出的 SQL 做字符串契约断言，锁定该子句恒存在。</li>
 * </ul>
 * 判定性单测（Mockito 打桩 {@code ObjectProvider<JdbcTemplate>}，不触库）。
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderListSqlContractTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    @Mock
    private ObjectProvider<DqKnowledgeSinkService> knowledgeSinkProvider;
    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    @Test
    @DisplayName("WorkOrderListSqlContractTest — 列表 SQL 恒含 'FROM ecos_dq.dq_work_order'（消 D-6b 缺 FROM）")
    void listSqlContainsFromClause() {
        when(jdbcTemplateProvider.getIfAvailable()).thenReturn(jdbc);
        DqWorkOrderServiceImpl svc = new DqWorkOrderServiceImpl(
                mock(DqSecurityService.class), mock(DqRcaService.class),
                jdbcTemplateProvider, knowledgeSinkProvider);

        // count 查询 → 0 行；page 查询 → 空列表
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        svc.listWorkOrders(new DqWorkOrderQuery());

        // 捕获最后一次 jdbc.query（分页列表）的 SQL，断言 FROM 子句存在
        org.mockito.Mockito.verify(jdbc).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertNotNull(sql);
        assertTrue(sql.contains("FROM ecos_dq.dq_work_order"),
                "列表 SQL 必须含 'FROM ecos_dq.dq_work_order'（D-6b 回归护栏）; 实际 = " + sql);
    }
}
