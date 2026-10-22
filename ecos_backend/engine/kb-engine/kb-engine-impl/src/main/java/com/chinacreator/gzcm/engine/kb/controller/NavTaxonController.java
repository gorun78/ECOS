package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.KbEngineModuleRegistry;
import com.chinacreator.gzcm.engine.kb.KbEngineModuleRegistry.Module;
import com.chinacreator.gzcm.engine.kb.KbEngineModuleRegistry.ModuleEntry;
import com.chinacreator.gzcm.engine.kb.nav.INavService;
import com.chinacreator.gzcm.engine.kb.nav.model.ModuleInfoVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavCategorySaveDTO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavCategoryVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavFolderQuery;
import com.chinacreator.gzcm.engine.kb.nav.model.NavModulesVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavNodeStatsVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavProductItemVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavRecommendVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavTagVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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
 *
 * <p><b>状态行保真（#142/B4-3e-33，收口裸回面余面 D-B4-59 在本类的全部类型见证腿）</b>：
 * 本类原先每个出口都是「裸 {@code ApiResponse}」，而 {@code ApiResponse} 只描述 body、
 * 没有 HTTP 状态能力 ⇒ 403 权限拒绝、404 不存在、400 入参不合法在传输层一律读成 HTTP 200，
 * 前端只能靠读信封里的 {@code code} 分态，任何只看状态行的消费者（BFF、重试策略、监控）
 * 都会把「被拒绝」读成「成功但 0 行」（详设 §4.7.2 的「不可用 ≠ 无权限」分态在传输层不可达）。
 * 修法与 gateway {@code RuntimeAlertV1Controller}、kb {@code KbProfileController} 同族：
 * <b>信封逐字段不动、路径与入参签名不动，只在外层补 {@code ResponseEntity} 状态行</b>，
 * 且状态值不第二处另写——统一由 {@link #failure(ApiResponse)} 从信封 {@code code}
 * 经 {@link DataBridgeException#resolveHttpStatus(int)} 单源推出，改码即改行、两处不会漂移。
 * 成功腿走 {@link #ok(ApiResponse)}（补状态行不得把成功一并抬走）。</p>
 *
 * <p><b>已知在册余项（本批不扩大范围）</b>：{@code ApiResponse.internalError(String)} 的信封
 * {@code code} 是信封 v1 的负数码约定 <b>-1</b>，本批状态行按 {@code resolveHttpStatus(-1) → 500}
 * 落真 500，于是这七枚出口出现「行 500／信封 -1」的同号性缺口——该缺口属已登记的
 * <b>D-B4-65「internalError code=-1 约定改判」</b>余项，与 {@code KbAssumptionController}
 * 采用的 500 正数码形态不同源，须由 D-B4-65 的域批一并裁决，不在本批私改信封 code。</p>
 *
 * @group ASSETS
 */
@RestController
@RequestMapping("/api/v1/knowledge/nav")
public class NavTaxonController {

    private static final Logger log = LoggerFactory.getLogger(NavTaxonController.class);

    private final INavService navService;

    public NavTaxonController(INavService navService) {
        this.navService = navService;
    }

    /**
     * 成功出口：状态行 200，信封原样。
     *
     * <p>单独包一层而不用 {@code ResponseEntity.ok(...)} 直写，是为了让本类所有出口的
     * 「状态行承载手段」在同一判据面上可读（{@code SecurityDenialHttpStatusRatchetTest}
     * 的收口自证腿按 {@code ResponseEntity} 在链上认定修法存在）。</p>
     */
    private static <T> ResponseEntity<ApiResponse<T>> ok(ApiResponse<T> body) {
        return ResponseEntity.ok(body);
    }

    /**
     * 失败出口：状态行取信封 {@code code} 单源（{@link DataBridgeException#resolveHttpStatus(int)}）。
     *
     * <p>不在调用点另写 403/404/400 字面量：两处各写一遍即是「改码不改行」的漂移源。</p>
     */
    private static <T> ResponseEntity<ApiResponse<T>> failure(ApiResponse<T> body) {
        return ResponseEntity.status(DataBridgeException.resolveHttpStatus(body.getCode())).body(body);
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
    public ResponseEntity<ApiResponse<List<NavCategoryVO>>> listCategories(
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestParam(value = "search", required = false) String search) {
        try {
            return ok(ApiResponse.success(navService.listCategories(domain, parentId, search)));
        } catch (Exception e) {
            log.error("nav 目录列表失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("查询失败"));
        }
    }

    /**
     * 创建目录（一级：parentId=null；二级/三级：parentId 指向上一级节点）。
     */
    @PostMapping("/categories")
    @RequirePermission(value = "创建导航目录", permission = "knowledge:nav:category:write")
    public ResponseEntity<ApiResponse<NavCategoryVO>> createCategory(@RequestBody NavCategorySaveDTO dto) {
        try {
            return ok(ApiResponse.success(navService.createCategory(dto)));
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (ForbiddenException e) {
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 创建目录失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("创建失败"));
        }
    }

    /**
     * 更新目录（name / sortOrder，level 不可变更 — 移动走 /categories/move）。
     */
    @PutMapping("/categories/{id}")
    @RequirePermission(value = "更新导航目录", permission = "knowledge:nav:category:write")
    public ResponseEntity<ApiResponse<NavCategoryVO>> updateCategory(
            @PathVariable String id,
            @RequestBody NavCategorySaveDTO dto) {
        try {
            return ok(ApiResponse.success(navService.updateCategory(id, dto)));
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (NotFoundException e) {
            return failure(ApiResponse.notFound(e.getMessage()));
        } catch (ForbiddenException e) {
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 更新目录失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("更新失败"));
        }
    }

    /**
     * 逻辑删除目录（is_deleted=1，禁物理删）。
     * 有子目录时拒绝，请先删子。
     */
    @DeleteMapping("/categories/{id}")
    @RequirePermission(value = "删除导航目录", permission = "knowledge:nav:category:write")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable String id) {
        try {
            navService.deleteCategory(id);
            return ok(ApiResponse.success());
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (NotFoundException e) {
            return failure(ApiResponse.notFound(e.getMessage()));
        } catch (ForbiddenException e) {
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 删除目录失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("删除失败"));
        }
    }

    /**
     * 批量移动目录到目标父节点（targetParentId=null = 移回根）。
     * 同批次失败 → 400，已移动部分回滚（业务内 @Transactional）。
     */
    @PostMapping("/categories/move")
    @RequirePermission(value = "移动导航目录", permission = "knowledge:nav:category:move")
    public ResponseEntity<ApiResponse<List<NavCategoryVO>>> moveCategories(@RequestBody Map<String, Object> body) {
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
            return ok(ApiResponse.success(navService.moveCategories(normalized, targetParentId)));
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (NotFoundException e) {
            return failure(ApiResponse.notFound(e.getMessage()));
        } catch (ForbiddenException e) {
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 批量移动失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("移动失败"));
        }
    }

    /**
     * 子树统计（直接 + 间接关联文章数 / 命中标签名）。
     */
    @GetMapping("/categories/{id}/stats")
    public ResponseEntity<ApiResponse<NavNodeStatsVO>> statsFor(@PathVariable String id) {
        try {
            return ok(ApiResponse.success(navService.statsFor(id)));
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (NotFoundException e) {
            return failure(ApiResponse.notFound(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 子树统计失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("统计失败"));
        }
    }

    // ═══════════════ 标签 CRUD ═══════════════

    /** 列出指定域的标签（含使用数）。 */
    @GetMapping("/tags")
    public ResponseEntity<ApiResponse<List<NavTagVO>>> listTags(
            @RequestParam(value = "domain", required = false) String domain) {
        try {
            return ok(ApiResponse.success(navService.listTags(domain)));
        } catch (Exception e) {
            log.error("nav 标签列表失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("查询失败"));
        }
    }

    /**
     * 创建标签（同 domain 下名唯一，撞名 400）。
     */
    @PostMapping("/tags")
    @RequirePermission(value = "创建导航标签", permission = "knowledge:nav:tag:write")
    public ResponseEntity<ApiResponse<NavTagVO>> createTag(
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "tagName", required = true) String tagName) {
        try {
            return ok(ApiResponse.success(navService.createTag(domain, tagName)));
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (ForbiddenException e) {
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 创建标签失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("创建失败"));
        }
    }

    /** 逻辑删除标签（rel 行保留，视图按 is_deleted=0 隐藏）。 */
    @DeleteMapping("/tags/{tagId}")
    @RequirePermission(value = "删除导航标签", permission = "knowledge:nav:tag:write")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable String tagId) {
        try {
            navService.deleteTag(tagId);
            return ok(ApiResponse.success());
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (NotFoundException e) {
            return failure(ApiResponse.notFound(e.getMessage()));
        } catch (ForbiddenException e) {
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 删除标签失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("删除失败"));
        }
    }

    // ═══════════════ 资产关联（单选/多选 + Undo）═══════════════

    /**
     * 单选/替换 — 删除文章 scope=category 下全部 rel 行，插入新集合。
     */
    @PutMapping("/products/{articleId}/categories")
    @RequirePermission(value = "设置文章目录关联", permission = "knowledge:nav:product:link")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setArticleCategories(
            @PathVariable String articleId,
            @RequestBody List<String> categoryIds) {
        try {
            navService.setArticleCategories(articleId, new java.util.LinkedHashSet<>(categoryIds));
            return ok(ApiResponse.success(Map.of("mode", "replace", "count", categoryIds == null ? 0 : categoryIds.size())));
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (NotFoundException e) {
            return failure(ApiResponse.notFound(e.getMessage()));
        } catch (ForbiddenException e) {
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 设置文章目录失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("设置失败"));
        }
    }

    /**
     * 单选/替换 — 删除文章 scope=tag 下全部 rel 行，插入新集合。
     */
    @PutMapping("/products/{articleId}/tags")
    @RequirePermission(value = "设置文章标签关联", permission = "knowledge:nav:product:link")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setArticleTags(
            @PathVariable String articleId,
            @RequestBody List<String> tagNames) {
        try {
            navService.setArticleTags(articleId, new java.util.LinkedHashSet<>(tagNames));
            return ok(ApiResponse.success(Map.of("mode", "replace", "count", tagNames == null ? 0 : tagNames.size())));
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (NotFoundException e) {
            return failure(ApiResponse.notFound(e.getMessage()));
        } catch (ForbiddenException e) {
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 设置文章标签失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("设置失败"));
        }
    }

    /**
     * 多选新增（保留旧），UNION 语义。
     */
    @PostMapping("/products/{articleId}/categories")
    @RequirePermission(value = "追加文章目录关联", permission = "knowledge:nav:product:link")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addArticleCategories(
            @PathVariable String articleId,
            @RequestBody List<String> categoryIds) {
        try {
            navService.addArticleCategories(articleId, new java.util.LinkedHashSet<>(categoryIds));
            return ok(ApiResponse.success(Map.of("mode", "add", "count", categoryIds == null ? 0 : categoryIds.size())));
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (NotFoundException e) {
            return failure(ApiResponse.notFound(e.getMessage()));
        } catch (ForbiddenException e) {
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 追加文章目录失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("追加失败"));
        }
    }

    /**
     * 多选新增（保留旧），UNION 语义。
     */
    @PostMapping("/products/{articleId}/tags")
    @RequirePermission(value = "追加文章标签关联", permission = "knowledge:nav:product:link")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addArticleTags(
            @PathVariable String articleId,
            @RequestBody List<String> tagNames) {
        try {
            navService.addArticleTags(articleId, new java.util.LinkedHashSet<>(tagNames));
            return ok(ApiResponse.success(Map.of("mode", "add", "count", tagNames == null ? 0 : tagNames.size())));
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (NotFoundException e) {
            return failure(ApiResponse.notFound(e.getMessage()));
        } catch (ForbiddenException e) {
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 追加文章标签失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("追加失败"));
        }
    }

    /**
     * 单选/多选删除（保留剩余）。
     */
    @DeleteMapping("/products/{articleId}/categories")
    @RequirePermission(value = "删除文章目录关联", permission = "knowledge:nav:product:link")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeArticleCategories(
            @PathVariable String articleId,
            @RequestParam(value = "ids", required = false) List<String> categoryIds) {
        try {
            navService.removeArticleCategories(articleId, new java.util.LinkedHashSet<>(categoryIds));
            return ok(ApiResponse.success(Map.of("mode", "remove", "count", categoryIds == null ? 0 : categoryIds.size())));
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (ForbiddenException e) {
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 删文章目录失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("删除失败"));
        }
    }

    // ═══════════════ Undo ═══════════════

    /**
     * Undo — 删除文章在 scope（category 或 tag）下的全部 rel 行（回到未标记态）。
     */
    @PostMapping("/products/{articleId}/undo")
    @RequirePermission(value = "撤销文章导航关联", permission = "knowledge:nav:product:link")
    public ResponseEntity<ApiResponse<Map<String, Object>>> undoArticle(
            @PathVariable String articleId,
            @RequestParam(value = "scope", defaultValue = "category") String scope) {
        try {
            navService.undoArticle(articleId, scope);
            return ok(ApiResponse.success(Map.of("articleId", articleId, "scope", scope, "mode", "undo")));
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (NotFoundException e) {
            return failure(ApiResponse.notFound(e.getMessage()));
        } catch (ForbiddenException e) {
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 撤销失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("撤销失败"));
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> listProducts(
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
            return ok(ApiResponse.success(resp));
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (NotFoundException e) {
            return failure(ApiResponse.notFound(e.getMessage()));
        } catch (Exception e) {
            log.error("nav 资产列表失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("查询失败"));
        }
    }

    // ═══════════════ LLM 候选 ═══════════════

    /**
     * PMO-C T2: 列出当前 nav 体系下所有 distinct domain（供前端多 domain 切换下拉）。
     */
    @GetMapping("/domains")
    public ResponseEntity<ApiResponse<List<String>>> listDomains() {
        try {
            return ok(ApiResponse.success(navService.listDomains()));
        } catch (Exception e) {
            log.error("nav domains 列表失败: {}", e.getMessage(), e);
            return failure(ApiResponse.internalError("查询失败"));
        }
    }

    // ═══════════════ 模块列表 ═══════════════

    /**
     * PMO-D F10: 列出 7 个前端页面模块及其归属 Controller 列表。
     *
     * <p>数据源：{@link KbEngineModuleRegistry} 常量表，纯内存读（≤ 5ms），
     * 不走 DB / Neo4j / pgvector。本方法无失败出口（纯内存常量表读），
     * 故不补状态行＝签名不动，避免把无裁决线的读接口一并改型。
     *
     * @return 强类型 {@link NavModulesVO}（非 Map）
     */
    @GetMapping("/modules")
    public ApiResponse<NavModulesVO> modulesList() {
        List<ModuleInfoVO> list = new ArrayList<>();
        for (ModuleEntry entry : KbEngineModuleRegistry.MODULES) {
            ModuleInfoVO info = new ModuleInfoVO();
            info.setModuleName(entry.module().name().toLowerCase());
            info.setControllers(entry.controllers());
            list.add(info);
        }
        NavModulesVO vo = new NavModulesVO();
        vo.setModules(list);
        return ApiResponse.success(vo);
    }

    /**
     * LLM 归类候选（仅建议，不落库）。
     */
    @GetMapping("/recommend/{articleId}")
    public ResponseEntity<ApiResponse<NavRecommendVO>> recommend(@PathVariable String articleId) {
        try {
            return ok(ApiResponse.success(navService.recommend(articleId)));
        } catch (ValidationException e) {
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (NotFoundException e) {
            return failure(ApiResponse.notFound(e.getMessage()));
        } catch (ForbiddenException e) {
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("nav LLM 候选失败: {}", e.getMessage(), e);
            // 这里同样走合规兜底，不抛
            NavRecommendVO vo = new NavRecommendVO();
            vo.setTags(List.of());
            vo.setReason("llm-gateway 不可用 或 服务异常");
            vo.setGeneratedAt(java.time.Instant.now());
            return ok(ApiResponse.success(vo));
        }
    }
}
