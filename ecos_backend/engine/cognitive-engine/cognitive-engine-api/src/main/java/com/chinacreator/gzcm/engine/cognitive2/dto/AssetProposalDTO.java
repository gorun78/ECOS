package com.chinacreator.gzcm.engine.cognitive2.dto;

import lombok.Data;

/**
 * 认知资产变更候选请求 DTO（《认知域与智能域补充详细设计 v1.0》§13
 * {@code POST /api/v1/cognitive/assets/{assetId}/proposals}）。
 *
 * <p><b>SUP-COG-03 语义</b>：候选只创建候选 / 审计留痕，<b>不自动升格生产资产</b>——
 * 批准升格必须另行走受控状态迁移（操作者 + 证据引用 + 审计 traceId）。
 * {@code targetState} 词表单源 = cognitive-engine-api {@code CognitiveAssetStatus}，
 * 非法值 / 非法迁移 ⇒ 400 / 409（原资产行 0 改动）。</p>
 */
@Data
public class AssetProposalDTO {

    /** 目标治理状态（必填；CognitiveAssetStatus 词表，如 ACTIVE / SUSPENDED） */
    private String targetState;

    /** 变更原因（必填；留痕审计） */
    private String reason;

    /** 证据引用（可空；evidence id / 计算引用 / 运行快照引用） */
    private String evidenceRef;

    /** 提出者身份（可空，缺省 system） */
    private String operator;

    /** 备注（可空；只入候选审计事件 payload，禁带 Prompt 原文） */
    private String note;
}
