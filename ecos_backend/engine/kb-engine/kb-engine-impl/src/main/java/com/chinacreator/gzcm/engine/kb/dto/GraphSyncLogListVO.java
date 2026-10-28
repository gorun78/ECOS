package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 同步日志分页 — 附录 A 第 15 条（{@code GET /api/v1/knowledge/graph/sync/logs}，出参 {@code {items,total,page,size}}）。
 */
@Data
public class GraphSyncLogListVO {

    /** 日志条目（{@code create_time} 倒序）。 */
    private List<GraphSyncLogEntryVO> items = new ArrayList<>();

    /** 台账总行数。 */
    private long total;

    /** 当前页码（1-based）。 */
    private int page;

    /** 每页条数（1~200）。 */
    private int size;
}
