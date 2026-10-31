package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGitHistoryVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyVersionService;

/**
 * F6-07 规范前缀 {@code /api/v1/ontology/git} 只读读面 — #146av additive 别名端点批。
 *
 * <p>详设-03 W69 契约：{@code GET /api/v1/ontology/git/history}（L1 只读，
 * 前端只传 repositoryId/assetType/versionNo 语义的只读形态）。本端点的数据来源是
 * 引擎侧发布链路在 runtime Git 归档成功后回填的 {@code git_ref}
 * （{@code ecos_ontology.ecos_ontology_version} 表）——runtime Git 底座当前无
 * commit log 读 API，本端点<b>不伪造 JGit 日志</b>，只呈现库内真实存在的归档历史
 * （已发布 + git_ref 非空）。
 *
 * <p>既有 {@code /api/v1/engine/ontology/git} 写面（commit/pull/load）按 API 只增不改
 * 原样保留；本控制器不占用其基址，新基址 {@code /api/v1/ontology/git} 是新增。
 *
 * <p>门禁对接：本基址被 route-manifest 的 {@code /api/v1/ontology} 条目覆盖，
 * F6-07①/② 钉值基线不动（详设-06 F6-07 收敛条）。
 */
@RestController
@RequestMapping("/api/v1/ontology/git")
public class OntologyGitHistoryApiController {

    private static final Logger log = LoggerFactory.getLogger(OntologyGitHistoryApiController.class);

    private final OntologyVersionService versionService;

    public OntologyGitHistoryApiController(OntologyVersionService versionService) {
        this.versionService = versionService;
    }

    /**
     * GET /api/v1/ontology/git/history — 已发布且已 Git 归档的版本历史（发布时间倒序）。
     *
     * @param ontologyId 可选；空 = 全量
     */
    @GetMapping("/history")
    public ApiResponse<List<OntologyGitHistoryVO>> history(
            @RequestParam(required = false) String ontologyId) {
        log.info("git history query ontologyId={}", ontologyId);
        return ApiResponse.success(versionService.gitHistoryVO(ontologyId));
    }
}
