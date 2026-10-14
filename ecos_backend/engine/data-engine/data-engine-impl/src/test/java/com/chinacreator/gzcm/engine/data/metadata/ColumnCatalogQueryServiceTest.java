package com.chinacreator.gzcm.engine.data.metadata;

import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.data.dao.ColumnCatalogDao;
import com.chinacreator.gzcm.engine.data.model.MetadataColumnsVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-3 差距补齐单测：跨引擎列元数据供数（{@code GET /api/v1/datanet/metadata/columns}）的解析层。
 *
 * <p>本测试锁的是<b>会真的出错的四件事</b>，不是形式覆盖：
 * <ol>
 *   <li>schema 封闭清单（ST07 + ecos_dw）——越界必须拒，否则只读元数据端点变全库枚举面；</li>
 *   <li>标识符白名单——注入形态必须拒；</li>
 *   <li>裸表名择一优先级——同名表跨 schema 时必须命中 ecos_dw 且回带 resolvedTable；</li>
 *   <li>「表不存在」与「依赖故障」的语义分离——前者 200 + exists=false，不得抛异常。</li>
 * </ol>
 * </p>
 */
class ColumnCatalogQueryServiceTest {

    private final ColumnCatalogDao dao = mock(ColumnCatalogDao.class);
    private final ColumnCatalogQueryService service = new ColumnCatalogQueryService(dao);

    @Test
    @DisplayName("限定名 tableRef：直接按 (schema, table) 取列，resolvedTable 回带限定名")
    void qualifiedTableRefReturnsColumns() {
        when(dao.columnNames("ecos_dw", "ecos_application_forecast_run"))
                .thenReturn(List.of("run_id", "amount", "as_of_time"));

        MetadataColumnsVO vo = service.query("ecos_dw.ecos_application_forecast_run");

        assertTrue(vo.exists());
        assertEquals("ecos_dw.ecos_application_forecast_run", vo.resolvedTable());
        assertEquals(List.of("run_id", "amount", "as_of_time"), vo.columns());
        // 限定名不得再走裸表名的 schema 探测
        verify(dao, never()).schemasHolding(any());
    }

    @Test
    @DisplayName("裸表名：同名表落多 schema 时按优先级命中 ecos_dw（不是 public）")
    void bareTableRefFollowsSchemaPrecedence() {
        when(dao.schemasHolding("stage_fact")).thenReturn(List.of("public", "ecos_dw"));
        when(dao.columnNames("ecos_dw", "stage_fact")).thenReturn(List.of("fact_id"));

        MetadataColumnsVO vo = service.query("stage_fact");

        assertTrue(vo.exists());
        assertEquals("ecos_dw.stage_fact", vo.resolvedTable());
        assertEquals(List.of("fact_id"), vo.columns());
        verify(dao, never()).columnNames(eq("public"), any());
    }

    @Test
    @DisplayName("裸表名：清单外 schema 的同名表不得被命中（只认 ST07+ecos_dw）")
    void bareTableRefIgnoresSchemasOutsideAllowlist() {
        when(dao.schemasHolding("orphan_table")).thenReturn(List.of("ecos_legacy_warehouse"));

        MetadataColumnsVO vo = service.query("orphan_table");

        assertFalse(vo.exists());
        assertNull(vo.resolvedTable());
        assertTrue(vo.columns().isEmpty());
    }

    @Test
    @DisplayName("schema 越界（pg_catalog）→ ValidationException，禁系统目录枚举")
    void disallowedSchemaRejected() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.query("pg_catalog.pg_user"));
        assertTrue(ex.getMessage().contains("schema"), ex.getMessage());
        verify(dao, never()).columnNames(any(), any());
    }

    @Test
    @DisplayName("非法标识符 / 段数超限 / 空入参 → ValidationException")
    void malformedTableRefRejected() {
        assertThrows(ValidationException.class, () -> service.query("ecos_dw.t; DROP TABLE x"));
        assertThrows(ValidationException.class, () -> service.query("a.b.c"));
        assertThrows(ValidationException.class, () -> service.query("  "));
        assertThrows(ValidationException.class, () -> service.query(null));
        assertThrows(ValidationException.class, () -> service.query("1abc"));
    }

    @Test
    @DisplayName("表不存在 = exists=false + 空列集（HTTP 200 语义），不抛依赖故障")
    void missingTableYieldsNotExistsInsteadOfError() {
        when(dao.columnNames("ecos_dw", "no_such_table")).thenReturn(List.of());

        MetadataColumnsVO vo = service.query("ecos_dw.no_such_table");

        assertFalse(vo.exists());
        assertNull(vo.resolvedTable());
        assertEquals(List.of(), vo.columns());
    }

    @Test
    @DisplayName("大写形态 tableRef 自动重试小写（PG 未加引号 DDL 落库即小写）")
    void uppercaseTableRefFallsBackToLowerCase() {
        when(dao.columnNames("ecos_dw", "STAGE_FACT")).thenReturn(List.of());
        when(dao.columnNames("ecos_dw", "stage_fact")).thenReturn(List.of("fact_id"));

        MetadataColumnsVO vo = service.query("ECOS_DW.STAGE_FACT");

        assertTrue(vo.exists());
        assertEquals("ecos_dw.stage_fact", vo.resolvedTable());
    }
}
