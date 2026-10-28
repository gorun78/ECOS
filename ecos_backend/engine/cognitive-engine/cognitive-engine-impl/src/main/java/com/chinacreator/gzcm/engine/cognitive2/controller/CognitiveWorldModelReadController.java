package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.WorldModelSegment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 世界模型只读四段契约承流面（差距分析附录A 第 17 条；契约行 = 详设接口总表第 5415 行
 * {@code getWorldModelEntities / getWorldModelRelations / getWorldModelTraces / getWorldModelRules}，
 * 机读码行 = 第 10194 行第 8 篇 {@code ECOS-COG-503（501 + stub=true）}）。
 *
 * <h3>本类只补承流面，不补能力</h3>
 * 详细设计-05 <b>DG-9</b>／X-43／W136（M0）定口径：世界模型能力未实现 ⇒ 四端点回<b>真实 HTTP 501</b>
 * （非 200 包壳）+ {@code code=501} + {@code errorCode=ECOS-COG-503} + {@code data.stub=true}，
 * 另出 {@code data.segment} / {@code data.path} / {@code data.missing}（缺载体取证，
 * 单源 {@link WorldModelSegment}）。<b>禁</b>固定 confidence、硬编码因果边、空列表伪装"查询成功但无数据"
 * ——那正是 X-43 判为"虚假现状声明"的原形态。差距台账对第 17 条的判定不因本批翻转。
 *
 * <h3>与 legacy {@link WorldModelController} 的关系（不可互替）</h3>
 * 既有桩面挂在 {@code /api/v1/world-model} 下、四个不同词（{@code /state}、{@code /scenarios}、
 * {@code /causal-graph}、{@code /strategy/recommend}）；附录A 第 17 条登记的正是「契约名 ≠ 实现名」的偏差。
 * 本类按契约逐字补 cognitive 前缀下的四段路径，legacy 面的路径、签名、载荷一字未动（只增不改）。
 *
 * <h3>路由与形状纪律</h3>
 * 类级前缀复用冻结常量 {@link CognitiveApiPaths#COGNITIVE}，方法级只挂相对段
 * {@link CognitiveApiPaths#WORLD_MODEL_ENTITIES_REL} 等（绝对常量挂方法级会注册成双前缀——SUP-D 教训）。
 * 四段各挂<b>字面段</b>而非 {@code {segment}} 变量：未知段名必须走 Spring 无匹配 ⇒ 真实 404，
 * 禁把拼错的段名也答成 501（那会让"未实现"覆盖"不存在"，两态不可分）。全类四枚 {@code @GetMapping}、
 * 零写映射、零 {@code replay} 字样（认知域 replay 单签名判据 =
 * {@code RouteUniquenessTest#cognitiveReplaySingleSignature}，附录A 第 16 条的定版另裁）。
 */
@RestController("ecosCognitiveWorldModelReadController")
@RequestMapping(CognitiveApiPaths.COGNITIVE)
public class CognitiveWorldModelReadController {

    @GetMapping(CognitiveApiPaths.WORLD_MODEL_ENTITIES_REL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWorldModelEntities() {
        return CognitiveStubResponses.worldModelSegmentNotImplemented(WorldModelSegment.ENTITIES);
    }

    @GetMapping(CognitiveApiPaths.WORLD_MODEL_RELATIONS_REL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWorldModelRelations() {
        return CognitiveStubResponses.worldModelSegmentNotImplemented(WorldModelSegment.RELATIONS);
    }

    @GetMapping(CognitiveApiPaths.WORLD_MODEL_TRACES_REL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWorldModelTraces() {
        return CognitiveStubResponses.worldModelSegmentNotImplemented(WorldModelSegment.TRACES);
    }

    @GetMapping(CognitiveApiPaths.WORLD_MODEL_RULES_REL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWorldModelRules() {
        return CognitiveStubResponses.worldModelSegmentNotImplemented(WorldModelSegment.RULES);
    }
}
