package com.chinacreator.gzcm.runtime.core.alert.vo;

import java.util.List;

/**
 * DLQ 分页包络（§D.5.3：{items,total}，first_seen_at desc）。
 */
public class DltPage {

    private List<DltItem> items;
    private long total;
    private int page;
    private int size;

    public DltPage() {
    }

    public DltPage(List<DltItem> items, long total, int page, int size) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<DltItem> getItems() {
        return items;
    }

    public void setItems(List<DltItem> items) {
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
