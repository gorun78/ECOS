package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档接入记录列表 — 详设-07 §7.3.2 第 11 行组 B
 * （{@code GET /api/v1/knowledge/ingest/documents}，信封按 §7.3.1 分页形态
 * {@code {items,total,page,size}}，默认 50 上限 500）。
 */
@Data
public class KnowledgeDocumentIngestListVO {

    /** 当页记录（{@code updated_at} 倒序）。 */
    private List<KnowledgeDocumentIngestRowVO> items = new ArrayList<>();

    /** 满足过滤条件的总行数（与 items 同谓词口径）。 */
    private long total;

    /** 当前页码（1 起，越界钳制后回显）。 */
    private int page;

    /** 页大小（钳制到 [1,500] 后回显）。 */
    private int size;

    /** 状态过滤回显（null/空 = 全部状态；真谓词见 {@code KbDocMapper.listByStatus}）。 */
    private String status;
}
