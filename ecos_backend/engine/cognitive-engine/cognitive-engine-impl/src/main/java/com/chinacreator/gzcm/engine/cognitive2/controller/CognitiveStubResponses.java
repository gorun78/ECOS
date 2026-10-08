package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
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
 */
final class CognitiveStubResponses {

    private CognitiveStubResponses() {}

    static ResponseEntity<ApiResponse<Map<String, Object>>> notImplemented() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stub", true);
        data.put("reason", "capability not implemented (详细设计-05 F05-16 显式 stub 化)");
        ApiResponse<Map<String, Object>> body = ApiResponse.error(
                HttpStatus.NOT_IMPLEMENTED.value(),
                CognitiveErrorCode.E503_NOT_IMPLEMENTED,
                CognitiveErrorCode.MSG_NOT_IMPLEMENTED);
        body.setData(data);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
    }
}
