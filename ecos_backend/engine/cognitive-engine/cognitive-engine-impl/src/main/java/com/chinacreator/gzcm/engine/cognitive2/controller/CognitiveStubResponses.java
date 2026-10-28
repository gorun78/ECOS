package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.WorldModelSegment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认知域显式 stub 响应工厂（详细设计-05 F05-16 / DG-9）。
 *
 * <p>门禁语义：本期无实现的端点一律回<b>真实 HTTP 501</b>（不是 200 + code=501）+
 * {@code errorCode=ECOS-COG-503} + {@code data.stub=true}，前端据此渲染"未实现"占位
 * （{@code services/cognitiveClient.ts} 的 501 分支 → {@code CognitiveApiError(code=STUB)}）。
 * 禁 200 + 固定/内置数据。</p>
 *
 * <p>2026-10-28 #146w 追加 {@link #worldModelSegmentNotImplemented(WorldModelSegment)}：
 * 附录A 第 17 条契约四段（{@code /api/v1/cognitive/world-model/{entities,relations,traces,rules}}）
 * 复用同一状态行与机读码，只多三枚定位键。<b>同一工厂出口</b>是刻意的——两套"未实现"
 * 若各写一份信封，前端 501 分支必然只认其中一套。</p>
 */
final class CognitiveStubResponses {

    /** fail-loud 原因串（旧四端点与新四段共用同一字面量，禁分叉出第二种"未实现"文案）。 */
    private static final String REASON_NOT_IMPLEMENTED =
            "capability not implemented (详细设计-05 F05-16 显式 stub 化)";

    private CognitiveStubResponses() {}

    static ResponseEntity<ApiResponse<Map<String, Object>>> notImplemented() {
        return stubResponse(null, null, null);
    }

    /**
     * 世界模型只读四段（附录A 第 17 条，{@code GET /api/v1/cognitive/world-model/{segment}}）。
     *
     * <p>与 {@link #notImplemented()} 同一状态行、同一机读码、同一 {@code stub} 位，
     * 额外三枚定位键 {@code segment} / {@code path} / {@code missing}——{@code missing} 取自
     * {@link WorldModelSegment#missingCarrier()}（已核实的缺载体取证，禁在此填任何数据值）。</p>
     */
    static ResponseEntity<ApiResponse<Map<String, Object>>> worldModelSegmentNotImplemented(WorldModelSegment segment) {
        return stubResponse(segment.pathSegment(),
                CognitiveApiPaths.COGNITIVE + segment.relativePath(),
                segment.missingCarrier());
    }

    private static ResponseEntity<ApiResponse<Map<String, Object>>> stubResponse(
            String segment, String path, String missing) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stub", true);
        data.put("reason", REASON_NOT_IMPLEMENTED);
        if (segment != null) {
            data.put("owner", "cognitive");
            data.put("segment", segment);
            data.put("path", path);
            data.put("missing", missing);
        }
        ApiResponse<Map<String, Object>> body = ApiResponse.error(
                HttpStatus.NOT_IMPLEMENTED.value(),
                CognitiveErrorCode.E503_NOT_IMPLEMENTED,
                CognitiveErrorCode.MSG_NOT_IMPLEMENTED);
        body.setData(data);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
    }
}
