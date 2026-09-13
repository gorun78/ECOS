package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 与前一版本 diff VO — T16-4 强类型返回容器。
 *
 * <p>对齐 {@code OntologyVersionSimpleController.diffWithPrevious}
 * → {@code OntologyVersionService.diffWithPrevious(id)} 既有 Map 输出契约：
 * {@code currentVersion / currentSnapshot / previousVersion / previousSnapshot}。
 *
 * <p>T16-4: 版本 diff 的 JSONB 快照差异动态结构豁免 —
 * {@code currentSnapshot / previousSnapshot} 为动态 JSON 对象（{@link Object}），
 * 与既有 {@code safeParseJson} 行为一致。{@code previousVersion / previousSnapshot}
 * 在前一版本不存在时置 null（NON_NULL 省略，与既有 Map put null 即前端 undefined 行为一致）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyVersionPreviousDiffVO {

    /** 当前版本号 */
    private String currentVersion;

    /** 当前版本快照（动态 JSON，T16-4: diff JSONB 快照动态结构豁免） */
    private Object currentSnapshot;

    /** 前一版本号（不存在时 null） */
    private String previousVersion;

    /** 前一版本快照（动态 JSON，不存在时 null，T16-4: diff JSONB 快照动态结构豁免） */
    private Object previousSnapshot;
}
