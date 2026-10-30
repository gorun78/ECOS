package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认知 run 级运行回放契约承流面（差距分析附录A 第 16 条；契约行 = 详设接口总表第 5415 行
 * {@code replayCognitiveRun}，机读码行 = 第 10194 行第 8 篇 {@code ECOS-COG-503（501 + stub）}）。
 *
 * <h3>本类只补承流面，不补能力</h3>
 * 2026-10-30 用户二值裁决定版 = <b>无参纯 stub 形态</b>：{@code GET /api/v1/cognitive/replay}
 * 0 入参，回<b>真实 HTTP 501</b>（非 200 包壳）+ {@code code=501} + {@code errorCode=ECOS-COG-503}
 * + {@code data.stub=true} + {@code data.owner}/segment/path/missing 四枚定位键——与详设 5415 行
 * 「否则 {@code 501 + stub=true}」原文同法，与 #146w 第 17 条 {@link CognitiveWorldModelReadController}
 * 同一状态行、同一机读码、同一工厂 {@link CognitiveStubResponses} 出口（无缝分叉可能）。
 * 定版另一种签名（run 级取数回放）<b>不建映射</b>：F05-12 守护项 1「推理结果不落盘」⇒
 * 无任何 run 轨迹/推演留痕载体表，「有真实存储返列表」分支缺存储，任何 200 实现都只能是
 * 对空台账的伪 200 或固定值（X-43 原判形态），禁内置任何数据键。
 *
 * <h3>与信念版本回放（不同物，不得互替）</h3>
 * 既有 {@code CognitiveBeliefController}（类级 {@code /api/v1/cognitive/beliefs}）的
 * {@code @GetMapping("/{variable}/{version}/replay")} 是<b>信念版本回放</b>（V129 version 链
 * 版本锚定、只读重算、0 行更新，能力已实装）；本端点是<b>run 级运行回放</b>的契约承流面。
 * 两者不同前缀、不同入参形态、不同判定状态，由
 * {@code RouteUniquenessTest#cognitiveReplaySingleSignature} 双签白名单钉住互不混同、
 * 且第三种 replay 映射出现即红（X-48「两种互斥签名定版一种，另一种不建映射」的落门禁形态）。
 *
 * <h3>形状纪律</h3>
 * 类级前缀复用冻结常量 {@link CognitiveApiPaths#COGNITIVE}（单值，禁旧式双前缀），方法级只挂
 * 相对段 {@link CognitiveApiPaths#REPLAY_REL}（绝对常量挂方法级会注册成双前缀——SUP-D 教训）；
 * 恰一枚无参 {@code @GetMapping}、零写映射、零路径变量；前端 501 分支据 {@code data.stub=true}
 * 渲染"未实现"占位（{@code services/cognitiveClient.ts} → {@code CognitiveApiError(code=STUB)}）。
 * 判定不翻转：本批只让契约路径可路由、状态行诚实，第 16 条 run 级回放能力仍属未实现、
 * 不计入命中率（台账附录A 123/131 基数不动）。
 */
@RestController("ecosCognitiveRunReplayController")
@RequestMapping(CognitiveApiPaths.COGNITIVE)
public class CognitiveRunReplayController {

    @GetMapping(CognitiveApiPaths.REPLAY_REL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> replayCognitiveRun() {
        return CognitiveStubResponses.runReplayNotImplemented();
    }
}
