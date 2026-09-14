package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体 Copilot 智能建模查询 Query。
 *
 * <p>{@code POST /api/v1/engine/ontology/copilot/{entity|relation|validate|import}}
 * 请求体字段：
 * <ul>
 *   <li>{@code prompt} — Copilot 自然语言提示（entity/relation 必填）</li>
 *   <li>{@code schemaInfo} — 数据源 schema 信息（可选，validate/import 使用）</li>
 * </ul>
 *
 * <p>T16-3 (2026-09-13)：跟随 T16-1/2 命名先例（入参 {@code XxxQuery}）。
 * 返回仍为动态 AI payload Map（ICopilotService 在 common-api 返回 Map，
 * 属动态结构豁免，不在此 VO 化）。
 */
@Data
public class OntologyCopilotQuery {

    /** Copilot 自然语言提示（entity/relation 必填，validate/import 可空） */
    private String prompt;

    /** 数据源 schema 信息（可选，供 LLM 上下文） */
    private String schemaInfo;
}
