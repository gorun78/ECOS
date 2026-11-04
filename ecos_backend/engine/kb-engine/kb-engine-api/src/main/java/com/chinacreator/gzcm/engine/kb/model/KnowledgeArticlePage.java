package com.chinacreator.gzcm.engine.kb.model;

import java.util.List;

/**
 * 游标分页结果（#147BA）。
 *
 * <p>{@code nextCursor} 为 {@code null} 表示已到末页；非 null 时调用方原样回传即可取下一页。
 * 游标是<b>不透明串</b>（base64url），编码内容属实现细节，调用方不得解析或拼装。</p>
 *
 * @author ECOS KB #147BA
 */
public class KnowledgeArticlePage {

    private final List<KnowledgeArticle> items;
    private final String nextCursor;

    public KnowledgeArticlePage(List<KnowledgeArticle> items, String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }

    public List<KnowledgeArticle> getItems() {
        return items;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
