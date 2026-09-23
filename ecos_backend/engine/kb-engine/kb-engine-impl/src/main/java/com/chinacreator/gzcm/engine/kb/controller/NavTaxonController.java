package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.nav.INavService;
import com.chinacreator.gzcm.engine.kb.nav.model.NavCategorySaveDTO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavCategoryVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavFolderQuery;
import com.chinacreator.gzcm.engine.kb.nav.model.NavNodeStatsVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavProductItemVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavRecommendVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavTagVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识导航 (kb-nav) 控制器 — {@code /api/v1/knowledge/nav/*}（PMO-A A3/A4）。
 *
 * <p>提供：</p>
 * <ol>
 *   <li>目录子树（GET /categories?domain=&amp;parentId=&amp;search=）</li>
 *   <li>目录 CRUD（POST/PUT/DELETE /categories，逻辑删）</li>
 *   <li>批量移动（POST /categories/move）</li>
 *   <li>子树统计（GET /categories/{id}/stats）</li>
 *   <li>标签 CRUD（GET/POST /tags，DELETE /tags/{tagId}）</li>
 *   <li>资产列表（GET /products?categoryIds=&amp;tags=&amp;keyword=&amp;pageNum=&amp;pageSize=）</li>
 *   <li>资产多选/单选关联（POST/PUT/DELETE /products/{articleId}/categories / tags；scope tag 用）</li>
 *   <li>Undo（POST /products/{articleId}/undo?scope=）</li>
 *   <li>LLM 候选（GET /recommend/{articleId}，仅建议不落库）</li>
 * </ol>
 *
 * <p>Controller 仅做入参校验 + 调用 INavService + 异常 → ApiResponse 映射；
 * 业务规则与 ABAC/审计由 Service 层处理（铁律 §1.1）。</p>
 */
@RestController
@RequestMapping("/api/v1/knowledge/nav")
public class NavTaxonController {

    private static final Logger log = LoggerFactory.getLogger(NavTaxonController.class);

    private final INavService navService;

    public NavTaxonController(INavService navService) {
        this.navService = navService;
    }

    // ═══════════════ 目录 CRUD ═══════════════

    /**
     * 列出目录子树（parentId 为空 = 一级目录）。
     *
     * @param domain   多租户预留，默认 "default"
     * @param parentId 限定父目录（可空，空 = 列出全子树）
     * @param search   模糊关键字（匹配 name OR path）
     */
    @GetMapping("/categories")
    public ApiResponse<List<NavCategoryVO>> listCategories(
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestParam(value = "search", required = false) String search) {
        try {
            return ApiResponse.success(navService.listCategories(domain, parentId, search));
        } catch (Exception e) {
            log.error("nav 目录列表失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 创建目录（一级：parentId=null；二级/三级：parentId 指向上一级节点）。
     */
    @PostMapping("/categories")
    public ApiResponse<NavCategoryVO> createCategory(@RequestBody NavCategorySaveDTO dto) {
        try {
            return ApiResponse.success(navService.createCategory(dto));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (ForbiddenException e) {
            return ApiResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("nav 创建目录失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新目录（name / sortOrder，level 不可变更 — 移动走 /categories/move）。
     */
    @PutMapping("/categories/{id}")
    public ApiResponse<NavCategoryVO> updateCategory(
            @PathVariable String id,
            @RequestBody NavCategorySaveDTO dto) {
        try {
            return ApiResponse.success(navService.updateCategory(id, dto));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (NotFoundException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return ApiResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("nav 更新目录失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    /**
     * 逻辑删除目录（is_deleted=1，禁物理删）。
     * 有子目录时拒绝，请先删子。
     */
    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable String id) {
        try {
            navService.deleteCategory(id);
            return ApiResponse.success();
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (NotFoundException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return ApiResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("nav 删除目录失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量移动目录到目标父节点（targetParentId=null = 移回根）。
     * 同批次失败 → 400，已移动部分回滚（业务内 @Transactional）。
     */
    @PostMapping("/categories/move")
    public ApiResponse<List<NavCategoryVO>> moveCategories(@RequestBody Map<String, Object> body) {
        List<?> ids = body == null ? null : (List<?>) body.get("ids");
        Object targetRaw = body == null ? null : body.get("targetParentId");
        String targetParentId = targetRaw == null ? null : (targetRaw instanceof String s ? s : String.valueOf(targetRaw));
        try {
            List<String> normalized = new java.util.ArrayList<>();
            if (ids != null) {
                for (Object o : ids) {
                    if (o instanceof String s && !s.isBlank()) {
                        normalized.add(s);
                    }
                }
            }
            return ApiResponse.success(navService.moveCategories(normalized, targetParentId));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (NotFoundException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return ApiResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("nav 批量移动失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("移动失败: " + e.getMessage());
        }
    }

    /**
     * 子树统计（直接 + 间接关联文章数 / 命中标签名）。
     */
    @GetMapping("/categories/{id}/stats")
    public ApiResponse<NavNodeStatsVO> statsFor(@PathVariable String id) {
        try {
            return ApiResponse.success(navService.statsFor(id));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (NotFoundException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("nav 子树统计失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("统计失败: " + e.getMessage());
        }
    }

    // ═══════════════ 标签 CRUD ═══════════════

    /** 列出指定域的标签（含使用数）。 */
    @GetMapping("/tags")
    public ApiResponse<List<NavTagVO>> listTags(
            @RequestParam(value = "domain", required = false) String domain) {
        try {
            return ApiResponse.success(navService.listTags(domain));
        } catch (Exception e) {
            log.error("nav 标签列表失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 创建标签（同 domain 下名唯一，撞名 400）。
     */
    @PostMapping("/tags")
    public ApiResponse<NavTagVO> createTag(
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "tagName", required = true) String tagName) {
        try {
            return ApiResponse.success(navService.createTag(domain, tagName));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (ForbiddenException e) {
            return ApiResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("nav 创建标签失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    /** 逻辑删除标签（rel 行保留，视图按 is_deleted=0 隐藏）。 */
    @DeleteMapping("/tags/{tagId}")
    public ApiResponse<Void> deleteTag(@PathVariable String tagId) {
        try {
            navService.deleteTag(tagId);
            return ApiResponse.success();
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (NotFoundException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return ApiResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("nav 删除标签失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    // ═══════════════ 资产关联（单选/多选 + Undo）═══════════════

    /**
     * 单选/替换 — 删除文章 scope=category 下全部 rel 行，插入新集合。
     */
    @PutMapping("/products/{articleId}/categories")
    public ApiResponse<Map<String, Object>> setArticleCategories(
            @PathVariable String articleId,
            @RequestBody List<String> categoryIds) {
        try {
            navService.setArticleCategories(articleId, new java.util.LinkedHashSet<>(categoryIds));
            return ApiResponse.success(Map.of("mode", "replace", "count", categoryIds == null ? 0 : categoryIds.size()));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (NotFoundException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return ApiResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("nav 设置文章目录失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("设置失败: " + e.getMessage());
        }
    }

    /**
     * 单选/替换 — 删除文章 scope=tag 下全部 rel 行，插入新集合。
     */
    @PutMapping("/products/{articleId}/tags")
    public ApiResponse<Map<String, Object>> setArticleTags(
            @PathVariable String articleId,
            @RequestBody List<String> tagNames) {
        try {
            navService.setArticleTags(articleId, new java.util.LinkedHashSet<>(tagNames));
            return ApiResponse.success(Map.of("mode", "replace", "count", tagNames == null ? 0 : tagNames.size()));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (NotFoundException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return ApiResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("nav 设置文章标签失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("设置失败: " + e.getMessage());
        }
    }

    /**
     * 多选新增（保留旧），UNION 语义。
     */
    @PostMapping("/products/{articleId}/categories")
    public ApiResponse<Map<String, Object>> addArticleCategories(
            @PathVariable String articleId,
            @RequestBody List<String> categoryIds) {
        try {
            navService.addArticleCategories(articleId, new java.util.LinkedHashSet<>(categoryIds));
            return ApiResponse.success(Map.of("mode", "add", "count", categoryIds == null ? 0 : categoryIds.size()));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (NotFoundException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return ApiResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("nav 追加文章目录失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("追加失败: " + e.getMessage());
        }
    }

    /**
     * 多选新增（保留旧），UNION 语义。
     */
    @PostMapping("/products/{articleId}/tags")
    public ApiResponse<Map<String, Object>> addArticleTags(
            @PathVariable String articleId,
            @RequestBody List<String> tagNames) {
        try {
            navService.addArticleTags(articleId, new java.util.LinkedHashSet<>(tagNames));
            return ApiResponse.success(Map.of("mode", "add", "count", tagNames == null ? 0 : tagNames.size()));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (NotFoundException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return ApiResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("nav 追加文章标签失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("追加失败: " + e.getMessage());
        }
    }

    /**
     * 单选/多选删除（保留剩余）。
     */
    @DeleteMapping("/products/{articleId}/categories")
    public ApiResponse<Map<String, Object>> removeArticleCategories(
            @PathVariable String articleId,
            @RequestParam(value = "ids", required = false) List<String> categoryIds) {
        try {
            navService.removeArticleCategories(articleId, new java.util.LinkedHashSet<>(categoryIds));
            return ApiResponse.success(Map.of("mode", "remove", "count", categoryIds == null ? 0 : categoryIds.size()));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (ForbiddenException e) {
            return ApiResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("nav 删文章目录失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    // ═══════════════ Undo ═══════════════

    /**
     * Undo — 删除文章在 scope（category 或 tag）下的全部 rel 行（回到未标记态）。
     */
    @PostMapping("/products/{articleId}/undo")
    public ApiResponse<Map<String, Object>> undoArticle(
            @PathVariable String articleId,
            @RequestParam(value = "scope", defaultValue = "category") String scope) {
        try {
            navService.undoArticle(articleId, scope);
            return ApiResponse.success(Map.of("articleId", articleId, "scope", scope, "mode", "undo"));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (NotFoundException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return ApiResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("nav 撤销失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("撤销失败: " + e.getMessage());
        }
    }

    // ═══════════════ 资产列表 ═══════════════

    /**
     * 资产分页列表（强类型 {@link NavFolderQuery}）。
     *
     * <p>兼容前端多种 query 形式：
     * {@code categoryIds=a1,a2,a3} 与 {@code ${Ids}=[...]}，tags 也支持逗号串。</p>
     */
    @GetMapping("/products")
    public ApiResponse<Map<String, Object>> listProducts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "level", required = false) Integer level,
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "categoryIds", required = false) List<String> categoryIds,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        NavFolderQuery q = new NavFolderQuery();
        q.setKeyword(keyword);
        q.setLevel(level);
        q.setDomain(domain);
        q.setType(type);
        q.setCategoryIds(categoryIds);
        q.setTags(tags);
        q.setPageNum(pageNum);
        q.setPageSize(pageSize);
        try {
            List<NavProductItemVO> items = navService.listProducts(q);
            long total = navService.countProducts(q);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("list", items);
            resp.put("total", total);
            resp.put("pageNum", pageNum);
            resp.put("pageSize", pageSize);
            return ApiResponse.success(resp);
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (NotFoundException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("nav 资产列表失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    // ═══════════════ LLM 候选 ═══════════════

    /**
     * LLM 归类候选（仅建议，不落库）。
     */
    @GetMapping("/recommend/{articleId}")
    public ApiResponse<NavRecommendVO> recommend(@PathVariable String articleId) {
        try {
            return ApiResponse.success(navService.recommend(articleId));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (NotFoundException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return ApiResponse.forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("nav LLM 候选失败: {}", e.getMessage(), e);
            // 这里同样走合规兜底，不抛
            NavRecommendVO vo = new NavRecommendVO();
            vo.setTags(List.of());
            vo.setReason("llm-gateway 不可用 或 服务异常: " + e.getMessage());
            vo.setGeneratedAt(java.time.Instant.now());
            return ApiResponse.success(vo);
        }
    }
}
