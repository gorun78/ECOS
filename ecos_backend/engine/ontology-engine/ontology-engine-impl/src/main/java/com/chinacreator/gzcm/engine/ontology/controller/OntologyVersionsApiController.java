package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyVersionService;

/**
 * 本体版本规范前缀只读读面 —— F6-07 接口面收敛余面（#146av）additive 别名端点批。
 *
 * <h3>端点（本类新增一对只读读面，原有前缀与签名零变化，API 只增不改）：</h3>
 * <ul>
 *   <li>GET  /api/v1/ontology/versions        — 全部版本列表（与 {@code /api/v1/ecos/versions}
 *                                               列表语义逐字等价；委托
 *                                               {@link OntologyVersionService#listAllVersionsVO()}）</li>
 *   <li>GET  /api/v1/ontology/versions/{id}   — 版本详情（不存在 404；委托
 *                                               {@link OntologyVersionService#getVersionVOById}）</li>
 * </ul>
 *
 * <p><b>F6-07 收敛推进档位判定</b>：详设-06「本体接口面收敛」点名「一个规范前缀
 * {@code /api/v1/ontology/**} + 版本号 Controller 并入该前缀」，原句判定仍「部分实现」——
 * 本类仅为 additive 补上一只读读面（新增第二支入口，不删除 {@code /api/v1/ecos/versions}
 * 既有承运面，不锁写端点进本前缀）。完整合并「前端消费面切换到规范前缀 + 旧 base
 * 补 deprecated 头 + Controller 类合并」仍须另一批裁决，见台账 F6-07 详版行订正注。
 *
 * <p><b>门禁对接（本仓 F6-07 三件套单源面）</b>：
 * <ul>
 *   <li>类级 {@code @RequestMapping("/api/v1/ontology/versions")} 由
 *       {@code OntologyInterfaceSurfaceScanner} 解析时进入「去重基址」集合，新增一枚新基址
 *       ——该基址已被 {@code route-manifest.json} 条目 {@code /api/v1/ontology} 前缀覆盖（段边界
 *       覆盖判据），故 {@code OntologyRouteManifestParityTest#PINNED_UNCOVERED_BASES}
 *       九枚差集不动；</li>
 *   <li>新类是「一支 Controller 声明一支基址」，未与任何在册同基址分组基址重叠，
 *       {@code OntologyNoDuplicateBaseMappingTest#PINNED_DUPLICATE_BASE_GROUPS} 六组钉值不动；</li>
 *   <li>仅声明 v1 前缀，不引入裸 {@code /api/} 别名，
 *       {@code PINNED_ALIAS_CARRIERS} 三枚钉值不动。</li>
 * </ul>
 * 校验动作复用既有 {@link OntologyVersionSimpleController} 的零分叉通道，无重复实现。
 */
@RestController
@RequestMapping("/api/v1/ontology/versions")
public class OntologyVersionsApiController {

    private static final Logger log = LoggerFactory.getLogger(OntologyVersionsApiController.class);

    private final OntologyVersionService versionService;

    public OntologyVersionsApiController(OntologyVersionService versionService) {
        this.versionService = versionService;
    }

    /**
     * GET /api/v1/ontology/versions — 全部版本列表（跨全部 ontology，与
     * {@code /api/v1/ecos/versions} 列表逐字同源）。
     */
    @GetMapping
    public ApiResponse<List<OntologyVersionVO>> listAll() {
        return ApiResponse.success(versionService.listAllVersionsVO());
    }

    /**
     * GET /api/v1/ontology/versions/{id} — 版本详情（不存在 404）。
     */
    @GetMapping("/{id}")
    public ApiResponse<OntologyVersionVO> get(@PathVariable String id) {
        OntologyVersionVO ver = versionService.getVersionVOById(id);
        if (ver == null) {
            return ApiResponse.notFound("ONT-001: Version '" + id + "' not found");
        }
        return ApiResponse.success(ver);
    }
}
