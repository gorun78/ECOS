package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 动作执行/测试 VO — {@code POST /actions/{id}/test} 与 {@code POST /actions/{id}/execute}
 * 统一返回单元（结构不同，便于前端按字段渲染）。
 *
 * <p>字段合并 {@code OntologyActionService.testAction / executeAction} 输出。
 * <p>{@code executionSteps}(test) / {@code payload}(execute) 等动态键集合保留 Map，
 * 因为执行摘要步骤为字符串键，不适合静态强类型（保留现有 contract）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyActionResultVO {

    // ── 公共字段 ─────────────────────────────────────────
    /** Action 主键 */
    private String actionId;

    /** 动作编码 */
    private String code;

    /** 动作名称 */
    private String name;

    /** 动作类型 */
    private String actionType;

    /** 执行策略 */
    private String strategy;

    /** 状态（仅 execute 路径写入） */
    private String status;

    // ── test 路径字段 ─────────────────────────────────────
    /** 前置条件（JSON 解析后对象，仅 test） */
    private Object preconditions;

    /** 后置效果（JSON 解析后对象, 仅 test） */
    private Object effects;

    /** 模拟执行步骤（仅 test, 有序步骤字典，service 输出为 Map<String,Object>，这里按实际类型对齐） */
    private Map<String, Object> executionSteps;

    /** 测试结果（仅 test, "SUCCESS"） */
    private String testResult;

    // ── execute 路径字段 ──────────────────────────────────
    /** 实际执行时的占位执行入参（仅 execute, 透传） */
    private Map<String, Object> payload;

    /** 是否已执行（仅 execute, 占位为 true） */
    private Boolean executed;

    /** 执行摘要消息（仅 execute） */
    private String message;
}
