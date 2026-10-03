package com.chinacreator.gzcm.runtime.core.alert.vo;

import java.util.List;

/**
 * 告警分页包络（§D.5.3 AlertPage = {items, total}）。
 * <p>
 * 分页 SQL 查 ecos_runtime_alert_record 按 occurred_at desc；
 * severity/status/page/size 端点入参，size 端点侧硬上限 200。
 */
public class AlertPage {

    private List<AlertItem> items;
    private long total;
    private int page;
    private int size;

    public AlertPage() {
    }

    public AlertPage(List<AlertItem> items, long total, int page, int size) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<AlertItem> getItems() {
        return items;
    }

    public void setItems(List<AlertItem> items) {
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
