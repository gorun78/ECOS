package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

/**
 * 数据资产 CRUD 入参 DTO（强类型 —— 后端规范 §4：禁 Map 入参/出参）。
 *
 * <p>新增/编辑资产时必填字段：</p>
 * <ul>
 *   <li>assetId -- 编辑时必填（新增时忽略，由服务生成 UUID）</li>
 *   <li>resourceId —— 必填（→ td_data_resource）</li>
 *   <li>assetName —— 必填（资产展示名）</li>
 * </ul>
 * 其他字段（businessDesc/owner/ownerOrg/dataGrain/categoryId/sensitivityLevel）均可选，
 * 编辑时 null = 不修改（COALESCE 语义）。
 *
 * @author DataBridge Datanet Team
 */
@Data
public class DataAssetSaveDTO {

    /** 资产 ID（编辑必填；新增忽略 —— 服务生成） */
    private String assetId;

    /** 物理资源 ID（必填；指向 td_data_resource.resource_id） */
    private String resourceId;

    /** 资产业务名（必填） */
    private String assetName;

    /** 业务说明（可选） */
    private String businessDesc;

    /** 责任人（可选） */
    private String owner;

    /** 组织（可选） */
    private String ownerOrg;

    /** 数据粒度（可选，自由文本，"用户级/订单级/审计级"等） */
    private String dataGrain;

    /** 业务分类 ID（可选；指向 ecos_data_category_tree.category_id） */
    private String categoryId;

    /** 敏感度（可选；L1/L2/L3/L4；缺省 L1） */
    private String sensitivityLevel;

    /** 业务域（多租户预留，缺省 "default"） */
    private String domain;

    /** 操作人（由拦截器填充，前端不强求） */
    private String operator;
}
