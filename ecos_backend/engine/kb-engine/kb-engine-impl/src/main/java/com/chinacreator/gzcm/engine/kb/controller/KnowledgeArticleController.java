package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kb/articles")
public class KnowledgeArticleController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeArticleController.class);

    @Autowired
    private KnowledgeRetrievalService knowledgeRetrievalService;

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
}