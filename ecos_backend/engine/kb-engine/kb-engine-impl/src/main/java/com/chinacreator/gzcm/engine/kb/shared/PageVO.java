package com.chinacreator.gzcm.engine.kb.shared;

import java.util.List;

/**
 * 通用分页响应（list 端点默认 limit 50，上限 500；D.2-4 禁止全量拉取）。
 *
 * @param <T> 行类型
 * @author ECOS KB Team
 */
public class PageVO<T> {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 500;

    private List<T> items;
    private long total;
    private int page;
    private int size;

    public PageVO() {
    }

    public PageVO(List<T> items, long total, int page, int size) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    /** 规范化 size：默认 50，上限 500。 */
    public static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** 规范化 page：最小 1。 */
    public static int clampPage(int page) {
        return Math.max(1, page);
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
