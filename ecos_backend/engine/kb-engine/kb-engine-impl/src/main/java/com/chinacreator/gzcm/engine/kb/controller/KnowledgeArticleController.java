package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import com.chinacreator.gzcm.engine.kb.nav.model.AssetStatusItemVO;
import com.chinacreator.gzcm.engine.kb.nav.model.AssetStatusVO;
import com.chinacreator.gzcm.engine.kb.repo.PgVectorSupport;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEmbeddingMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识文章 Controller（{@code /api/v1/knowledge/articles/*}）。
 *
 * @group ASSETS
 */
@RestController
@RequestMapping({"/api/v1/knowledge/articles", "/api/v1/knowledge/assets"})
public class KnowledgeArticleController {

    /** F10: 批量状态查询 ID 上限 */
    private static final int MAX_ASSET_IDS = 100;

    private static final Logger log = LoggerFactory.getLogger(KnowledgeArticleController.class);

    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEmbeddingMapper embeddingMapper;
    private final PgVectorSupport pgVectorSupport;

    /**
     * F04-14：构造函数注入（禁 @Autowired 字段注入）；双引擎批查改为同步串行调用。
     */
    public KnowledgeArticleController(KnowledgeRetrievalService knowledgeRetrievalService,
                                      KnowledgeNodeMapper nodeMapper,
                                      KnowledgeEmbeddingMapper embeddingMapper,
                                      PgVectorSupport pgVectorSupport) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.nodeMapper = nodeMapper;
        this.embeddingMapper = embeddingMapper;
        this.pgVectorSupport = pgVectorSupport;
    }

    @GetMapping("/{articleId}")
    public ApiResponse<KnowledgeArticle> getArticle(@PathVariable String articleId) {
        KnowledgeArticle article = knowledgeRetrievalService.getArticle(articleId);
        if (article == null) return ApiResponse.notFound("Article " + articleId + " not found");
        return ApiResponse.success(article);
    }

    @PostMapping
    public ApiResponse<KnowledgeArticle> createArticle(@RequestBody KnowledgeArticle article) {
        return ApiResponse.success(knowledgeRetrievalService.createArticle(article));
    }

    @GetMapping("/search")
    public ApiResponse<List<KnowledgeArticle>> searchArticles(
            @RequestParam String q, @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(knowledgeRetrievalService.searchArticles(q, limit));
    }

    /**
     * F10: 批量查询知识资产的图谱/向量双写状态。
     *
     * <p>F04-14 调度/并发收敛：原 {@code CompletableFuture.supplyAsync × 2 + allOf}
     * 双引擎并行批查改为<b>同步串行</b>调用。两侧 {@code batchExists} 均为单条
     * {@code SELECT ... WHERE id IN (...)} 批查，单次耗时远低于并行收益，串行执行
     * 消除自建线程池依赖；任一侧失败即整体报 {@code 批查失败}（与原
     * {@code allOf.get()} 抛 ExecutionException 行为一致）。</p>
     *
     * <ol>
     *   <li>{@code KnowledgeNodeMapper.batchExists(ids)} → graph 状态</li>
     *   <li>{@code KnowledgeEmbeddingMapper.batchExists(ids)} → vector 状态（pgvector 可用时查，否则空集）</li>
     * </ol>
     *
     * @param ids 逗号分隔的文章 ID（去重 + trim，空/blank 忽略，≤ 100）
     * @return 强类型 {@link AssetStatusVO}
     */
    @GetMapping("/status")
    public ApiResponse<AssetStatusVO> assetStatus(@RequestParam String ids) {
        if (ids == null || ids.isBlank()) {
            throw new ValidationException("ids 不能为空");
        }

        // 参数化：拆分 → trim → 去空 → 去重
        Set<String> idSet = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (idSet.isEmpty()) {
            throw new ValidationException("ids 不能为空");
        }
        if (idSet.size() > MAX_ASSET_IDS) {
            throw new ValidationException("ids 超限，单次 ≤ " + MAX_ASSET_IDS);
        }
        List<String> idList = new ArrayList<>(idSet);

        // F04-14：同步串行批查（原 CompletableFuture 并行已去除）
        Set<String> graphIds;
        Set<String> vectorIds;
        try {
            graphIds = new HashSet<>(nodeMapper.batchExists(idList));
            if (pgVectorSupport.isAvailable()) {
                vectorIds = new HashSet<>(embeddingMapper.batchExists(idList));
            } else {
                vectorIds = Collections.emptySet();
            }
        } catch (Exception e) {
            log.error("F10 asset status 批查失败: {}", e.getMessage(), e);
            throw new ValidationException("批查失败: " + e.getMessage());
        }

        // 组装 VO
        List<AssetStatusItemVO> items = new ArrayList<>(idList.size());
        for (String id : idList) {
            AssetStatusItemVO item = new AssetStatusItemVO();
            item.setArticleId(id);
            item.setGraph(graphIds.contains(id));
            item.setVector(vectorIds.contains(id));
            items.add(item);
        }
        AssetStatusVO vo = new AssetStatusVO();
        vo.setItems(items);
        return ApiResponse.success(vo);
    }
}
