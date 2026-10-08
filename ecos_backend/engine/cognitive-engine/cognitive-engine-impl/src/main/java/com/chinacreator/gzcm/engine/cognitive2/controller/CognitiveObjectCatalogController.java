package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.objects.CognitiveObjectClass;
import com.chinacreator.gzcm.engine.cognitive2.service.objects.CognitiveObjectCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 层对象目录只读 REST API（详细设计-05 §8.1 接缝义务「提供对象目录 + 只读解析端点」/ REQ-COG-06）。
 *
 * <p>路径池（只增不改，均为 GET）：</p>
 * <pre>
 * GET /api/v1/cognitive/objects             — 11 类全量目录（枚举声明序，稳定可 diff）
 * GET /api/v1/cognitive/objects/{objectKey} — 单对象解析（未登记对象名 ⇒ 400 ECOS-COG-400，fail-loud）
 * </pre>
 *
 * <p><b>零 SQL / 零业务计算</b>：数据只来自 {@link CognitiveObjectCatalogService} 单源枚举视图，
 * 本类不得出现第二套对象清单（C.8 契约单源）。响应里的 {@code carrier} 是<b>契约元数据</b>
 * （哪张 schema 限定表承载该 C 对象），不是数据行；写权限口径随行内 {@code note} 声明
 * （prediction 只经 data-engine 唯一写通道、decision_basis 对 W 只读）。</p>
 *
 * <p><b>三滤波器（铁律 §1.2）核对</b>：类前缀复用既有 {@code /api/v1/cognitive}
 * （VersionPrefixRewriteFilter 对该前缀 KEEP，无新增映射条目 ⇒ 不触
 * {@code RouteManifestParityTest} 的 23 条基线）；未在 {@code AnonymousEndpointRegistry}
 * 登记 ⇒ 落 {@code SecurityConfig} 的 {@code anyRequest().authenticated()}（默认非匿名），
 * ClearanceInterceptor 经 {@code /api/v1/} 规则要求 L1；
 * 错误矩阵由 {@code CognitiveErrorAdvice}（basePackages 含本包）承接。</p>
 */
@RestController("ecosCognitiveObjectCatalogController")
@RequestMapping(CognitiveApiPaths.COGNITIVE)
public class CognitiveObjectCatalogController {

    private final CognitiveObjectCatalogService catalogService;

    public CognitiveObjectCatalogController(CognitiveObjectCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** 全量目录（附件第二册 §16.2 冻结的 11 类，载体/引用态二选一由枚举构造期不变量保证）。 */
    @GetMapping(CognitiveApiPaths.OBJECTS_REL)
    public ApiResponse<List<CognitiveObjectClass.CatalogEntry>> catalog() {
        return ApiResponse.success(catalogService.catalog());
    }

    /** 单对象只读解析（大小写不敏感；未登记 ⇒ guard 异常转 400，禁静默返回空对象）。 */
    @GetMapping(CognitiveApiPaths.OBJECTS_ITEM_REL)
    public ApiResponse<CognitiveObjectClass.CatalogEntry> resolve(
            @PathVariable(CognitiveApiPaths.PARAM_OBJECT_KEY) String objectKey) {
        return ApiResponse.success(catalogService.resolve(objectKey));
    }
}
