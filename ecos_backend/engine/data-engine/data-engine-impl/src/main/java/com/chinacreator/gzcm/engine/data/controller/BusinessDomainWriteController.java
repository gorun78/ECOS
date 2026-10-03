package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.service.BusinessDomainWriteService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * BusinessDomainWriteController — F02-05 唯一写通道 + 载体登记（详细设计-02 §C.3.6 / §D.2）。
 *
 * <ul>
 *   <li>{@code POST /api/v1/datanet/write-channel} — 外部引擎唯一业务域写入口</li>
 *   <li>{@code POST /api/v1/engine/data/layers/{layer}/carriers} — 登记层载体（L3 准入）</li>
 * </ul>
 *
 * 三滤波器：new 裸前缀 /api/v1/datanet/write-channel 不加双向映射（§C.2.2）；SecurityConfig 默认 DENY；
 * ClearanceInterceptor 数据域必认证。
 *
 * @author ECOS-BE (F02-05)
 */
@RestController
public class BusinessDomainWriteController {

    private final BusinessDomainWriteService writeService;

    public BusinessDomainWriteController(BusinessDomainWriteService writeService) {
        this.writeService = writeService;
    }

    @PostMapping("/api/v1/datanet/write-channel")
    public ApiResponse<Map<String, Object>> writeChannel(
            @RequestBody BusinessDomainWriteService.WriteChannelRequest req) {
        BusinessDomainWriteService.WriteResult r = writeService.writeBusinessDomainRows(req);
        return ApiResponse.success(Map.of(
                "carrierRef", r.carrierRef(),
                "layer", r.layer(),
                "written", r.written(),
                "idempotencyKey", r.idempotencyKey(),
                "replay", r.idempotentReplay()));
    }

    public record RegisterCarrierRequest(String carrierRef, String storageKind) {}

    @PostMapping("/api/v1/engine/data/layers/{layer}/carriers")
    public ApiResponse<Map<String, Object>> registerCarrier(
            @PathVariable String layer, @RequestBody RegisterCarrierRequest req) {
        String id = writeService.registerLayerCarrier(layer, req.carrierRef(), req.storageKind());
        return ApiResponse.success("载体登记成功", Map.of("resourceId", id, "layer", layer,
                "carrierRef", req.carrierRef(), "storageKind", req.storageKind() == null ? "PG" : req.storageKind()));
    }
}
