package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 版本 diff 对比 VO — T16-4 强类型返回容器。
 *
 * <p>对齐 {@code VersionDiffController.diff} 既有 Map 输出契约（T11 前端支撑，
 * API 路径/参数/字段名严格不变）：
 * <ul>
 *   <li>正常分支（v1 != v2）：{@code version1 / version2 / snapshot1 / snapshot2}
 *       来自 {@code OntologyVersionService.diff}，再追加 {@code added / removed / modified}。</li>
 *   <li>空 diff 分支（v1 == v2）：{@code version1Id / version2Id} + 三空列表。</li>
 * </ul>
 * 两分支 key 命名历史不一致，本 VO 同时承载两组版本号字段（NON_NULL 按需输出）。
 *
 * <p>T16-4: 版本 diff 的 JSONB 快照差异动态结构豁免 —
 * {@code snapshot1 / snapshot2} 为动态 JSON 对象（{@link Object}）；
 * {@code added / removed / modified} 元素为 {@code {field, value[, newValue]}} 动态条目集合
 * （保持 {@code List<Object>}），与既有 Map 逐 key 比对行为等价。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyVersionDiffVO {

    /** 第一版本号（正常分支，service.diff 输出） */
    private String version1;

    /** 第二版本号（正常分支，service.diff 输出） */
    private String version2;

    /** 第一版本 ID（空 diff 分支输出） */
    private String version1Id;

    /** 第二版本 ID（空 diff 分支输出） */
    private String version2Id;

    /** 第一版本快照（动态 JSON，正常分支填充，T16-4: diff JSONB 快照动态结构豁免） */
    private Object snapshot1;

    /** 第二版本快照（动态 JSON，正常分支填充，T16-4: diff JSONB 快照动态结构豁免） */
    private Object snapshot2;

    /**
     * 新增字段条目（v2 有而 v1 无），元素 {field, value} 动态结构（T16-4 豁免）。
     */
    private List<Object> added;

    /**
     * 移除字段条目（v1 有而 v2 无），元素 {field, value} 动态结构（T16-4 豁免）。
     */
    private List<Object> removed;

    /**
     * 变更字段条目（值不同），元素 {field, value, newValue} 动态结构（T16-4 豁免）。
     */
    private List<Object> modified;
}
