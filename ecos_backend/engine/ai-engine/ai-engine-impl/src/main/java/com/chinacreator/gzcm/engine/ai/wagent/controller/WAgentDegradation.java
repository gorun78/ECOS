package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * W 域控制面"明确降级"出口单源（详细设计-10 §5.2 补债，无假真理 P0 纪律）。
 *
 * <p>本批 §5.2 端点中，凡<b>依赖未接线的编排语义/存储面</b>（如 V241 决策/行动表未落、
 * pre-commit 裁决 + Git 归档未接、契约测试五关未建、kill-switch 标志位存储单源未建、
 * readiness 缺口补齐引擎侧未接）者，一律以本类 {@code 501 + NOT_IMPLEMENTED} 如实出口，
 * <b>禁</b>伪造成功数据、<b>禁</b>把未实现包成 200（§5.3 E-INTERNAL 禁滥用的同族纪律）。</p>
 *
 * <p>响应形态与 {@code WAgentOrchestrationAdvice} 同构：{@code ApiResponse}
 * （code/errorCode/message/data/traceId），data 内标注 {@code pending} 指向欠账条目，
 * 供调用方与验收对账。</p>
 */
final class WAgentDegradation {

    /** 上线冻结的降级码字面量（非 §5.3 业务错误码，仅表达"端点在位、语义未接线"）。 */
    static final String NOT_IMPLEMENTED = "NOT_IMPLEMENTED";

    private WAgentDegradation() {}

    /**
     * 501 明确降级出口。
     *
     * @param feature 欠账条目单源标识（如 {@code F10-21 kill-switch 标志位存储(sysman config 未接)}}
     */
    static ResponseEntity<ApiResponse<Map<String, Object>>> notImplemented(String feature) {
        String traceId = AiTraceSupport.currentTraceId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("implemented", false);
        data.put("pending", feature);
        ApiResponse<Map<String, Object>> body = ApiResponse
                .<Map<String, Object>>error(501, NOT_IMPLEMENTED,
                        "该端点已落位，但实现依赖未接线（" + feature + "），禁伪造结果（诊断码 " + traceId + "）")
                .withTraceId(traceId);
        body.setData(data);
        return ResponseEntity.status(501).body(body);
    }
}
