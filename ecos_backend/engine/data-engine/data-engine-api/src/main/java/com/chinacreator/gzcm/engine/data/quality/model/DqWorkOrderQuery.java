package com.chinacreator.gzcm.engine.data.quality.model;

import lombok.Data;

/**
 * DQ 工单列表查询条件 — REST 列表端点入参。
 * <p>全部字段可选，null/空串 表示不过滤；pageNum/pageSize 缺省 1/20。</p>
 *
 * @author PMO-48-C T12
 */
@Data
public class DqWorkOrderQuery {

    /** 工单状态: PENDING / ASSIGNED / IN_WORK / RESOLVED / VERIFIED / CLOSED / REJECTED */
    private String status;

    /** 处理模式: MANUAL / AUTO_REPAIR / EXEMPT */
    private String handlingMode;

    /** 严重级别: CRITICAL / HIGH / MEDIUM / LOW */
    private String severity;

    /** 资产 ID 精确匹配 */
    private String assetId;

    /** 规则 ID 精确匹配 */
    private String ruleId;

    /** 关键词（title / order_no 模糊匹配） */
    private String keyword;

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页大小 */
    private Integer pageSize = 20;
}
