package com.chinacreator.gzcm.engine.data.model;

import java.util.List;

/**
 * 跨引擎列元数据出参（P0-3 差距补齐，2026-10-14）。
 *
 * <p>契约出处：详设《从零实施版》端点登记表
 * {@code GET /api/v1/datanet/metadata/columns | listColumnsByTableRef |
 * 跨引擎供数：本体域 V3 血缘校验取列 | L1 | tableRef 限定名 | 列集合}。
 * 消费方 = 本体引擎 {@code DatNetLineageGuard.RestLineageColumnFetcher}，
 * 其解析路径固定为 {@code $.data.columns}（必须为数组），
 * 因此本 VO 字段名即线上契约，<b>只增不改</b>。</p>
 *
 * <p>字段语义（{@code exists=false} 时 {@code resolvedTable=null}、{@code columns=[]}）：
 * 「表真实不存在」是数据事实，不是 datanet 依赖故障，故走 HTTP 200 空列集，
 * 由调用侧 V3 折算成「列缺失」门禁否决。</p>
 *
 * @param tableRef      原始入参回显（{@code schema.table} 或裸表名）
 * @param resolvedTable 实际命定的 {@code schema.table}；未命中为 {@code null}
 * @param exists        表是否真实存在
 * @param columns       列清单（物理列序）
 */
public record MetadataColumnsVO(
        String tableRef,
        String resolvedTable,
        boolean exists,
        List<String> columns) {
}
