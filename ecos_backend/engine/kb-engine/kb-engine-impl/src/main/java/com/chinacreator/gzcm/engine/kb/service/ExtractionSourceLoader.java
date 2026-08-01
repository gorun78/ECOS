package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.model.ExtractionSource;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeArticleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 知识抽取源加载器 — 根据源类型（KB_ARTICLE / DOCUMENT / MANUAL）加载文本内容。
 *
 * <p>使用场景：Extractor 在开始抽取前，通过此加载器获取知识源的原始文本，
 * 再送入 LLM 或规则引擎生成 ExtractedSubGraph。</p>
 */
@Service
public class ExtractionSourceLoader {

    private static final Logger log = LoggerFactory.getLogger(ExtractionSourceLoader.class);

    private final KnowledgeArticleMapper articleMapper;

    public ExtractionSourceLoader(KnowledgeArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    // ── Public API ──

    /**
     * 根据 ExtractionSource 记录加载文本内容。
     */
    public String loadContent(ExtractionSource source) {
        if (source == null) {
            log.warn("ExtractionSource is null, returning empty content");
            return "";
        }
        return loadContent(source.getSourceType(), source.getSourceId());
    }

    /**
     * 根据源类型和源ID加载文本内容。
     *
     * @param sourceType 源类型：KB_ARTICLE / DOCUMENT / MANUAL
     * @param sourceId   源ID（通常为主键或文档编号）
     * @return 文本内容；未找到时返回空字符串
     */
    public String loadContent(String sourceType, String sourceId) {
        if (sourceType == null || sourceId == null) {
            log.warn("sourceType or sourceId is null: type={}, id={}", sourceType, sourceId);
            return "";
        }

        String normalizedType = sourceType.trim().toUpperCase();

        try {
            return switch (normalizedType) {
                case "KB_ARTICLE", "ARTICLE" -> loadArticleContent(sourceId);
                case "DOCUMENT" -> loadDocumentContent(sourceId);
                case "MANUAL" -> loadManualContent(sourceId);
                default -> {
                    log.warn("Unknown sourceType '{}'", sourceType);
                    yield "";
                }
            };
        } catch (Exception e) {
            log.error("Failed to load content for sourceType={} sourceId={}: {}", sourceType, sourceId, e.getMessage(), e);
            return "";
        }
    }

    // ── Source-specific loaders ──

    /**
     * 加载知识文章（KB_ARTICLE）的内容。
     * <p>当前实现通过 KnowledgeArticleMapper 查询 ecos_knowledge.knowledge_article 表。</p>
     */
    private String loadArticleContent(String articleId) {
        KnowledgeArticle article = articleMapper.findById(articleId);
        if (article == null) {
            log.warn("KB_ARTICLE not found: id={}", articleId);
            return "";
        }
        log.info("Loaded KB_ARTICLE: id={}, title={}, chars={}",
                articleId, article.getTitle(),
                article.getContent() != null ? article.getContent().length() : 0);
        return article.getContent() != null ? article.getContent() : "";
    }

    /**
     * 加载文档（DOCUMENT）的内容。
     * <p>当前复用 KnowledgeArticleMapper 作为通用文档存储，
     * 后续可替换为独立的 DocumentMapper / MinIO 文件读取。</p>
     */
    private String loadDocumentContent(String documentId) {
        // DOCUMENT 类型当前复用 knowledge_article 表存储
        KnowledgeArticle doc = articleMapper.findById(documentId);
        if (doc == null) {
            log.warn("DOCUMENT not found: id={}", documentId);
            return "";
        }
        log.info("Loaded DOCUMENT: id={}, title={}, chars={}",
                documentId, doc.getTitle(),
                doc.getContent() != null ? doc.getContent().length() : 0);
        return doc.getContent() != null ? doc.getContent() : "";
    }

    /**
     * 加载手册（MANUAL）的内容。
     * <p>当前复用 KnowledgeArticleMapper 作为通用手册存储，
     * 后续可替换为独立的 ManualMapper。</p>
     */
    private String loadManualContent(String manualId) {
        KnowledgeArticle manual = articleMapper.findById(manualId);
        if (manual == null) {
            log.warn("MANUAL not found: id={}", manualId);
            return "";
        }
        log.info("Loaded MANUAL: id={}, title={}, chars={}",
                manualId, manual.getTitle(),
                manual.getContent() != null ? manual.getContent().length() : 0);
        return manual.getContent() != null ? manual.getContent() : "";
    }
}
