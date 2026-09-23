package com.chinacreator.gzcm.engine.kb.nav;

import com.chinacreator.gzcm.engine.kb.nav.model.NavCategorySaveDTO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavCategoryVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavFolderQuery;
import com.chinacreator.gzcm.engine.kb.nav.model.NavNodeStatsVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavProductItemVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavRecommendVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavTagVO;

import java.util.List;
import java.util.Set;

/**
 * 知识导航·目录 / 标签 / 关联 / LLM 建议 统一服务契约。
 *
 * <p><b>接口仅增不改</b>（kb-engine-api AGENTS.md 红线）：本接口为 PMO-A A2 一次性引入，
 * 所有方法均为新增能力，不影响既有 KB 契约。</p>
 *
 * <p>异常约定：</p>
 * <ul>
 *   <li>ABAC 拒绝        → impl 抛 {@code ForbiddenException}（HTTP 403）</li>
 *   <li>资源不存在        → impl 抛 {@code NotFoundException}（HTTP 404）</li>
 *   <li>参数非法 / 层级越界 → impl 抛 {@code ValidationException}（HTTP 400）</li>
 *   <li>LLM 异常         → <b>不</b>抛，返回 {@code NavRecommendVO}{reason=…}（合规兜底）</li>
 * </ul>
 */
public interface INavService {

    /** 列出指定域的一级目录（可叠加 parentId / search 过滤），按 sort_order 排序。 */
    List<NavCategoryVO> listCategories(String domain, String parentId, String search);

    /** 新增目录（parentId=null 创建一级，level 由 parentId 推断；不允许跨级）。 */
    NavCategoryVO createCategory(NavCategorySaveDTO dto);

    /** 更新目录（name / sortOrder），level 不可变更（移动走 {@link #moveCategories}）。 */
    NavCategoryVO updateCategory(String id, NavCategorySaveDTO dto);

    /** 逻辑删除（is_deleted=1），禁止物理删。 */
    void deleteCategory(String id);

    /** 批量移动目录到目标父节点，完成后回填 path；targetParentId=null → 移回根。 */
    List<NavCategoryVO> moveCategories(List<String> ids, String targetParentId);

    /** 子树统计（直接 + 间接所属文章数 / 命中数 / 命中标签名）。 */
    NavNodeStatsVO statsFor(String categoryId);

    /** 列出指定域的标签（带 useCount）。 */
    List<NavTagVO> listTags(String domain);

    /** 创建标签（同 domain 下名唯一，撞名抛 ValidationException 409）。 */
    NavTagVO createTag(String domain, String tagName);

    /** 删标签（逻辑删；已关联文章的 rel 行保留但视图失效）。 */
    void deleteTag(String tagId);

    /** 单选变更（替换）— 删除文章在 scope=category 下的全部 rel 行，重建集合。 */
    void setArticleCategories(String articleId, Set<String> categoryIds);

    /** 单选变更（替换）— 删除文章在 scope=tag 下的全部 rel 行，重建集合。 */
    void setArticleTags(String articleId, Set<String> tagNames);

    /** 多选新增（保留旧），UNION 语义。 */
    void addArticleCategories(String articleId, Set<String> categoryIds);

    /** 多选新增（保留旧），UNION 语义。 */
    void addArticleTags(String articleId, Set<String> tagNames);

    /** 单选/多选删除（保留剩余）。 */
    void removeArticleCategories(String articleId, Set<String> categoryIds);

    /** 单选/多选删除（保留剩余）。 */
    void removeArticleTags(String articleId, Set<String> tagNames);

    /** Undo — 删除文章在当前 scope（category/tag）下的全部 rel 行（回到未标记态）。 */
    void undoArticle(String articleId, String scope);

    /**
     * 资产分页列表（强类型 {@link NavFolderQuery}）。
     * 命中语义：category（含子树展开）OR tag ALL + keyword，
     * 入参全空返回全量分页。
     */
    List<NavProductItemVO> listProducts(NavFolderQuery query);

    /** {@link #listProducts} 的总条数（同条件）。 */
    long countProducts(NavFolderQuery query);

    /**
     * LLM 归类候选（<b>仅建议，不落库</b>）：取文章 title+content（截断 2000 字）
     * → 走 llm-gateway（{@code @Autowired(required=false) LLMGatewayService}）
     * → 请求 ≤5 候选标签 + 1 推荐二级目录名。
     *
     * <p>失败 / 不可用：返回 {@code tags=[] / suggestedCategoryId=null /
     * reason="llm-gateway 不可用 或 LLM 解析失败"}，<b>不抛</b>。</p>
     */
    NavRecommendVO recommend(String articleId);
}
