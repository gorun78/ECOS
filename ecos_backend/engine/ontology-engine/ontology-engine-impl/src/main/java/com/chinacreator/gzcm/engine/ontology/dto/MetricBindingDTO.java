package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * F03-01 — 指标口径绑定入参。
 *
 * <p>两个契约面共用本 DTO（#146u 追加契约体键 {@link #caliberId}，旧键一个未删）：</p>
 * <ul>
 *   <li>旧面 {@code POST /api/v1/ontology/calibers/metrics/{metricCode}/binding}：
 *       体用 {@link #caliberCode}（业务编码），由 Controller 经
 *       {@code CaliberService.findByCode} 折成 {@code caliber_id}；</li>
 *   <li>契约面 {@code POST /api/v1/ontology/metrics/{code}/binding}（《ECOS详细设计说明书-从零实施版》
 *       Vol-3 接口总表 {@code bindMetricCaliber}）：体用 {@link #caliberId}（口径主键），
 *       未给时回退到 {@link #caliberCode} 解析（{@code resolvedBy} 在回执里自证走了哪条腿）。</li>
 * </ul>
 *
 * <p>{@link #formulaVersion} 两面同义：口径版本号（{@code ecos_caliber_version.version_no}）。</p>
 */
@Data
public class MetricBindingDTO {
    /** 口径主键（{@code ecos_caliber.id}）——契约面 {@code /metrics/{code}/binding} 的体键。 */
    private String caliberId;
    /** 口径业务编码（{@code ecos_caliber.code}）——旧面 {@code /calibers/metrics/...} 的体键。 */
    private String caliberCode;
    /** 口径版本号（{@code ecos_caliber_version.version_no}）。 */
    private String formulaVersion;
}
