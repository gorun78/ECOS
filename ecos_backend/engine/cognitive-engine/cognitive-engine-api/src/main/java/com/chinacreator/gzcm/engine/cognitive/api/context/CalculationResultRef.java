package com.chinacreator.gzcm.engine.cognitive.api.context;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 确定性计算产物引用契约（《认知域与智能域补充详细设计 v1.0》§4.2 / 附录 A CalculationResultRef /
 * REQ-COG-09）。
 *
 * <p>"指向确定性计算结果及其输入快照/口径版本的引用"。数值来源校验锚点：
 * {@code deterministic=true} 时必须同时给出 {@code inputSnapshotRef} 与 {@code outputRef}，
 * 缺任一即视为非法引用（LLM 不得直接产出确定性数值，§0.3 红线 6；
 * 本类仅作契约校验，裁决在 Context Builder 与服务层）。</p>
 *
 * <p>引用态契约：计算产物本体落业务域 {@code ecos_dw}（铁律 §0.6 / ADR-14），
 * 认知域只保留引用，<b>不复制数值正文</b>。</p>
 */
@Data
public class CalculationResultRef {

    /** 是否确定性计算产物（true = 数值可复算；false 禁止用于业务结论数值）。 */
    private boolean deterministic;

    /** 计算任务/运行稳定 ID（必填）。 */
    private String calculationId;

    /** 输入快照引用（必填；outcome 偏差对比与回放锚点，§9.2-1）。 */
    private String inputSnapshotRef;

    /** 计算结果存储引用（必填；结果落业务域/MinIO，本引用不携带正文）。 */
    private String outputRef;

    /** 计算参数/口径版本引用（可选但强烈建议：caliberId + 版本）。 */
    private String parametersRef;

    /** 计算完成时点（ISO-8601 UTC；可选，缺省由来源系统补记）。 */
    private String calculationTime;

    /** 支撑该计算结果的证据引用（可选；与 {@link EvidenceRef} 同校验纪律）。 */
    private List<EvidenceRef> evidenceRefs = new ArrayList<>();

    /**
     * 契约校验：返回拒绝原因列表（空 = 合法）。
     * 规则：calculationId 必填；{@code deterministic=true} ⇒ inputSnapshotRef / outputRef 必填。
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (isBlank(calculationId)) problems.add("calculationId");
        if (deterministic) {
            if (isBlank(inputSnapshotRef)) problems.add("inputSnapshotRef");
            if (isBlank(outputRef)) problems.add("outputRef");
        }
        return problems;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
