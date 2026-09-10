package com.chinacreator.gzcm.engine.data.quality.model;

import lombok.Data;

/**
 * DQ 告警列表查询条件 — DqAlertController 列表端点入参。
 * <p>全部字段可选，null/空串 表示不过滤；pageNum/pageSize 缺省 1/20。</p>
 *
 * @author PMO-48-C T12
 */
@Data
public class DqAlertQuery {

    /** 告警级别: P0 / P1 / P2 / P3 */
    private String alertLevel;

    /** 告警状态: PENDING / NOTIFIED / ACKED / RESOLVED / IGNORED / ESCALATED */
    private String status;

    /** 规则 ID 精确匹配 */
    private String ruleId;

    /** 资产 ID 精确匹配 */
    private String assetId;

    /** 关键词（rule_name / message 模糊匹配） */
    private String keyword;

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页大小 */
    private Integer pageSize = 20;
}
