package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 世界模型 4 handler 显式 stub（详细设计-05 F05-16 / DG-9 · Cg@X-43/X-45）。
 *
 * <p>返回<b>真实 HTTP 501</b>（非 200 包壳）+ {@code code=501} + {@code errorCode=ECOS-COG-503} +
 * {@code data.stub=true}，前端 {@code services/cognitiveClient.ts} 501 分支据此渲染"未实现"占位。</p>
 *
 * <p>禁返回固定 confidence 0.75 / 0.15 / 0.3 或硬编码因果边 {@code CustomerSatisfaction→RenewalRate w=0.8}
 * （"虚假现状声明"红线）；对应 Service 侧 {@code WorldModelServiceImpl} 同步 fail-loud。</p>
 */
@RestController
@RequestMapping("/api/v1/world-model")
public class WorldModelController {

    @GetMapping("/state")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentState() {
        return CognitiveStubResponses.notImplemented();
    }

    /**
     * 零参 = 不绑定请求体。桩面一旦绑定刚性 DTO（曾为 {@code @RequestBody Scenario}），
     * 真实前端载荷（{@code assumptions} 为数组、{@code type} 为字符串）会先被 Jackson 判成
     * 400 参数错误，设计要求的「未实现」占位永远出不来（F05-16 / DG-9）。
     */
    @PostMapping("/scenarios")
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulate() {
        return CognitiveStubResponses.notImplemented();
    }

    @PostMapping("/strategy/recommend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recommendStrategy(@RequestParam String goal) {
        return CognitiveStubResponses.notImplemented();
    }

    @GetMapping("/causal-graph")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCausalGraph() {
        return CognitiveStubResponses.notImplemented();
    }
}
