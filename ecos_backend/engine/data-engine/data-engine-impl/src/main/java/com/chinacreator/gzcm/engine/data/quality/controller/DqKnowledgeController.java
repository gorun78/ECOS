package com.chinacreator.gzcm.engine.data.quality.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.quality.DqKnowledgeSinkService;
import com.chinacreator.gzcm.engine.data.quality.model.DqKnowledgeEntryVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;
import com.chinacreator.gzcm.engine.data.quality.service.DqSecurityService;

/**
 * DQ 知识沉淀 RAG 检索 REST API（PMO-48-D T16）。
 *
 * <p>端点：{@code GET /api/v1/dq/kb/similar?query=&top=3} — 近邻相似检索
 * （Jaccard 字符二元组，全表扫描，默认 top=3，上限 20）。</p>
 *
 * <p>三滤波器：{@code /api/v1/dq/**} 已被
 * {@code VersionPrefixRewriteFilter.v1KeepMap}（KEEP）+
 * {@code SecurityConfig.permitAll("/api/v1/dq/**")} +
 * {@code ClearanceInterceptor}（{@code path.startsWith("/api/v1/dq")}）覆盖，
 * 本 Controller 映射子路径 {@code /api/v1/dq/kb/**}，无需新增滤波器条目（铁律 1.2）。</p>
 *
 * <p>审计（铁律 2.4 #5）：查询前调 {@code DqSecurityService.auditRead(DQ_KB_SEARCH, query)}。</p>
 *
 * @author PMO-48-D T16
 */
@RestController
@RequestMapping("/api/v1/dq/kb")
public class DqKnowledgeController {

    /** top 上限（防全表轰炸 + 限流） */
    private static final int TOP_MAX = 20;

    private final DqKnowledgeSinkService knowledgeSinkService;
    private final DqSecurityService securityService;

    public DqKnowledgeController(DqKnowledgeSinkService knowledgeSinkService,
                                  DqSecurityService securityService) {
        this.knowledgeSinkService = knowledgeSinkService;
        this.securityService = securityService;
    }

    /**
     * RAG 相似检索（query 必填，top 默认 3 上限 20）。
     *
     * @param query 查询文本
     * @param top   返回条数（默认 3）
     * @return top N 相似知识条目（按相似度降序）
     */
    @GetMapping("/similar")
    public ApiResponse<PageResult<DqKnowledgeEntryVO>> similar(
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "3") int top) {
        int safeTop = Math.max(1, Math.min(top, TOP_MAX));
        List<DqKnowledgeEntryVO> data = knowledgeSinkService.searchSimilar(query, safeTop).getData();
        securityService.auditRead("DQ_KB_SEARCH", query == null ? "batch" : query.trim());
        return ApiResponse.success(new PageResult<>(data, (long) data.size(), 1, safeTop));
    }
}
