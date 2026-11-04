package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.OntologyGitService;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGitResultVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGitSaveDTO;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveResult;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitSyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 本体 Git 协同 REST API — T16-5 强类型入出参。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>POST /api/v1/engine/ontology/git/commit/{ontologyId} — 提交快照</li>
 *   <li>POST /api/v1/engine/ontology/git/pull/{ontologyId}   — 拉取</li>
 *   <li>POST /api/v1/engine/ontology/git/load                — 按 URL 加载</li>
 * </ul>
 *
 * <p><b>VG-2 收口（2026-11-04）</b>：原先控制器把 DTO 摊成 {@code Map} 交给服务的「Wave31 兼容垫片」、
 * 再 {@code convertValue} 回 VO——服务契约因此仍带 {@code Map<String,Object>}。现在服务侧为强类型
 * （{@code pullVersion/loadVersion} + {@code archiveVersion}），控制器直接组装请求与结果；
 * <b>wire 字段逐字不变</b>（VO 带 {@code @JsonInclude(NON_NULL)}，空值行为与旧 Map 一致）。</p>
 *
 * <p><b>安全（P0 红线）</b>：客户端 {@code url} 一律不作为 clone 依据——仓库只由服务端
 * {@code repositoryId} 注册表定位；{@code /load} 收到 {@code url} 只记 WARN 后丢弃。</p>
 */
@RestController
@RequestMapping("/api/v1/engine/ontology/git")
public class OntologyGitController {

    private static final Logger log = LoggerFactory.getLogger(OntologyGitController.class);

    private final OntologyGitService gitService;

    public OntologyGitController(OntologyGitService gitService) {
        this.gitService = gitService;
    }

    /** Git 提交（强类型入出参；直连 archiveVersion，不再经 Map 垫片）。 */
    @PostMapping("/commit/{ontologyId}")
    public ApiResponse<OntologyGitResultVO> commit(@PathVariable String ontologyId,
                                                    @RequestBody OntologyGitSaveDTO dto) {
        OntologyGitArchiveRequest req = new OntologyGitArchiveRequest();
        if (dto != null) {
            req.setRepositoryId(dto.getRepositoryId());
            req.setAssetType(dto.getAssetType());
            req.setVersionNo(dto.getVersionNo());
            req.setContent(dto.getContent());
            req.setCommitMessage(dto.getMessage());
        }
        req.setAssetId(ontologyId);
        OntologyGitArchiveResult archived = gitService.archiveVersion(req);
        return ApiResponse.success(toVO(ontologyId, dto == null ? null : dto.getMessage(), archived));
    }

    /** Git 拉取（强类型入出参）。 */
    @PostMapping("/pull/{ontologyId}")
    public ApiResponse<OntologyGitResultVO> pull(@PathVariable String ontologyId,
                                                  @RequestBody(required = false) OntologyGitSaveDTO dto) {
        OntologyGitSyncResult result = gitService.pullVersion(ontologyId,
                dto == null ? null : dto.getRepositoryId());
        return ApiResponse.success(toVO(result));
    }

    /** Git 加载（强类型入出参）：只认服务端 repositoryId，客户端 url 忽略。 */
    @PostMapping("/load")
    public ApiResponse<OntologyGitResultVO> load(@RequestBody(required = false) OntologyGitSaveDTO dto) {
        if (dto != null && dto.getUrl() != null && !dto.getUrl().isBlank()) {
            log.warn("Git load 忽略客户端传入的 url（禁止 remote gitUrl 入参），仅接受 repositoryId");
        }
        OntologyGitSyncResult result = gitService.loadVersion(dto == null ? null : dto.getRepositoryId());
        return ApiResponse.success(toVO(result));
    }

    // ═══════════════ 内部转换（强类型 ↔ VO）═══════════════

    /** 归档结果 → VO（字段集与此前 Map→VO 转换一致；null 由 VO 的 NON_NULL 序列化策略省略）。 */
    private OntologyGitResultVO toVO(String ontologyId, String commitMessage, OntologyGitArchiveResult r) {
        OntologyGitResultVO vo = new OntologyGitResultVO();
        if (r == null) {
            return vo;
        }
        vo.setStatus(r.getStatus());
        vo.setOntologyId(ontologyId);
        vo.setCommitMessage(commitMessage);
        vo.setTimestamp(r.getTimestamp() != null ? r.getTimestamp() : System.currentTimeMillis());
        vo.setRepositoryId(r.getRepositoryId());
        vo.setCommitId(r.getCommitId());
        vo.setTag(r.getTag());
        vo.setAssetType(r.getAssetType());
        vo.setVersionNo(r.getVersionNo());
        vo.setReason(r.getReason());
        return vo;
    }

    /** 同步结果 → VO。 */
    private OntologyGitResultVO toVO(OntologyGitSyncResult r) {
        OntologyGitResultVO vo = new OntologyGitResultVO();
        if (r == null) {
            return vo;
        }
        vo.setStatus(r.getStatus());
        vo.setReason(r.getReason());
        vo.setOntologyId(r.getOntologyId());
        vo.setRepositoryId(r.getRepositoryId());
        vo.setRemote(r.getRemote());
        vo.setTimestamp(r.getTimestamp());
        return vo;
    }
}
