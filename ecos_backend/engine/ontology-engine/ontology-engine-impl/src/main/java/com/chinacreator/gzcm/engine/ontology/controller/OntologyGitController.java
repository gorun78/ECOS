package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.OntologyGitService;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGitResultVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGitSaveDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

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
 * <p>T16-5：入参由 {@code Map} 改 {@link OntologyGitSaveDTO}，返回由 {@code Map} 改
 * {@link OntologyGitResultVO}。底层 {@code OntologyGitService} 为 ontology-engine-api
 * 契约（只增不改），旧 Map 方法签名保留（Wave31 C1 mock 兼容）；本控制器仅在
 * Controller 层做 DTO → Map 入参组装与 Jackson Map → VO 转换
 * （POJO 序列化 == Map 序列化，API 输出契约严格等价）。
 *
 * <p>T16-5: Git 提交记录动态结构豁免 — commit/pull/load 当前仅返回
 * ontologyId/url/commitMessage/status/timestamp 五个标量字段；
 * 未来扩展 commit hash/author/files 等动态 Git 元数据可在 VO 追加 Object 豁免字段。
 */
@RestController
@RequestMapping("/api/v1/engine/ontology/git")
public class OntologyGitController {

    /** Controller 层 Map → VO 转换用的 Jackson Mapper（static 单例，零开销）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OntologyGitService gitService;

    public OntologyGitController(OntologyGitService gitService) {
        this.gitService = gitService;
    }

    /** Git 提交（强类型入出参）。 */
    @PostMapping("/commit/{ontologyId}")
    public ApiResponse<OntologyGitResultVO> commit(@PathVariable String ontologyId,
                                                    @RequestBody OntologyGitSaveDTO dto) {
        Map<String, Object> result = gitService.commit(ontologyId, toBody(dto));
        return ApiResponse.success(toVO(result));
    }

    /** Git 拉取（强类型入出参）。 */
    @PostMapping("/pull/{ontologyId}")
    public ApiResponse<OntologyGitResultVO> pull(@PathVariable String ontologyId,
                                                  @RequestBody OntologyGitSaveDTO dto) {
        Map<String, Object> result = gitService.pull(ontologyId, toBody(dto));
        return ApiResponse.success(toVO(result));
    }

    /** Git 加载（强类型入出参）。 */
    @PostMapping("/load")
    public ApiResponse<OntologyGitResultVO> load(@RequestBody OntologyGitSaveDTO dto) {
        Map<String, Object> result = gitService.load(toBody(dto));
        return ApiResponse.success(toVO(result));
    }

    // ═══════════════ 内部转换（强类型 ↔ service 旧 Map 契约）═══════════════

    /**
     * DTO → service 旧 Map 入参（仅透传 service 消费的 message/url 两键；
     * 缺省时 service 走 getOrDefault 默认值路径，与既有行为等价）。
     */
    private Map<String, Object> toBody(OntologyGitSaveDTO dto) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (dto != null) {
            if (dto.getMessage() != null) {
                body.put("message", dto.getMessage());
            }
            if (dto.getUrl() != null) {
                body.put("url", dto.getUrl());
            }
        }
        return body;
    }

    /** service 旧 Map 返回 → OntologyGitResultVO（字段名等价，NON_NULL）。 */
    private OntologyGitResultVO toVO(Map<String, Object> row) {
        if (row == null) {
            return new OntologyGitResultVO();
        }
        return MAPPER.convertValue(row, OntologyGitResultVO.class);
    }
}
