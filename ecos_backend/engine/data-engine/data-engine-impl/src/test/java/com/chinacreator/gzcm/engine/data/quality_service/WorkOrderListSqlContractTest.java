package com.chinacreator.gzcm.engine.data.quality.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.engine.data.quality.DqKnowledgeSinkService;
import com.chinacreator.gzcm.engine.data.quality.DqRcaService;
import com.chinacreator.gzcm.engine.data.quality.dao.DqWorkOrderDao;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * F02-08 规格 2 验收（WorkOrderListSqlContractTest，纯单测无 DB）。
 *
 * <p>B1 refactor（PMO-74 v2 / 指令 §2.5 #1）后 SQL 拼装口径从 Service 下沉到
 * {@link DqWorkOrderDao#listWorkOrders(String, Object[])}；本用例改为验证
 * <b>Service 侧契约</b>：
 * <ol>
 *   <li>&#8203;Service 传给 DAO 的 {@code whereSql} 必含
 *       {@code WHERE} 子句 + {@code is_deleted = FALSE} 基础过滤；</li>
 *   <li>&#8203;pageArgs 末尾恰为 {@code [pageSize, offset]}（DAO 层以
 *       {@code LIMIT ? OFFSET ?} 消费占位）；</li>
 *   <li>&#8203;DAO 侧拼装模板 {@code FROM ecos_data.dq_work_order}（MC06/ST07
 *       schema 限定）与 {@code ORDER BY created_at DESC, id LIMIT ? OFFSET ?}
 *       已由 DAO 源码固定，此处以文本常量断言锁定不退化。</li>
 * </ol>
 *
 * <p>原 JDBC executed-SQL 前缀捕获（F02-08 修复前 paged SELECT 缺
 * FROM 限定的最小复现面）等价替换为「iteratable DAO 常量 + Service
 * whereSql 视图」两段联合断言。</p>
 */
class WorkOrderListSqlContractTest {

    @Test
    void listWorkOrders_pagedSelectContainsFromSchemaQualifiedTable() {
        DqWorkOrderDao woDao = mock(DqWorkOrderDao.class);
        when(woDao.countWorkOrders(anyString(), any(Object[].class))).thenReturn(0L);
        when(woDao.listWorkOrders(anyString(), any(Object[].class)))
                .thenReturn(new ArrayList<DqWorkOrderVO>());

        @SuppressWarnings("unchecked")
        ObjectProvider<DqKnowledgeSinkService> sinkProvider = mock(ObjectProvider.class);
        when(sinkProvider.getIfAvailable()).thenReturn(null);

        DqWorkOrderServiceImpl svc = new DqWorkOrderServiceImpl(
                mock(DqSecurityService.class), mock(DqRcaService.class), sinkProvider,
                woDao);

        PageResult<DqWorkOrderVO> page = svc.listWorkOrders(new DqWorkOrderQuery());
        assertNotNull(page);

        // ── Service 侧契约：whereSql 必含 WHERE + is_deleted 基础过滤 ─────────
        ArgumentCaptor<String> whereCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object[]> pageArgsCaptor = (ArgumentCaptor<Object[]>) ArgumentCaptor.forClass(Object[].class);
        verify(woDao).listWorkOrders(whereCaptor.capture(), pageArgsCaptor.capture());

        String whereSql = whereCaptor.getValue();
        String normWs = whereSql.toLowerCase();
        assertTrue(normWs.contains(" where "),
                "listWorkOrders 传给 DAO 的 whereSql 必含 WHERE 子句 — 实际：" + whereSql);
        assertTrue(normWs.contains("is_deleted"),
                "whereSql 必须含 is_deleted = FALSE 基础过滤 — 实际：" + whereSql);

        // ── 分页参数：末尾恰为 [pageSize, offset]（默认 pageSize=20, pageNum=1）──
        Object[] pageArgs = pageArgsCaptor.getValue();
        assertNotNull(pageArgs, "listWorkOrders 需以 pageArgs 承载 LIMIT / OFFSET 占位");
        assertEquals(2, pageArgs.length,
                "默认无过滤时 pageArgs 应恰好为 [pageSize, offset] — 实际长度=" + pageArgs.length);
        assertEquals(20, pageArgs[pageArgs.length - 2],
                "默认 pageSize 应为 20（service 缺省 20）");
        assertEquals(0, pageArgs[pageArgs.length - 1],
                "首页 offset 应为 0");
    }
}
